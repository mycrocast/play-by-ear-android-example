package de.mycrocast.android.play_by_ear_example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.mycrocast.android.play_by_ear.sdk.connection.domain.PlayByEarConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel which starts the initial connection try to the backend of PlayByEar via the given PlayByEarConnection.
 * It will also collect updates of the connection state to the PlayByEar backend to navigate to a corresponding screen.
 *
 * @property locationManager
 * @property connection PlayByEarConnection to observe and to connect to.
 * @property context
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val locationManager: LocationManager,
    private val connection: PlayByEarConnection,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /**
     * Represents the different screens where the app can navigate to depending on the current connection state.
     */
    enum class Screen {
        /**
         * Screen which asks user to allow the location permission.
         */
        REQUEST_LOCATION_PERMISSION,

        /**
         * Screen which asks the user to enable the location provider.
         */
        ENABLE_LOCATION_PROVIDER,

        /**
         * The connection attempt failed.
         * Show a screen with the possibility to reconnect via PlayByEarConnection.
         */
        CONNECTION_FAILED,

        /**
         * The connection process is currently running.
         * Show a screen with a loading animation.
         */
        CONNECTING,

        /**
         * The connection was established successfully.
         * Show a screen where the list of currently active livestreams is loaded and displayed.
         */
        LIVESTREAMS,

        /**
         * A previously established connection was closed.
         * Show a screen with the possibility to reconnect via PlayByEarConnection.
         */
        DISCONNECTED
    }

    /**
     * Represents the current state of the user interface
     *
     * @property screen The current Screen to show.
     */
    data class UIState(
        val screen: Screen? = null
    )

    private val _uiState = MutableStateFlow(UIState())
    val uiState = _uiState.asStateFlow()

    init {
        // collect connection changes, routes to screens accordingly
        viewModelScope.launch {
            connection.currentState.collect { state ->
                when (state) {
                    PlayByEarConnection.State.NEW -> initializeSDK()
                    PlayByEarConnection.State.CONNECTING -> _uiState.update { it.copy(screen = Screen.CONNECTING) }
                    PlayByEarConnection.State.CONNECTED -> _uiState.update { it.copy(screen = Screen.LIVESTREAMS) }
                    PlayByEarConnection.State.DISCONNECTED -> _uiState.update { it.copy(screen = Screen.DISCONNECTED) }
                }
            }
        }
    }

    private suspend fun initializeSDK() {
        if (!hasLocationPermission()) {
            requestLocationPermission()
            return
        }

        if (!isAnyLocationProviderEnabled()) {
            enableLocationProvider()
            return
        }

        connect()
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        _uiState.update { it.copy(screen = Screen.REQUEST_LOCATION_PERMISSION) }
    }

    private fun isAnyLocationProviderEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun enableLocationProvider() {
        _uiState.update { it.copy(screen = Screen.ENABLE_LOCATION_PROVIDER) }
    }

    /**
     * Tries to establish a connection via PlayByEarConnection.
     * Navigates to the Connection_Failed screen in case of failure.
     */
    private suspend fun connect() {
        val success = connection.connect()
        if (!success) {
            _uiState.update { it.copy(screen = Screen.CONNECTION_FAILED) }
        }
    }

    fun onPermissionGranted() {
        viewModelScope.launch {
            initializeSDK()
        }
    }

    fun onProviderEnabled() {
        viewModelScope.launch {
            initializeSDK()
        }
    }
}