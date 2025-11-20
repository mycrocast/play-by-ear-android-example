package de.mycrocast.android.play_by_ear_example.sdk_implementation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY
import de.mycrocast.android.play_by_ear.sdk.location.domain.PlayByEarLocation
import de.mycrocast.android.play_by_ear.sdk.location.domain.PlayByEarLocationProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume

class CustomPlayByEarFusedLocationCallbackProvider(
    private val context: Context,
    private val locationClient: FusedLocationProviderClient,
    private val executor: Executor
) : PlayByEarLocationProvider {

    override suspend fun getLocation(): PlayByEarLocation? = suspendCancellableCoroutine { cont ->
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        val locationRequest: LocationRequest = LocationRequest.Builder(PRIORITY_BALANCED_POWER_ACCURACY, 1000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(2000)
            .setMaxUpdateDelayMillis(1000)
            .build()

        val locationCallback: LocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val lastLocation = locationResult.lastLocation
                if(lastLocation != null) {
                    cont.resume(PlayByEarLocation(lastLocation.latitude, lastLocation.longitude))
                    locationClient.removeLocationUpdates(this)
                    return
                }

                val locations = locationResult.locations
                var bestLocation: Location? = null
                for (loc in locations) {
                    if(bestLocation == null || loc.accuracy > bestLocation.accuracy) {
                        bestLocation = loc
                    }
                }

                cont.resume(if(bestLocation == null) null else PlayByEarLocation(bestLocation.latitude, bestLocation.longitude))
                locationClient.removeLocationUpdates(this)
            }
        }

        locationClient.requestLocationUpdates(locationRequest, executor, locationCallback)
    }
}