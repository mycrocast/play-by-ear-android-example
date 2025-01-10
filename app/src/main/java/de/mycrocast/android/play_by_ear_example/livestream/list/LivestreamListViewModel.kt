package de.mycrocast.android.play_by_ear_example.livestream.list

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.mycrocast.android.play_by_ear.sdk.core.domain.PlayByEarLivestream
import de.mycrocast.android.play_by_ear.sdk.livestream.container.domain.PlayByEarLivestreamContainer
import de.mycrocast.android.play_by_ear.sdk.livestream.loader.domain.PlayByEarLivestreamLoader
import de.mycrocast.android.play_by_ear_example.livestream.play_state.PlayState
import de.mycrocast.android.play_by_ear_example.livestream.play_state.PlayStateContainer
import de.mycrocast.android.play_by_ear_example.livestream.service.LivestreamPlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel controlling and observing the livestream-list loading process (initial & refresh).
 * Observes the container for livestream groups for changes.
 * Redirects changes to the UI via UIState flow.
 *
 * @property loader Used for (re-) loading all currently active livestreams.
 * @property container Used to observe changes of all currently active livestreams.
 * @property playStateContainer Used to observe changes of the current play state of a livestream.
 * @property context Used to start & stop the foreground service for playing the audio broadcast of a livestream.
 */
@HiltViewModel
class LivestreamListViewModel @Inject constructor(
    private val loader: PlayByEarLivestreamLoader,
    private val container: PlayByEarLivestreamContainer,
    private val playStateContainer: PlayStateContainer,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /**
     * Represents the current state of the user interface
     */
    data class UIState(
        /**
         * Whether the initial loading of livestreams process in running or not
         */
        val isLoading: Boolean = false,

        /**
         * Whether a reloading process of livestreams is running or not
         */
        val isRefreshing: Boolean = false,

        /**
         * Current play state
         */
        val playState: PlayState? = null,

        /**
         * Currently active livestreams
         */
        val livestreams: List<PlayByEarLivestream> = emptyList()
    )

    private val _uiState = MutableStateFlow(UIState())
    val uiState = _uiState.asStateFlow()

    init {
        // collect changes of the currently active livestream groups
        viewModelScope.launch {
            container.online.collect { streams ->
                // update ui state accordingly
                _uiState.update {
                    it.copy(
                        livestreams = streams
                    )
                }
            }
        }

        // start initial loading process
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val success = loader.load()
            if (!success) {
                // TODO: add failure info for user
            }

            _uiState.update { it.copy(isLoading = false) }
        }

        // collect updates for current play state
        viewModelScope.launch {
            playStateContainer.currentPlayState.collect { newState ->
                _uiState.update { it.copy(playState = newState) }
            }
        }
    }

    /**
     * Starts the reloading process of currently active livestream groups.
     */
    fun onRefreshList() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val success = loader.load()
            if (!success) {
                // TODO: add failure info for user
            }

            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * User clicked on a livestream.
     *
     * @param livestream The clicked livestream.
     */
    fun onLivestreamClicked(livestream: PlayByEarLivestream) {
        viewModelScope.launch {
            // if we are currently playing a livestream of this group, the user wants to stop playing
            val currentPlayState = uiState.value.playState
            if (currentPlayState != null && currentPlayState.streamToken == livestream.token) {
                context.stopService(LivestreamPlayService.stop(context))
                return@launch
            }

            // if a livestream is currently connecting/playing/disconnected, we need to stop the foreground service which stops the playing)
            if (currentPlayState != null) {
                context.stopService(LivestreamPlayService.stop(context))
            }

            // start a new foreground service zo start playing the selected livestream
            val intent = LivestreamPlayService.newInstance(context, livestream)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}