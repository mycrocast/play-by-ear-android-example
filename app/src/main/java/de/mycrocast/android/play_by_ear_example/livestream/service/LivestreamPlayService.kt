package de.mycrocast.android.play_by_ear_example.livestream.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import de.mycrocast.android.play_by_ear.sdk.connection.domain.PlayByEarConnection
import de.mycrocast.android.play_by_ear.sdk.core.domain.PlayByEarLivestream
import de.mycrocast.android.play_by_ear.sdk.livestream.container.domain.PlayByEarLivestreamContainer
import de.mycrocast.android.play_by_ear.sdk.livestream.loader.domain.PlayByEarLivestreamLoader
import de.mycrocast.android.play_by_ear.sdk.livestream.player.domain.PlayByEarLivestreamPlayer
import de.mycrocast.android.play_by_ear_example.livestream.play_state.PlayStateContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service used to play the audio broadcast of a specific livestream.
 * Will also:
 * - create & show notifications
 * - collect updates of the specific livestream which is playing,
 * - collect updates to the connection state (of the PlayByEar backend) and tries to reestablish the connection if lost,
 * - controls & observes the player used to play the audio broadcast.
 */
@AndroidEntryPoint
class LivestreamPlayService : Service() {

    companion object {
        /**
         * Used to store & restore the token of the livestream in the intent-bundle of this service.
         */
        private const val LIVESTREAM_TOKEN = "livestream_token"

        /**
         * Used to store & restore identifier of the streamer in the intent-bundle of this service.
         */
        private const val LIVESTREAM_BROADCASTER_ID = "livestream_streamer_id"

        /**
         * Used to store & restore title of the livestream in the intent-bundle of this service.
         */
        private const val LIVESTREAM_TITLE = "livestream_title"

        /**
         * Used to store & restore language of the livestream in the intent-bundle of this service.
         */
        private const val LIVESTREAM_LANGUAGE = "livestream_language"

        /**
         * Identifier of notification used to display information about the livestream.
         */
        private const val LIVESTREAM_NOTIFICATION_ID = 1

        /**
         * Identifier of notification used to display client side connection issue.
         */
        private const val CLIENT_CONNECTION_LOST_NOTIFICATION_ID = 2

        /**
         * Identifier of notification used to display remote (streamer) side connection issue.
         */
        private const val REMOTE_CONNECTION_LOST_NOTIFICATION_ID = 3

        /**
         * Identifier of notification channel used for backwards compatibility.
         */
        private const val COMPAT_NOTIFICATION_CHANNEL_ID = ""

        /**
         * Identifier of notification channel used to display notifications for this service.
         */
        private const val NOTIFICATION_CHANNEL_ID = "pbe_livestream_listener_channel_id"

        /**
         * Name of notification channel used to display notifications for this service.
         */
        private const val NOTIFICATION_CHANNEL_NAME = "PlayByEar Livestream Player Service"

        /**
         * Creates a new instance of LivestreamPlayService.
         *
         * @param context Context from which the foreground service will be started.
         * @param livestream The specific livestream to play.
         */
        fun newInstance(context: Context, livestream: PlayByEarLivestream): Intent {
            val result = Intent(context, LivestreamPlayService::class.java)
            result.putExtra(LIVESTREAM_TOKEN, livestream.token)
            result.putExtra(LIVESTREAM_BROADCASTER_ID, livestream.streamerId)
            result.putExtra(LIVESTREAM_TITLE, livestream.title)
            result.putExtra(LIVESTREAM_LANGUAGE, livestream.language.native)
            return result
        }

        /**
         * Stops all currently active instances of LivestreamPlayService.
         *
         * @param context
         */
        fun stop(context: Context): Intent {
            return Intent(context, LivestreamPlayService::class.java)
        }
    }

    @Inject
    lateinit var connection: PlayByEarConnection

    @Inject
    lateinit var streamContainer: PlayByEarLivestreamContainer

    @Inject
    lateinit var playStateContainer: PlayStateContainer

    @Inject
    lateinit var playerFactory: PlayByEarLivestreamPlayer.Factory

