package de.mycrocast.android.play_by_ear_example.livestream.spot.data

import de.mycrocast.android.play_by_ear_example.livestream.spot.domain.SpotPlayContainer
import de.mycrocast.android.play_by_ear.sdk.core.domain.PlayByEarSpot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DefaultSpotPlayContainer : SpotPlayContainer {

    private val _currentSpot = MutableStateFlow<PlayByEarSpot?>(null)
    override val currentSpot = _currentSpot.asStateFlow()

    private val _currentPlayTime = MutableStateFlow(0)
    override val currentPlayTime = _currentPlayTime.asStateFlow()

    override fun updateSpot(spot: PlayByEarSpot) {
        _currentSpot.update { spot }
    }

    override fun updatePlayTime(millis: Int) {
        _currentPlayTime.update { millis }
    }

    override fun reset() {
        _currentSpot.update { null }
        _currentPlayTime.update { 0 }
    }
}