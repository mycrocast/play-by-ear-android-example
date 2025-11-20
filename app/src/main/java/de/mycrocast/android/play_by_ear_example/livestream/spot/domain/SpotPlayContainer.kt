package de.mycrocast.android.play_by_ear_example.livestream.spot.domain

import de.mycrocast.android.play_by_ear.sdk.core.domain.PlayByEarSpot
import kotlinx.coroutines.flow.StateFlow

/**
 * Container which holds all information needed for displaying spots.
 */
interface SpotPlayContainer {
    /**
     * Current playing spot. Is null when no spot is playing
     */
    val currentSpot: StateFlow<PlayByEarSpot?>

    /**
     * Current play time of the playing spot or zero if no spot is playing.
     */
    val currentPlayTime: StateFlow<Int>

    /**
     * Updates the current playing spot.
     */
    fun updateSpot(spot: PlayByEarSpot)

    /**
     * Updates the play time of the playing spot.
     */
    fun updatePlayTime(millis: Int)

    /**
     * Resets the container to: No spot is playing & play time is zero.
     */
    fun reset()
}