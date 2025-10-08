// In a new package, e.g., /app/src/main/java/com/example/potholeclickerclient/ble
package com.example.potholeclickerclient.ble

import androidx.lifecycle.*
import com.st.blue_sdk.BlueManager
import com.st.blue_sdk.models.Node
import com.st.blue_sdk.common.Status
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DeviceScanViewModel @Inject constructor(
        private val blueManager: BlueManager
) : ViewModel() {
    private val _scanBleDevices = MutableStateFlow<List<Node>>(emptyList())
    private val _isLoading = MutableStateFlow(false)

    val scanBleDevices: LiveData<List<Node>> = _scanBleDevices.asLiveData()
    val isLoading: LiveData<Boolean> = _isLoading.asLiveData()

    private var scanPeripheralJob: Job? = null

    fun startScan() {
        scanPeripheralJob?.cancel()
        scanPeripheralJob = viewModelScope.launch {
            blueManager.scanNodes().map {
                _isLoading.tryEmit(it.status == Status.LOADING)
                it.data ?: emptyList()
            }.collect {
                _scanBleDevices.tryEmit(it)
            }
        }
    }
}
