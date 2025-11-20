package de.mycrocast.android.play_by_ear_example.di

import android.content.Context
import android.content.SharedPreferences
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.mycrocast.android.play_by_ear.sdk.connection.domain.PlayByEarConnection
import de.mycrocast.android.play_by_ear.sdk.core.data.PlayByEarSDKBuilder
import de.mycrocast.android.play_by_ear.sdk.core.domain.PlayByEarSDK
import de.mycrocast.android.play_by_ear.sdk.core.domain.PlayByEarSDKCredentials
import de.mycrocast.android.play_by_ear.sdk.livestream.container.domain.PlayByEarLivestreamContainer
import de.mycrocast.android.play_by_ear.sdk.livestream.loader.domain.PlayByEarLivestreamLoader
import de.mycrocast.android.play_by_ear.sdk.livestream.player.domain.PlayByEarLivestreamPlayer
import de.mycrocast.android.play_by_ear.sdk.location.domain.PlayByEarLocationProvider
import de.mycrocast.android.play_by_ear.sdk.logger.PlayByEarLogger
import de.mycrocast.android.play_by_ear_example.livestream.play_state.DefaultPlayStateContainer
import de.mycrocast.android.play_by_ear_example.livestream.play_state.PlayStateContainer
import de.mycrocast.android.play_by_ear_example.livestream.spot.data.DefaultSpotPlayContainer
import de.mycrocast.android.play_by_ear_example.livestream.spot.domain.SpotPlayContainer
import de.mycrocast.android.play_by_ear_example.sdk_implementation.CustomPlayByEarFusedLocationCallbackProvider
import de.mycrocast.android.play_by_ear_example.sdk_implementation.CustomPlayByEarLogger
import de.mycrocast.android.play_by_ear_example.sdk_implementation.CustomPlayByEarSDKCredentials
import javax.inject.Singleton

/**
 * Module which includes all dependencies the example application needs to inject in their view-models or services.
 */
@Module
@InstallIn(SingletonComponent::class)
class ApplicationModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences(
            "de.mycrocast.android.play_by_ear_example.preferences",
            Context.MODE_PRIVATE
        )
    }

    @Provides
    @Singleton
    fun provideSDKCredentials(): PlayByEarSDKCredentials {
        return CustomPlayByEarSDKCredentials()
    }

    @Provides
    @Singleton
    fun provideSDKLogger() : PlayByEarLogger {
        return CustomPlayByEarLogger()
    }

    @Provides
    @Singleton
    fun provideLocationManager(
        @ApplicationContext context: Context
    ) : LocationManager {
        return context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    @Provides
    @Singleton
    fun provideLocationProvider(
        @ApplicationContext context: Context
    ) : PlayByEarLocationProvider {
        val executor = ContextCompat.getMainExecutor(context)
        val client = LocationServices.getFusedLocationProviderClient(context)
        return CustomPlayByEarFusedLocationCallbackProvider(context, client, executor)
    }

    @Provides
    @Singleton
    fun provideSDK(
        preferences: SharedPreferences,
        credentials: PlayByEarSDKCredentials,
        logger: PlayByEarLogger,
        locationProvider: PlayByEarLocationProvider
    ): PlayByEarSDK {
        return PlayByEarSDKBuilder(credentials, preferences, logger, locationProvider).build()
    }

    @Provides
    @Singleton
    fun provideConnection(
        sdk: PlayByEarSDK
    ): PlayByEarConnection {
        return sdk.connection
    }

    @Provides
    @Singleton
    fun provideLivestreamLoader(
        sdk: PlayByEarSDK
    ): PlayByEarLivestreamLoader {
        return sdk.livestreamLoader
    }

    @Provides
    @Singleton
    fun provideLivestreamContainer(
        sdk: PlayByEarSDK
    ): PlayByEarLivestreamContainer {
        return sdk.livestreamContainer
    }

    @Provides
    @Singleton
    fun provideLivestreamPlayerFactory(
        sdk: PlayByEarSDK
    ): PlayByEarLivestreamPlayer.Factory {
        return sdk.livestreamPlayerFactory
    }

    @Provides
    @Singleton
    fun providePlayStateContainer(): PlayStateContainer {
        return DefaultPlayStateContainer()
    }

    @Provides
    @Singleton
    fun provideSpotPlayContainer(): SpotPlayContainer {
        return DefaultSpotPlayContainer()
    }
}