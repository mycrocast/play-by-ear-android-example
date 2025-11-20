package de.mycrocast.android.play_by_ear_example.livestream.spot.presentation.banner

import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import coil3.compose.SubcomposeAsyncImage
import de.mycrocast.android.play_by_ear.sdk.core.domain.PlayByEarSpotBanner

/**
 * View for displaying the banner of a PlayByEarSpot.
 */
@Composable
fun SpotBannerView(
    banner: PlayByEarSpotBanner?,
    modifier: Modifier = Modifier
) {
    if (banner == null) {
        DefaultSpotBannerView(
            modifier = modifier
        )

        return
    }

    SubcomposeAsyncImage(
        model = banner.pictureUrl,
        contentDescription = banner.title,
        loading = {
            CircularProgressIndicator(
                modifier = modifier
            )
        },
        error = {
            DefaultSpotBannerView(
                modifier = modifier
            )
        },
        modifier = modifier.clickable {
            // TODO: open url
        }
    )
}