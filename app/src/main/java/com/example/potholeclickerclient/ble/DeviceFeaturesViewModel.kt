package com.example.potholeclickerclient.ble

import com.st.blue_sdk.BlueManager
import com.st.blue_sdk.features.Feature
import com.st.blue_sdk.features.FeatureUpdate
import com.st.blue_sdk.features.acceleration.Acceleration
import com.st.blue_sdk.features.gyroscope.Gyroscope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceFeaturesViewModel  @Inject constructor(
    private val blueManager: BlueManager
) {
    private var notificationJob: Job? = null

    fun startSensorSubscription(deviceId: String, features: List<Feature<*>>, onDataReceived: (FeatureUpdate<*>) -> Unit) {
        val flows = mutableListOf<Flow<FeatureUpdate<*>>>()

        features.forEach { feature ->
            flows.add(blueManager.getFeatureUpdates(nodeId = deviceId, features = listOf(feature)))
        }

        notificationJob = CoroutineScope(Dispatchers.IO).launch {
           flows.merge().collect { update ->
               onDataReceived(update)
           }
        }
    }

    fun stopSensorSubscription(deviceId: String, features: List<Feature<*>>) {
        notificationJob?.cancel()
        notificationJob = null

        CoroutineScope(Dispatchers.IO).launch {
            blueManager.disableFeatures(deviceId, features)
        }
    }
}