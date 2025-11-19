package com.example.potholeclickerclient

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.potholeclickerclient.ble.DeviceFeaturesViewModel
import com.example.potholeclickerclient.tools.CsvManager
import com.example.potholeclickerclient.tools.FileType
import com.example.potholeclickerclient.tools.LocationManager
import com.st.blue_sdk.BlueManager
import com.st.blue_sdk.features.Feature
import com.st.blue_sdk.features.FeatureUpdate
import com.st.blue_sdk.features.acceleration.Acceleration
import com.st.blue_sdk.features.acceleration.AccelerationInfo
import com.st.blue_sdk.features.gyroscope.Gyroscope
import com.st.blue_sdk.models.NodeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val deviceFeaturesViewModel: DeviceFeaturesViewModel,
    private val blueManager: BlueManager
) : ViewModel() {
    companion object {
        private val TAG = MainViewModel::class.simpleName
        private const val MAX_RETRY_CONNECTION = 3
    }

    private val _potholeCount = MutableLiveData(0)
    private val _speedBumpCount = MutableLiveData(0)
    private val _manholeCount = MutableLiveData(0)
    private val _otherCount = MutableLiveData(0)
    private val _frameCount = MutableLiveData(0)


    private val _toastMessage = MutableLiveData<String?>()


    val potholeCount: LiveData<Int> = _potholeCount
    val speedBumpCount: LiveData<Int> = _speedBumpCount
    val manholeCount: LiveData<Int> = _manholeCount
    val otherCount: LiveData<Int> = _otherCount

    val frameCount: LiveData<Int> = _frameCount
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
    private var loggedFeatures: List<Feature<*>> = emptyList()

    fun onPlayStopClick() {
        if(_isTracking.value == true) {
            stopTracking()
        } else {
            startTracking()
        }
    }

    private var trackingJob: Job? = null

//    private fun startTracking(maxConnectionRetries: Int = MAX_RETRY_CONNECTION) {
//        val deviceId = selectedDeviceAddress.value ?: return
//
//        trackingJob?.cancel() // Cancel any previous job
//        trackingJob = viewModelScope.launch {
//            var retryCount = 0
//            _isTracking.postValue(true)
//
//            val node = blueManager.getNode(deviceId)
//
//            blueManager.connectToNode(deviceId).collect {
//                val previousNodeState = it.connectionStatus.prev
//                val currentNodeState = it.connectionStatus.current
//
//                Log.d(TAG, "Node state (prev: $previousNodeState - current: $currentNodeState) retryCount: $retryCount")
//
//                // We only act when the state newly becomes "Connected"
//                if (currentNodeState == NodeState.Connected && previousNodeState != NodeState.Connected) {
//                    Log.d(TAG, "Node is connected. Waiting for service discovery...")
//                    delay(1000)
//
//                    Log.d(TAG, "Discovering features...")
//                    val allFeatures = blueManager.nodeFeatures(deviceId)
//
//                    if (allFeatures.isEmpty()) {
//                        _toastMessage.postValue("Could not discover device features. Please try again.")
//                        stopTracking()
//                        return@collect
//                    }
//
//                    val accelerometer = allFeatures.find { f -> f is Acceleration }
//                    val gyroscope = allFeatures.find { f -> f is Gyroscope }
//                    val featuresToLog = listOfNotNull(accelerometer, gyroscope)
//
//                    if (featuresToLog.isEmpty()) {
//                        _toastMessage.postValue("Accelerometer/Gyroscope not found on this device.")
//                        stopTracking()
//                        return@collect
//                    }
//
//                    Log.d(TAG, "Found features: ${featuresToLog.map { f -> f.name }.joinToString()}. Starting subscriptions.")
//                    locationManager.startLocationUpdates()
//                    deviceFeaturesViewModel.startSensorSubscription(deviceId, featuresToLog) { featureUpdate ->
//                        val line = formatSensorDataForCsv(featureUpdate)
//                        line?.let {
//                            csvManager.appendLineToCsv(
//                                FileType.fromSensorName(featureUpdate.featureName),
//                                it)
//
//                            if(featureUpdate.featureName == "Accelerometer") {
//                                _frameCount.postValue((_frameCount.value ?: 0) + 1)
//                            }
//                        }
//                    }
//                }
//                else if (previousNodeState == NodeState.Connecting && currentNodeState == NodeState.Disconnected) {
//                    retryCount++
//                    if (retryCount > MAX_RETRY_CONNECTION) {
//                        _toastMessage.postValue("Connection failed after $MAX_RETRY_CONNECTION retries.")
//                        _isTracking.postValue(false)
//                        return@collect
//                    }
//                    Log.d(TAG, "Connection failed. Retrying...")
//                    blueManager.connectToNode(deviceId)
//                }
//            }
//        }
//    }

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
                        Log.d(TAG, "Node is connected. Waiting for service discovery...")
                        subscribeToFeatures(deviceId);
                    } else if(currentNodeState == NodeState.Disconnected && previousNodeState == NodeState.Connecting) {
                        retryCount++
                        if (retryCount > MAX_RETRY_CONNECTION) {
                            _toastMessage.postValue("Connection failed after $MAX_RETRY_CONNECTION retries.")
                            _isTracking.postValue(false)
                            return@collect
                        }
                        Log.d(TAG, "Connection failed. Retrying...")
                        blueManager.connectToNode(deviceId)
                    } else if(currentNodeState == NodeState.Disconnected && previousNodeState == NodeState.Connected) {
                        Log.d(TAG, "Node has disconnected.")
                        stopTracking() // This will update the UI state.
                        trackingJob?.cancel() // Stop this collector.
                        trackingJob = null
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
        viewModelScope.launch {
            delay(1000)

            Log.d(TAG, "Discovering features...")
            val allFeatures = blueManager.nodeFeatures(deviceId)

            if (allFeatures.isEmpty()) {
                _toastMessage.postValue("Could not discover device features. Please try again.")
                stopTracking()
                return@launch
            }

            val accelerometer = allFeatures.find { f -> f is Acceleration }
            val gyroscope = allFeatures.find { f -> f is Gyroscope }
            loggedFeatures = listOfNotNull(accelerometer, gyroscope)

            if (loggedFeatures.isEmpty()) {
                _toastMessage.postValue("Accelerometer/Gyroscope not found on this device.")
                stopTracking()
                return@launch
            }

            blueManager.enableFeatures(deviceId, loggedFeatures)

            Log.d(TAG, "Found features: ${loggedFeatures.joinToString { f -> f.name }}. Starting subscriptions.")
            locationManager.startLocationUpdates()
            deviceFeaturesViewModel.startSensorSubscription(deviceId, loggedFeatures) { featureUpdate ->
                val line = formatSensorDataForCsv(featureUpdate)
                line?.let {
                    csvManager.appendLineToCsv(
                    FileType.fromSensorName(featureUpdate.featureName),
                    it)

                    if(featureUpdate.featureName == "Accelerometer") {
                        _frameCount.postValue((_frameCount.value ?: 0) + 1)
                    }
                }
            }
        }
    }

    private fun stopTracking() {
        val deviceId = selectedDeviceAddress.value ?: return

        Log.d(TAG, "Stopping tracking sensors...")
        _isTracking.postValue(false)
        locationManager.stopLocationUpdates()
        deviceFeaturesViewModel.stopSensorSubscription(deviceId, loggedFeatures)
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
    fun onDeviceSelected(name: String?, address: String?) {
        _selectedDeviceName.value = name
        _selectedDeviceAddress.value = address
        _isPlayButtonEnabled.value = (address != null)
        _isTracking.value = false
    }

    fun getFileDefaultName(): String {
        return csvManager.fileDefaultName
    }

    fun onActivityCreated() {
        if(!locationManager.hasLocationPermission()) {
            requestLocationPermission()
        }
        if (!csvManager.isCsvFileChosen) {
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

    fun onNewCsvFileCreated(uri: android.net.Uri?) {
        if(uri != null) {
            csvManager.handleCreateCsvResult(uri)

            _frameCount.value = 0
            _potholeCount.value = 0
            _speedBumpCount.value = 0
            _manholeCount.value = 0
            _otherCount.value = 0
        }
    }

    fun onEventButtonClick(eventType: EventType) {
        val timestamp = System.currentTimeMillis()
        when(eventType) {
            EventType.POTHOLE -> _potholeCount.value = (_potholeCount.value ?: 0) + 1
            EventType.SPEED_BUMP -> _speedBumpCount.value = (_speedBumpCount.value ?: 0) + 1
            EventType.MANHOLE -> _manholeCount.value = (_manholeCount.value ?: 0) + 1
            EventType.OTHER -> _otherCount.value = (_otherCount.value ?: 0) + 1
        }

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