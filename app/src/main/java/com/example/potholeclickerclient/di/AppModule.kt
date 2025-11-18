package com.example.potholeclickerclient.di;

import android.app.Activity
import android.content.Context
import com.example.potholeclickerclient.ble.DeviceFeaturesViewModel
import com.example.potholeclickerclient.tools.CsvManager
import com.example.potholeclickerclient.tools.LocationManager
import com.st.blue_sdk.BlueManager
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideLocationManager(
        @ApplicationContext context: Context
    ): LocationManager {
        // Hilt provides the Activity context, which we cast to Activity
        return LocationManager(context)
    }

    @Provides
    @Singleton
    fun provideCsvManager(
        @ApplicationContext context: Context
    ): CsvManager {
        return CsvManager(context)
    }

    @Provides
    @Singleton
    fun provideDeviceFeaturesViewModel(blueManager: BlueManager) : DeviceFeaturesViewModel {
        return DeviceFeaturesViewModel(blueManager)
    }
}
