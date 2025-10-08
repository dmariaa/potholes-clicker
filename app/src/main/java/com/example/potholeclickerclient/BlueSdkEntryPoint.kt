package com.example.potholeclickerclient

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface BlueSdkEntryPoint {
    fun blueManager(): com.st.blue_sdk.BlueManager
}