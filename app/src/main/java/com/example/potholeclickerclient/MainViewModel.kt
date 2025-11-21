package com.example.potholeclickerclient

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.example.potholeclickerclient.ble.DeviceFeaturesViewModel
import com.example.potholeclickerclient.state.SessionModel
import com.example.potholeclickerclient.tools.CsvManager
import com.example.potholeclickerclient.tools.FileType
import com.example.potholeclickerclient.tools.LocationManager
import com.example.potholeclickerclient.tools.MetadataManager
import com.st.blue_sdk.BlueManager
import com.st.blue_sdk.features.FeatureUpdate
import com.st.blue_sdk.models.NodeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class EventType(val type: String) {
    POTHOLE("pothole"),
    SPEED_BUMP("speed_bump"),
    MANHOLE("manhole"),
    OTHER("other")
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val locationManager: LocationManager,
    private val csvManager: CsvManager,
    private val metadataManager: MetadataManager,
    val sessionModel: SessionModel,
    val deviceFeaturesViewModel: DeviceFeaturesViewModel,
    private val blueManager: BlueManager
) : ViewModel() {
    companion object {
        private val TAG = MainViewModel::class.simpleName
        private const val MAX_RETRY_CONNECTION = 3
    }

    private val _toastMessage = MutableLiveData<String?>()

    val toastMessage: LiveData<String?> = _toastMessage

    private val _launchCreateCsvFile = MutableLiveData<Boolean>(false)
    val launchCreateCsvFile: LiveData<Boolean> = _launchCreateCsvFile

    private val _requestLocationPermission = MutableLiveData<Boolean>(false)
    val requestLocationPermission: LiveData<Boolean> = _requestLocationPermission

    private val _selectedDeviceAddress = MutableLiveData<String?>()
    val selectedDeviceAddress: LiveData<String?> = _selectedDeviceAddress

    private val _selectedDeviceName = MutableLiveData<String?>()
    val selectedDeviceName: LiveData<String?> = _selectedDeviceName

    private val _isPlayButtonEnabled = MutableLiveData<Boolean>(false)
    val isPlayButtonEnabled: LiveData<Boolean> = _isPlayButtonEnabled

    private val _isTracking = MutableLiveData<Boolean>(false)
    val isTracking: LiveData<Boolean> = _isTracking

    private val _isStopping = MutableLiveData<Boolean>(false)
    val isStopping: LiveData<Boolean> = _isStopping

    init {
        listenForSensorsData()
    }

    private fun listenForSensorsData() {
        viewModelScope.launch {
            deviceFeaturesViewModel.featureUpdates.asFlow().collect {
                if(it == null) return@collect

                val featureName = FileType.fromSensorName(it.featureName)
                val line = formatSensorDataForCsv(it)
                line?.let {
                    csvManager.appendLineToCsv(featureName, it)
                }
            }
        }
    }

    fun onPlayStopClick() {
        if(_isStopping.value == true) {
            _toastMessage.postValue("Stopping data collection, please wait...")
            return
        }

        if(_isTracking.value == true) {
            viewModelScope.launch {
                stopTracking()
            }
        } else {
            startTracking()
        }
    }

    private var trackingJob: Job? = null

    private fun startTracking(maxConnectionRetries: Int = MAX_RETRY_CONNECTION) {
        val deviceId = selectedDeviceAddress.value ?: return

        if(trackingJob == null) {
            trackingJob = viewModelScope.launch {
                var retryCount = 0

                _isTracking.postValue(true)

                val node = blueManager.getNode(deviceId)
                if(node==null) {
                    _toastMessage.postValue("Device not found. Please re-select.")
                    _isTracking.postValue(false)
                    return@launch
                }

                blueManager.connectToNode(deviceId).collect {
                    val previousNodeState = it.connectionStatus.prev
                    val currentNodeState = it.connectionStatus.current

                    Log.d(TAG, "Node state (prev: $previousNodeState - current: $currentNodeState")

                    if(currentNodeState == NodeState.Connected && previousNodeState != NodeState.Connected) {
                        Log.d(TAG, "Node is connected. Waiting for node to be ready...")
                    } else if(currentNodeState == NodeState.Disconnected) {
                        if(previousNodeState == NodeState.Connecting) {
                            retryCount++
                            if (retryCount > MAX_RETRY_CONNECTION) {
                                _toastMessage.postValue("Connection failed after $MAX_RETRY_CONNECTION retries.")
                                _isTracking.postValue(false)
                                return@collect
                            }
                            Log.d(TAG, "Connection failed. Retrying...")
                            blueManager.connectToNode(deviceId)
                        } else if(previousNodeState == NodeState.Connected || previousNodeState== NodeState.Ready) {
                            Log.d(TAG, "Node has disconnected.")
                            stopTracking() // This will update the UI state.
                            trackingJob?.cancel() // Stop this collector.
                            trackingJob = null
                        }
                    } else if(currentNodeState == NodeState.Ready) {
                        Log.d(TAG, "Node is ready. Subscribing features...")
                        subscribeToFeatures(deviceId)
                    }
                }
            }
        } else {
            Log.d(TAG, "Connection already open. Re-subscribing to features...")
            _isTracking.postValue(true)
            subscribeToFeatures(deviceId)
        }
    }

    private fun subscribeToFeatures(deviceId: String) {
        locationManager.startLocationUpdates()
        deviceFeaturesViewModel.startSensorSubscription(deviceId = deviceId)
    }

    private suspend fun stopTracking() {
        val deviceId = selectedDeviceAddress.value ?: return

        Log.d(TAG, "Stopping tracking sensors...")
        _isStopping.postValue(true)
        _isTracking.postValue(false)

        locationManager.stopLocationUpdates()
        deviceFeaturesViewModel.stopSensorSubscription(deviceId = deviceId)

        _isStopping.postValue(false)
        Log.d(TAG, "Tracking sensors stopped.")
        Log.d(TAG, "SessionModel frame counter value: ${sessionModel.frameCount.value}")
    }

    private fun formatSensorDataForCsv(featureUpdate: FeatureUpdate<*>): String? {
        val timestamp = System.currentTimeMillis()
        val sensor_timestamp = featureUpdate.timeStamp
        val data = featureUpdate.data
        val value = data.logValue;      // x, y, z
        val featureName = featureUpdate.featureName
        val notificationTime = featureUpdate.notificationTime
        val rawData = featureUpdate.rawData
        val location = locationManager.lastKnownLocation
        val lat = location?.latitude ?: ""
        val lon = location?.longitude ?: ""

        return "$timestamp, $lat, $lon, $sensor_timestamp, $featureName, ${value}, " +
                rawData.joinToString("") { "%02X".format(it) }
    }

    fun onActivityCreated() {
        if(!locationManager.hasLocationPermission()) {
            requestLocationPermission()
        }
        if (sessionModel.directoryUri == null) {
            requestNewCsvFile()
        }
    }

    fun requestLocationPermission() {
        _requestLocationPermission.value = true
    }

    fun onLocationPermissionLauncherTriggered() {
        _requestLocationPermission.value = false
    }

    fun requestNewCsvFile() {
        _launchCreateCsvFile.value = true;
    }

    fun onCsvFileLauncherTriggered() {
        _launchCreateCsvFile.value = false
    }

    fun onDeviceSelected(name: String?, address: String?) {
        _selectedDeviceName.value = name
        _selectedDeviceAddress.value = address
        _isPlayButtonEnabled.value = (address != null)
        _isTracking.value = false

        if(sessionModel.directoryUri != null) {
            createSessionFiles()
        } else {
            requestNewCsvFile()
        }
    }

    fun onNewFolderSelected(uri: android.net.Uri?) {
        if(uri == null) return;
        sessionModel.startNewSession(uri)

        if(selectedDeviceAddress.value != null)
        {
            createSessionFiles()
        }
    }

    private fun createSessionFiles() {
        sessionModel.resetSession()

        val directoryUri = sessionModel.directoryUri
        val timestamp = sessionModel.sessionTimestamp
        val sensorName = selectedDeviceName.value
        csvManager.createSessionFiles(directoryUri, timestamp)
        metadataManager.saveMetadata(directoryUri, sensorName, timestamp)
    }

    fun onEventButtonClick(eventType: EventType) {
        val timestamp = System.currentTimeMillis()
        sessionModel.incrementEventCount(eventType)

        locationManager.getCurrentLocation { location ->
            val lat = location?.latitude
            val lon = location?.longitude
            csvManager.appendEvent(eventType.type, timestamp, lat, lon)

            if(location == null) {
                _toastMessage.postValue("Location unavailable; saved without coordinates.")
            }
        }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }
}