    @Inject
    lateinit var streamLoader: PlayByEarLivestreamLoader

    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)

    /**
     * Job which observes changes of the current connection state of PlayByEarConnection
     */
    private var observeConnectionState: Job? = null

    /**
     * Job which observes changes of the currently playing livestream via PlayByEarLivestreamContainer
     */
    private var observeLivestream: Job? = null

    /**
     * Job which observes changes of the PlayByEarLivestreamPlayer
     */
    private var observePlayerState: Job? = null

    /**
     * Job which stops the service after a specific time due to streamer connection issues
     */
    private var stopServiceDueToConnectionIssue: Job? = null

    /**
     * Used to create notification channel, show and update notifications.
     */
    private lateinit var notificationManager: NotificationManagerCompat

    /**
     * Used to create our custom notifications.
     */
    private lateinit var notificationBuilder: LivestreamNotificationBuilder

    /**
     * Used to receive our custom intents.
     */
    private lateinit var intentReceiver: LivestreamIntentReceiver

    /**
     * Used to control the playing process of the livestream.
     */
    private lateinit var player: PlayByEarLivestreamPlayer

    /**
     * Identifier of the streamer of the livestream.
     */
    private var streamerId: Long = -1L

    /**
     * Has client lost connection?
     */
    private var isClientConnectionLost = false

    /**
     * Has streamer lost connection?
     */
    private var isStreamerConnectionLost = false

    override fun onCreate() {
        super.onCreate()

        // create everything needed to create & show notifications
        notificationManager = NotificationManagerCompat.from(this)
        val channelId = createNotificationChannel(notificationManager)
        notificationBuilder = LivestreamNotificationBuilder(this, channelId)

        // create and register the intent receiver
        intentReceiver = LivestreamIntentReceiver { stopService() }
        ContextCompat.registerReceiver(
            this,
            intentReceiver,
            intentReceiver.filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        observeConnectionState = ioScope.launch {
            connection.currentState.collect {
                // did we just lost the connection? if yes:
                // - show notification
                // - stop playing
                // - try to reconnect
                if (it == PlayByEarConnection.State.DISCONNECTED && !isClientConnectionLost) {
                    isClientConnectionLost = true

                    if (checkPostNotificationPermission()) {
                        val notification = notificationBuilder.createClientConnectionLostNotification()
                        notificationManager.notify(
                            CLIENT_CONNECTION_LOST_NOTIFICATION_ID,
                            notification
                        )
                    }

                    player.stop()

                    // TODO: better automatic reconnect
                    while (!connection.reconnect()) {
                        delay(2000)
                    }
                }

                // did we just establish a connection after a connection loss? if yes:
                // - dismiss notification
                // - (re-) start playing if possible
                if (it == PlayByEarConnection.State.CONNECTED && isClientConnectionLost) {
                    isClientConnectionLost = false

                    notificationManager.cancel(CLIENT_CONNECTION_LOST_NOTIFICATION_ID)

                    // we need to make sure that we refresh the list of currently active livestreams,
                    // because in the time we were disconnected, the stream could have changed or (even worse) was stopped by the streamer in the meantime
                    if (streamLoader.load()) {
                        val stream = streamContainer.findByStreamerId(streamerId).firstOrNull()
                        if (stream == null) {
                            // streamer lost connection or ended the stream, will be handled by the observeLivestream - job
                            return@collect
                        }

                        // stream has not ended, start playing with "refreshed" stream
                        player.play(stream.token)
                        return@collect
                    }

                    // load() failed, therefore something went wrong -> stop service
                    onConnectionFailed()
                }
            }
        }
    }

    /**
     * Checks whether the application has the POST_NOTIFICATIONS permission or not.
     */
    private fun checkPostNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }

        // no need to check permission for android api lower than 33
        return true
    }

    /**
     * Creates the notification channel.
     *
     * @param notificationManager The notification manger used to create the channel.
     */
    private fun createNotificationChannel(notificationManager: NotificationManagerCompat): String {
        // no need to create a notification channel for android api lower than 26
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return COMPAT_NOTIFICATION_CHANNEL_ID
        }

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_NONE
        )
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE

        notificationManager.createNotificationChannel(channel)
        return channel.id
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val streamToken = intent?.getStringExtra(LIVESTREAM_TOKEN)
        if (streamToken == null) { // check for invalid streamToken
            onConnectionFailed()
            return START_NOT_STICKY
        }

        val streamerId = intent.getLongExtra(LIVESTREAM_BROADCASTER_ID, -1L)
        if (streamerId == -1L) { // check for invalid streamerId
            onConnectionFailed()
            return START_NOT_STICKY
        }

        this.streamerId = streamerId

        // create the foreground notification
        val title = intent.getStringExtra(LIVESTREAM_TITLE) ?: ""
        val language = intent.getStringExtra(LIVESTREAM_LANGUAGE) ?: ""
        val notification = notificationBuilder.createLivestreamNotification(title, language)
        startForeground(LIVESTREAM_NOTIFICATION_ID, notification)

        // create livestream player
        player = playerFactory.create(this.application)

        // collect updates for the specific livestream
        observeLivestream = ioScope.launch {
            streamContainer.findByStreamerId(streamerId).collect {
                if (it == null) { // streamer ended the livestream or lost their connection
                    if (!isStreamerConnectionLost) {
                        isStreamerConnectionLost = true
                        onStreamerConnectionLost()
                    }

                    return@collect
                }

                if (isStreamerConnectionLost) {
                    isStreamerConnectionLost = false
                    onStreamerConnectionReestablished(it)
                }

                updateLivestreamNotification(it)
            }
        }

        // collect updates of the current play state of the player
        observePlayerState = ioScope.launch {
            player.current.collect { playState ->
                when (playState) {
                    is PlayByEarLivestreamPlayer.PlayState.New -> {
                        // initial state of the player, nothing to do.
                    }

                    is PlayByEarLivestreamPlayer.PlayState.Connecting -> {
                        playStateContainer.onConnect(playState.streamToken)
                    }

                    is PlayByEarLivestreamPlayer.PlayState.Playing -> {
                        playStateContainer.onPlay(playState.streamToken)
                    }

                    is PlayByEarLivestreamPlayer.PlayState.Disconnected -> {
                        playStateContainer.onDisconnect(playState.streamToken)
                    }

                    is PlayByEarLivestreamPlayer.PlayState.Failed -> {
                        onConnectionFailed()
                    }

                    is PlayByEarLivestreamPlayer.PlayState.Closed -> {
                        // was the player closed because either the client or the streamer lost their connection?
                        if (isClientConnectionLost || isStreamerConnectionLost) {
                            playStateContainer.onDisconnect(streamToken)
                            return@collect
                        }

                        playStateContainer.onStop()
                    }
                }
            }
        }

        // start the playing process of the livestream
        if (player.play(streamToken)) {
            return START_STICKY
        }

        onConnectionFailed()
        return START_NOT_STICKY
    }

    /**
     * The streamer ended the broadcast. We wait a specific time for him to reconnect/start a new stream.
     */
    private fun onStreamerConnectionLost() {
        // stop current play process
        player.stop()

        // show notification indicating connection loss for streamer
        if (checkPostNotificationPermission()) {
            val notification = notificationBuilder.createStreamerConnectionLostNotification()
            notificationManager.notify(REMOTE_CONNECTION_LOST_NOTIFICATION_ID, notification)
        }

        // start job which stops the service after it has completed
        stopServiceDueToConnectionIssue = ioScope.launch {
            delay(10000)
            stopService()
        }
    }

    /**
     * The streamer restarted their broadcast or reconnected to their broadcast, before the timer elapsed.
     *
     * @param stream Their new livestream.
     */
    private fun onStreamerConnectionReestablished(stream: PlayByEarLivestream) {
        // cancel the stop service timer
        stopServiceDueToConnectionIssue?.cancel()

        // dismiss the notification
        notificationManager.cancel(REMOTE_CONNECTION_LOST_NOTIFICATION_ID)

        // start playing their new livestream
        player.play(stream.token)
    }

    /**
     * A unrecoverable problem occurred while connecting to audio broadcast of the livestream.
     */
    private fun onConnectionFailed() {
        stopService()
    }

    /**
     * Stops this foreground service and removes the foreground notification.
     */
    private fun stopService() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            @Suppress("DEPRECATION")
            this.stopForeground(true)
        } else {
            this.stopForeground(STOP_FOREGROUND_REMOVE)
        }

        this.stopSelf()
    }

    /**
     * Updates the notification for the livestream.
     *
     * @param livestream The updated livestream which information to display in the notification.
     */
    private fun updateLivestreamNotification(livestream: PlayByEarLivestream) {
        if (checkPostNotificationPermission()) {
            val notification = notificationBuilder.createLivestreamNotification(
                livestream.title,
                livestream.language.native
            )
            notificationManager.notify(LIVESTREAM_NOTIFICATION_ID, notification)
            return
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // cancel all notifications
        notificationManager.cancel(CLIENT_CONNECTION_LOST_NOTIFICATION_ID)
        notificationManager.cancel(REMOTE_CONNECTION_LOST_NOTIFICATION_ID)

        // unregister intent receiver
        unregisterReceiver(intentReceiver)

        // ensure playing process is stopped
        if (::player.isInitialized) {
            player.stop()
        }

        // change current play state accordingly
        ioScope.launch {
            playStateContainer.onStop()
        }

        // cancel all currently active jobs
        observeConnectionState?.cancel()
        observePlayerState?.cancel()
        observeLivestream?.cancel()
        stopServiceDueToConnectionIssue?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}