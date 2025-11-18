package de.mycrocast.android.play_by_ear_example.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mycrocast.android.play_by_ear.sdk.connection.domain.PlayByEarConnection
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Screen which enables the user to start a new connection attempt.
 *
 * @param viewModel ViewModel used to start a new connection attempt via PlayByEarConnection.
 */
@Composable
fun ConnectionFailedScreen(
    viewModel: ConnectionFailedViewModel = hiltViewModel()
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Connection to the server could not be established.")
        Button(onClick = { viewModel.retryConnect() }) {
            Text(text = "Try again.")
        }
    }
}

/**
 * ViewModel for starting another connect attempt.
 *
 * @property connection Used to start another connect attempt.
 */
@HiltViewModel
class ConnectionFailedViewModel @Inject constructor(
    private val connection: PlayByEarConnection
) : ViewModel() {

    fun retryConnect() {
        viewModelScope.launch {
            connection.connect()
        }
    }
}