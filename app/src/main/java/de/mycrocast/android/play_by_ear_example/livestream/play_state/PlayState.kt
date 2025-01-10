package de.mycrocast.android.play_by_ear_example.livestream.play_state

/**
 * Represents the current play state of a livestream.
 *
 * @property streamToken The token of the livestream.
 */
sealed class PlayState(val streamToken: String) {

    /**
     * The process to establish a connection to the audio broadcast of the livestream is currently running.
     */
    class Connecting(streamToken: String) : PlayState(streamToken)

    /**
     * The audio broadcast of the livestream is currently playing.
     */
    class Playing(streamToken: String) : PlayState(streamToken)
}