package de.mycrocast.android.play_by_ear_example.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mycrocast.android.play_by_ear.sdk.connection.domain.PlayByEarConnection
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Screen which enables the user to try a new reconnect attempt.
 *
 * @param viewModel ViewModel used to start a reconnect attempt in PlayByEarConnection.
 */
@Composable
fun DisconnectedScreen(
    viewModel: DisconnectedViewModel = hiltViewModel()
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Connection to the server was lost.")
        Button(onClick = { viewModel.reconnect() }) {
            Text(text = "Reconnect.")
        }
    }
}

/**
 * ViewModel for starting a reconnect attempt.
 *
 * @property connection Used to start a reconnect attempt.
 */
@HiltViewModel
class DisconnectedViewModel @Inject constructor(
    private val connection: PlayByEarConnection
) : ViewModel() {

    fun reconnect() {
        viewModelScope.launch {
            connection.reconnect()
        }
    }
}