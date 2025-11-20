package de.mycrocast.android.play_by_ear_example.livestream.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import de.mycrocast.android.play_by_ear_example.livestream.play_state.PlayStateContainer
import de.mycrocast.android.play_by_ear_example.livestream.spot.domain.SpotPlayContainer
import de.mycrocast.android.play_by_ear.sdk.connection.domain.PlayByEarConnection
import de.mycrocast.android.play_by_ear.sdk.core.domain.PlayByEarLivestream
import de.mycrocast.android.play_by_ear.sdk.core.domain.PlayByEarSpot
import de.mycrocast.android.play_by_ear.sdk.livestream.container.domain.PlayByEarLivestreamContainer
import de.mycrocast.android.play_by_ear.sdk.livestream.loader.domain.PlayByEarLivestreamLoader
import de.mycrocast.android.play_by_ear.sdk.livestream.player.domain.PlayByEarLivestreamPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

/**
 * Foreground service used to play a specific livestream. Will also:
 * - create & show notifications
 * - collect updates of the specific livestream which is playing
 * - collect updates to the connection state (to the play by ear server) and tries to reestablish the connection if lost
 * - controls & observes the player used to play the specific livestream
 * - collects spots to play, play them via MediaPlayer and tracking them as finished playing
 */
@AndroidEntryPoint
class LivestreamPlayService : Service() {

    companion object {
        /**
         * Used to store & restore identifier of the livestream in the intent-bundle of this service.
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
         * Identifier of notification used to display information about currently playing spot.
         */
        private const val SPOT_NOTIFICATION_ID = 4

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

    @Inject
    lateinit var spotPlayContainer: SpotPlayContainer

    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)

    /**
     * Job which observes changes of the current connection state of PlayByEarConnection
     */
    private var observeConnectionState: Job? = null

    /**
     * Job which observes changes of the currently playing livestream
     */
    private var observeLivestream: Job? = null

    /**
     * Job which observes changes of the livestream player
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
    private lateinit var livestreamPlayer: PlayByEarLivestreamPlayer

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

    /**
     * Used to play spots.
     */
    private lateinit var spotPlayer: MediaPlayer

    /**
     * Queue which holds all spots which needs to be played.
     */
    private val spotQueue = ConcurrentLinkedQueue<PlayByEarSpot>()

    /**
     * Job which collects spots to play.
     */
    private var collectSpotsToPlay: Job? = null

    private val handler = Handler(Looper.getMainLooper())

    private val updateSpotPlayTimeRunnable: Runnable = object : Runnable {
        override fun run() {
            if (spotPlayer.isPlaying) {
                val currentTimeMillis = spotPlayer.currentPosition
                spotPlayContainer.updatePlayTime(currentTimeMillis)
                handler.postDelayed(this, 100)
            }
        }
    }

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

                    livestreamPlayer.stop()

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
                        livestreamPlayer.play(stream.token)
                        return@collect
                    }

                    // load() failed, therefore something went wrong -> stop service
                    onConnectionFailed()
                }
            }
        }

        spotPlayer = MediaPlayer()
        spotPlayer.setOnCompletionListener {
            onSpotPlayFinished()
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
        livestreamPlayer = playerFactory.create(this.application)

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

        collectSpotsToPlay = ioScope.launch {
            livestreamPlayer.spots.collect { spot ->
                spotQueue.add(spot)

                // start playing spots, if no spot is currently playing
                if (this@LivestreamPlayService.spotPlayContainer.currentSpot.value == null) {
                    playNextSpot()
                }
            }
        }

        // collect updates of the current play state of the player
        observePlayerState = ioScope.launch {
            livestreamPlayer.current.collect { playState ->
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
        if (livestreamPlayer.play(streamToken)) {
            return START_STICKY
        }

        onConnectionFailed()
        return START_NOT_STICKY
    }

    /**
     * The playing of a spot finished successfully.
     */
    private fun onSpotPlayFinished() {
        // track the finished play of the spot
        val currentSpot = spotPlayContainer.currentSpot.value
        currentSpot?.let {
            livestreamPlayer.trackSpotPlayFor(it)
        }

        // start next spot or stop playing spots
        playNextSpot()
    }

    /**
     * Determines whether to start playing the next spot (and muting the livestream)
     * or to stop playing spots and start playing the livestream again.
     */
    private fun playNextSpot() {
        val spot = spotQueue.poll()

        // are we currently finished playing spots?
        if (spot == null) {
            // close notification
            notificationManager.cancel(SPOT_NOTIFICATION_ID)

            // stop handler of play progress
            handler.removeCallbacks(updateSpotPlayTimeRunnable)

            // reset spot play container
            spotPlayContainer.reset()

            // unmute the livestream audio broadcast
            livestreamPlayer.unmute()
            return
        }

        // show notification
        if (checkPostNotificationPermission()) {
            val notification = notificationBuilder.createSpotNotification(spot)
            notificationManager.notify(SPOT_NOTIFICATION_ID, notification)
        }

        // update spot in spot play container
        spotPlayContainer.updateSpot(spot)

        // mute livestream audio broadcast
        livestreamPlayer.mute()

        // start playing spot
        spotPlayer.apply {
            reset()
            setDataSource(spot.audioUrl)
            prepare()
            start()
        }

        // start handler to collect play progress updates from spotPlayer
        handler.post(updateSpotPlayTimeRunnable)
    }

    /**
     * The streamer ended the broadcast. We wait a specific time for him to reconnect/start a new stream.
     */
    private fun onStreamerConnectionLost() {
        // stop current play process
        livestreamPlayer.stop()

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
        livestreamPlayer.play(stream.token)
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

    /**
     * Listen for app kill, e.g. by use swipe.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        // Stop the foreground service when the app is swiped away
        stopService()
    }

    override fun onDestroy() {
        super.onDestroy()

        // cancel all notifications
        notificationManager.cancel(CLIENT_CONNECTION_LOST_NOTIFICATION_ID)
        notificationManager.cancel(REMOTE_CONNECTION_LOST_NOTIFICATION_ID)

        // unregister intent receiver
        unregisterReceiver(intentReceiver)

        // ensure playing process is stopped
        if (::livestreamPlayer.isInitialized) {
            livestreamPlayer.stop()
        }

        // change current play state accordingly
        ioScope.launch {
            playStateContainer.onStop()
        }

        // cancel all currently active jobs
        observeConnectionState?.cancel()
        collectSpotsToPlay?.cancel()
        observePlayerState?.cancel()
        observeLivestream?.cancel()
        stopServiceDueToConnectionIssue?.cancel()

        // stop spot play progress handler if not already cleaned up
        handler.removeCallbacks(updateSpotPlayTimeRunnable)

        // stop spot play if currently running, clean player
        spotPlayer.apply {
            if (isPlaying) {
                stop()
            }

            reset()
            release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}