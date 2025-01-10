package de.mycrocast.android.play_by_ear_example.livestream.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * Used to listen for custom intents, like:
 * - the stop playing intent.
 *
 * @property onStopListenReceived Invoked whenever a stop play intent was received.
 */
class LivestreamIntentReceiver(
    private val onStopListenReceived: () -> Unit
) : BroadcastReceiver() {

    companion object {
        // since android 14 (api level 34) we need to include the package name in custom intents to receive them,
        // if we are registering them as RECEIVER_NOT_EXPORTED.
        private const val PACKAGE_NAME = "de.mycrocast.android.play_by_ear_example"
        private const val STOP_LISTEN_ACTION = "$PACKAGE_NAME.STOP_LISTEN"

        /**
         * Creates a stop play intent.
         */
        fun stopPlayIntent(): Intent {
            val intent = Intent(STOP_LISTEN_ACTION)
            intent.setPackage(PACKAGE_NAME)
            return intent
        }
    }

    val filter: IntentFilter = IntentFilter()

    init {
        filter.addAction(STOP_LISTEN_ACTION)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        when {
            intent == null -> {
                return
            }

            intent.action == STOP_LISTEN_ACTION -> {
                onStopListenReceived()
            }
        }
    }
}