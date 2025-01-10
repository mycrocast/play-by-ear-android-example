package de.mycrocast.android.play_by_ear_example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import de.mycrocast.android.play_by_ear_example.connection.ConnectingScreen
import de.mycrocast.android.play_by_ear_example.connection.ConnectionFailedScreen
import de.mycrocast.android.play_by_ear_example.connection.DisconnectedScreen
import de.mycrocast.android.play_by_ear_example.livestream.list.LivestreamListScreen
import de.mycrocast.android.play_by_ear_example.ui.theme.PlayByEarExampleTheme

/**
 * Entry point of the application.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlayByEarExampleTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

/**
 * Navigates to different screens depending of the UIState given by the ViewModel.
 *
 * @param viewModel ViewModel which adjusts the UIState according to the connection state to the PlayByEar backend.
 */
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val screen = uiState.screen

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        screen?.let {
            when (it) {
                MainViewModel.Screen.CONNECTION_FAILED -> ConnectionFailedScreen()
                MainViewModel.Screen.CONNECTING -> ConnectingScreen()
                MainViewModel.Screen.LIVESTREAMS -> LivestreamListScreen()
                MainViewModel.Screen.DISCONNECTED -> DisconnectedScreen()
            }
        }
    }
}