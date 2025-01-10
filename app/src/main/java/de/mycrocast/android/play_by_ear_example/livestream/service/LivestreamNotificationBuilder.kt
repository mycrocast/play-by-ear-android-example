package de.mycrocast.android.play_by_ear_example.livestream.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import de.mycrocast.android.play_by_ear_example.R

/**
 * Builder for notifications of the LivestreamPlayService.
 *
 * @property context Context of foreground service, used to create Notification builder.
 * @param channelId Identifier of the Notification channel, where the notification should be "shown".
 */
class LivestreamNotificationBuilder(
    private val context: Context,
    channelId: String
) {
    private val builder: NotificationCompat.Builder = NotificationCompat.Builder(this.context, channelId)

    init {
        builder.setSmallIcon(R.drawable.ic_notification)
    }

    /**
     * Creates a basic notification for a livestream, displaying its title and language.
     * Contains a button the user can click to stop playing.
     *
     * @param title The title of the livestream.
     * @param language The language of the livestream.
     */
    fun createLivestreamNotification(
        title: String,
        language: String
    ): Notification {

        // update content of notification to display information about the current livestream
        builder.setContentTitle(title)
        builder.setContentText("Language: $language")

        // update actions
        builder.clearActions()
        builder.addAction(
            R.drawable.ic_stop_play,
            "Stop listen",
            PendingIntent.getBroadcast(
                context,
                0,
                LivestreamIntentReceiver.stopPlayIntent(),
                PendingIntent.FLAG_IMMUTABLE
            )
        )

        return builder.build()
    }

    /**
     * Creates a basic notification informing the user that their mobile phone has lost the connection to the PlayByEar server.
     */
    fun createClientConnectionLostNotification(): Notification {
        builder.setContentTitle("Client lost connection")
        builder.setContentText("Waiting for client to be reconnected.")
        return builder.build()
    }

    /**
     * Creates a basic notification informing the user the streamer of the livestream he is currently listening to has lost their connection to the PlayByEar server.
     */
    fun createStreamerConnectionLostNotification(): Notification {
        builder.setContentTitle("Streamer lost connection")
        builder.setContentText("Waiting for streamer to reconnect.")
        return builder.build()
    }
}