package com.example.potholeclickerclient.ble

import androidx.lifecycle.ViewModel
import com.st.blue_sdk.BlueManager
import com.st.blue_sdk.features.Feature
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

class DeviceViewModel @Inject constructor(
    private val blueManager: BlueManager,
) : ViewModel() {

    companion object {
        private val TAG = DeviceScanViewModel::class.simpleName
        private const val MAX_RETRY_CONNECTION = 3
    }

    val features = MutableStateFlow<List<Feature<*>>>(emptyList())

    fun connect(deviceId: String, maxConnectionRetries: Int = MAX_RETRY_CONNECTION) {

    }
}