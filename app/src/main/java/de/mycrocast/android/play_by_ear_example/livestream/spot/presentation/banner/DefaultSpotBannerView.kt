package de.mycrocast.android.play_by_ear_example.livestream.spot.presentation.banner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Default view if the PlayByEarSpot has no banner configured.
 */
@Composable
fun DefaultSpotBannerView(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color.Blue),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No spot banner configured.",
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Color.White
        )
    }
}