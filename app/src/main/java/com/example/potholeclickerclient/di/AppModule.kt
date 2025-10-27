package com.example.potholeclickerclient.di;

import android.app.Activity
import android.content.Context
import com.example.potholeclickerclient.tools.CsvManager
import com.example.potholeclickerclient.tools.LocationManager
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.qualifiers.ActivityContext

@Module
@InstallIn(ActivityComponent::class)
object AppModule {
    @Provides
    fun provideLocationManager(
        @ActivityContext context: Context
    ): LocationManager {
        // Hilt provides the Activity context, which we cast to Activity
        return LocationManager(context as Activity)
    }

    @Provides
    fun provideCsvManager(
        @ActivityContext context: Context
    ): CsvManager {
        return CsvManager(context)
    }
}
