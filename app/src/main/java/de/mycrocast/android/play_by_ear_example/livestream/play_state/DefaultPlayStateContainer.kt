package de.mycrocast.android.play_by_ear_example.livestream.play_state

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Main-thread safe implementation of the PlayStateContainer.
 *
 * @property dispatcher The CoroutineDispatcher to use in which operations are executed.
 */
class DefaultPlayStateContainer(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : PlayStateContainer {

    private val _currentPlayState = MutableStateFlow<PlayState?>(null)
    override val currentPlayState = _currentPlayState.asStateFlow()

    override suspend fun onConnect(livestreamToken: String) = withContext(dispatcher) {
        _currentPlayState.update { PlayState.Connecting(livestreamToken) }
    }

    override suspend fun onPlay(livestreamToken: String) = withContext(dispatcher) {
        _currentPlayState.update { PlayState.Playing(livestreamToken) }
    }

    override suspend fun onDisconnect(livestreamToken: String) {
        _currentPlayState.update { PlayState.Connecting(livestreamToken) }
    }

    override suspend fun onStop() = withContext(dispatcher) {
        _currentPlayState.update { null }
    }
}