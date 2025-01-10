package de.mycrocast.android.play_by_ear_example.livestream.play_state

import kotlinx.coroutines.flow.StateFlow

/**
 * Holds and adjusts the current play state.
 */
interface PlayStateContainer {

    /**
     * Current play state.
     * Is always null when nothing is currently connecting nor playing. (Therefore it is also null in the beginning.)
     */
    val currentPlayState: StateFlow<PlayState?>

    /**
     * Changes the current play state to Connecting.
     *
     * @param livestreamToken The token of the connecting livestream.
     */
    suspend fun onConnect(livestreamToken: String)

    /**
     * Changes the current play state to Playing.
     *
     * @param livestreamToken The token of the playing livestream.
     */
    suspend fun onPlay(livestreamToken: String)

    /**
     * Changes the current play state to Connecting.
     *
     * @param livestreamToken The token of the disconnected livestream.
     */
    suspend fun onDisconnect(livestreamToken: String)

    /**
     * (Re-) Sets the current play state to null.
     */
    suspend fun onStop()
}