package com.example.potholeclickerclient.ble

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.potholeclickerclient.state.SessionModel
import com.st.blue_sdk.BlueManager
import com.st.blue_sdk.features.FeatureUpdate
import com.st.blue_sdk.features.acceleration.Acceleration
import com.st.blue_sdk.features.gyroscope.Gyroscope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject


class DeviceFeaturesViewModel  @Inject constructor(
    private val blueManager: BlueManager,
    private val sessionModel: SessionModel
) : ViewModel() {
    companion object {
        private val TAG = DeviceFeaturesViewModel::class.simpleName
    }
    private var dataReceivedJob: Job? = null

    private val _realtimeFrameCounter = AtomicInteger(0)
    private val _frameCounter = MutableLiveData(0)
    val frameCounter: LiveData<Int> = _frameCounter

    private val _featureUpdates = MutableLiveData<FeatureUpdate<*>?>(null)

    val featureUpdates: LiveData<FeatureUpdate<*>?>
        get() = _featureUpdates

    fun startSensorSubscription(deviceId: String)
    {
        if(dataReceivedJob != null) return

        viewModelScope.launch {
            stopSubscription(deviceId)
            startSubscription(deviceId)
        }
    }

    fun stopSensorSubscription(deviceId: String) {
        viewModelScope.launch {
            stopSubscription(deviceId)
        }
    }

    private fun startSubscription(deviceId: String)
    {
        val allFeatures = blueManager.nodeFeatures(deviceId)
        val acceleration = allFeatures.find { f -> f is Acceleration }
        val gyroscope = allFeatures.find { f -> f is Gyroscope }
        val features = listOfNotNull(acceleration, gyroscope)

        dataReceivedJob = blueManager.getFeatureUpdates(nodeId = deviceId, features = features)
            .flowOn(Dispatchers.IO)
            .onEach {
                _featureUpdates.value = it

                if(it.featureName == "Accelerometer") {
                    sessionModel.setFrameCount(_realtimeFrameCounter.incrementAndGet())
                }
            }.launchIn(viewModelScope)
        Log.d(TAG, "Features subscribed...")
    }

    private suspend fun stopSubscription(deviceId: String) {
        dataReceivedJob?.cancelAndJoin()
        dataReceivedJob = null

        val allFeatures = blueManager.nodeFeatures(deviceId)
        val acceleration = allFeatures.find { f -> f is Acceleration }
        val gyroscope = allFeatures.find { f -> f is Gyroscope }
        val features = listOfNotNull(acceleration, gyroscope)

        blueManager.disableFeatures(nodeId = deviceId, features = features)
        Log.d(TAG, "Frame counter value: ${_realtimeFrameCounter.get()}")
        Log.d(TAG, "Features unsubscribed...")
    }
}