package com.example.potholeclickerclient.state

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.potholeclickerclient.EventType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionModel @Inject constructor (
    @ApplicationContext context: Context
) {
    companion object {
        private val TAG = SessionModel::class.simpleName
        private val DIRECTORY_URI_KEY = "directory_uri"
    }
    private val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)

    private val _potholeCount = MutableLiveData(0)
    val potholeCount: LiveData<Int> = _potholeCount
    private val _speedBumpCount = MutableLiveData(0)
    val speedBumpCount: LiveData<Int> = _speedBumpCount
    private val _manholeCount = MutableLiveData(0)
    val manholeCount: LiveData<Int> = _manholeCount
    private val _otherCount = MutableLiveData(0)
    val otherCount: LiveData<Int> = _otherCount
    private val _frameCount = MutableLiveData(0)
    val frameCount: LiveData<Int> = _frameCount

    var directoryUri: Uri? = null
        private set

    var sessionTimestamp: Date? = null
        private set

    init {
        loadSessionState()
    }

    fun startNewSession(uri: Uri)
    {
        directoryUri = uri
        sessionTimestamp = Date()
        saveSessionState()
    }

    fun resetSession()
    {
        sessionTimestamp = Date()

        _frameCount.postValue(0)
        _potholeCount.postValue(0)
        _speedBumpCount.postValue(0)
        _manholeCount.postValue(0)
        _otherCount.postValue(0)
    }

    fun setFrameCount(value: Int)
    {
        _frameCount.value = value
    }

    fun incrementEventCount(eventType: EventType)
    {
        when(eventType) {
            EventType.POTHOLE -> _potholeCount.postValue((_potholeCount.value ?: 0) + 1)
            EventType.SPEED_BUMP -> _speedBumpCount.postValue((_speedBumpCount.value ?: 0) + 1)
            EventType.MANHOLE -> _manholeCount.postValue((_manholeCount.value ?: 0) + 1)
            EventType.OTHER -> _otherCount.postValue((_otherCount.value ?: 0) + 1)
        }
    }


    private fun loadSessionState() {
        val uriString = prefs.getString(DIRECTORY_URI_KEY, null)
        if(uriString != null) {
            directoryUri = Uri.parse(uriString)
        }
    }

    private fun saveSessionState() {
        prefs.edit().apply {
            putString(DIRECTORY_URI_KEY, directoryUri.toString())
            apply()
        }
    }
}