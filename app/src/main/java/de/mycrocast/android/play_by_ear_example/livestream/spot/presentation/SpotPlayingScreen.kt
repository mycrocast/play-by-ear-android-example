package de.mycrocast.android.play_by_ear_example.livestream.spot.presentation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import de.mycrocast.android.play_by_ear_example.R
import de.mycrocast.android.play_by_ear_example.livestream.spot.presentation.banner.SpotBannerView
import de.mycrocast.android.play_by_ear.sdk.core.domain.PlayByEarSpot

/**
 * Screen for displaying a PlayByEarSpot.
 *
 * @param spot The PlayByEarSpot to display.
 * @param playTime The play time of the PlayByEarSpot.
 */
@Composable
fun SpotPlayingScreen(
    spot: PlayByEarSpot,
    playTime: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playProcess: Float = playTime / (spot.duration * 1000.0f)

    // Disable back button presses while spot is playing
    BackHandler(enabled = true) {
        // User feedback, e.g. show information
        Toast.makeText(context, "Back press is disabled while the spot is playing.", Toast.LENGTH_SHORT).show()
    }

    ConstraintLayout(
        modifier = modifier.padding(16.dp)
    ) {
        val (headline, spotName, banner, progress) = createRefs()

        Text(
            text = stringResource(R.string.i18n_playing_spot),
            modifier = Modifier.constrainAs(headline) {
                top.linkTo(parent.top)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            },
            textAlign = TextAlign.Center,
            color = Color.Gray,
            maxLines = 1,
            minLines = 1
        )

        Text(
            text = spot.name,
            modifier = Modifier.constrainAs(spotName) {
                top.linkTo(headline.bottom)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            },
            textAlign = TextAlign.Center,
            color = Color.Black
        )

        SpotBannerView(
            banner = spot.banner,
            modifier = Modifier.constrainAs(banner) {
                top.linkTo(spotName.bottom, margin = 16.dp)
                bottom.linkTo(progress.top, margin = 16.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                width = Dimension.fillToConstraints
                height = Dimension.ratio("1:1")
            }
        )

        LinearProgressIndicator(
            progress = { playProcess },
            modifier = Modifier.constrainAs(progress) {
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }
        )
    }
}