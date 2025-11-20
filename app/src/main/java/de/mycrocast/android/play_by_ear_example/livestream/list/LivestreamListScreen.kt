package de.mycrocast.android.play_by_ear_example.livestream.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import de.mycrocast.android.play_by_ear_example.livestream.spot.presentation.SpotPlayingScreen

/**
 * Screen displaying all currently active PlayByEarLivestream as well as their current play state.
 *
 * @param viewModel Contains the current UIState and a possibility to reload currently active livestreams.
 * Starts & stops the foreground service used to play the audio broadcast of a PlayByEarLivestream.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivestreamListScreen(
    viewModel: LivestreamListViewModel = hiltViewModel()
) {
    val state = rememberPullToRefreshState()

    // current UIState, given by ViewModel
    val uiState by viewModel.uiState.collectAsState()
    val isLoading = uiState.isLoading
    val isRefreshing = uiState.isRefreshing
    val livestreams = uiState.livestreams
    val playState = uiState.playState
    val currentPlayingSpot = uiState.currentPlayingSpot
    val currentSpotPlayTime = uiState.currentSpotPlayTime

    // if spot is currently playing show fullscreen spot playing screen
    if (currentPlayingSpot != null) {
        SpotPlayingScreen(
            spot = currentPlayingSpot,
            playTime = currentSpotPlayTime,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    // show an initial loading animation in the center and nothing else if initial loading
    if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.fillMaxSize(0.5f))
        return
    }

    // display the list of livestream groups
    // add option to user for pull refresh
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier.padding(innerPadding),
            state = state,
            isRefreshing = isRefreshing,
            onRefresh = viewModel::onRefreshList
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(livestreams) { livestream ->
                    LivestreamItem(
                        livestream = livestream,
                        playState
                    ) {
                        viewModel.onLivestreamClicked(livestream)
                    }
                }
            }
        }

        if (livestreams.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "There are currently no livestreams available.",
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}