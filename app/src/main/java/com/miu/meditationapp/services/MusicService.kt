package com.miu.meditationapp.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.miu.meditationapp.services.music.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.miu.meditationapp.services.music.PlaybackStatsManager
import com.miu.meditationapp.services.music.MusicServiceBroadcastHandler
import com.miu.meditationapp.services.music.MusicProgressUpdater
import com.miu.meditationapp.services.music.MusicCommandHandler
import com.miu.meditationapp.services.music.MusicServiceEventHandler
import com.miu.meditationapp.services.music.PlaybackSession
import android.content.Context.RECEIVER_NOT_EXPORTED
import kotlinx.coroutines.delay

/**
 * Foreground music playback service. Handles playback, notifications, state, and broadcasts via modular helpers.
 */
class MusicServiceRefactored : Service() {
    
    // Modular components
    private var musicPlayer: MusicPlayer? = null
    private var musicStateManager: MusicStateManager? = null
    private var musicNotificationManager: MusicNotificationManager? = null
    private var musicBroadcastManager: MusicBroadcastManager? = null
    private var audioFocusManager: AudioFocusManager? = null
    private var playbackStatsManager: PlaybackStatsManager? = null
    
    // Service state (encapsulated)
    private var session = PlaybackSession()
    
    /**
     * Public getter for session state, for use by event handlers and other helpers.
     */
    val sessionPublic: PlaybackSession
        get() = session
    
    private val binder = MusicBinder()
    private val handler = Handler(Looper.getMainLooper())
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private var broadcastHandler: MusicServiceBroadcastHandler? = null
    private var progressUpdater: MusicProgressUpdater? = null
    private var commandHandler: MusicCommandHandler? = null
    private var eventHandler: MusicServiceEventHandler? = null
    
    private val notificationActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.miu.meditationapp.PLAY_PAUSE" -> {
                    if (session.isPlaying) pause() else play()
                }
                "com.miu.meditationapp.STOP" -> stopPlayback()
            }
        }
    }
    
    companion object {
        const val ACTION_PLAY = "com.miu.meditationapp.PLAY"
        const val ACTION_PAUSE = "com.miu.meditationapp.PAUSE"
        const val ACTION_RESUME = "com.miu.meditationapp.ACTION_RESUME"
        const val ACTION_STOP = "com.miu.meditationapp.STOP"
        const val ACTION_PLAY_SONG = "com.miu.meditationapp.PLAY_SONG"
        const val ACTION_SEEK = "com.miu.meditationapp.SEEK"
        const val ACTION_GET_STATE = "com.miu.meditationapp.GET_STATE"
    }
    
    inner class MusicBinder : Binder() {
        fun getService(): MusicServiceRefactored = this@MusicServiceRefactored
    }
    
    override fun onCreate() {
        super.onCreate()
        initMusicPlayer()
        initStateManager()
        initNotificationManager()
        initBroadcastManager()
        initAudioFocusManager()
        initPlaybackStatsManager()
        initHandlers()
        val filter = IntentFilter().apply {
            addAction("com.miu.meditationapp.PLAY_PAUSE")
            addAction("com.miu.meditationapp.STOP")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            registerReceiver(notificationActionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationActionReceiver, filter)
        }
        startForeground(
            MusicNotificationManager.NOTIFICATION_ID,
            musicNotificationManager?.createNotification(
                title = "",
                isPlaying = false,
                currentPosition = 0,
                duration = 0,
                onPlayPauseClick = {},
                onStopClick = {}
            )
        )
        restorePreviousState()
    }
    
    private fun initMusicPlayer() {
        musicPlayer = MusicPlayer(this).apply {
            onPrepared = { eventHandler?.handlePrepared() }
            onCompletion = { eventHandler?.handleCompletion() }
            onError = { what, extra -> eventHandler?.handleError(what, extra); true }
            onProgressUpdate = { position, duration -> eventHandler?.handleProgressUpdate(position, duration) }
            onPlaybackStarted = {
                musicBroadcastManager?.broadcastProgress(
                    songId = session.currentSongId,
                    currentPosition = musicPlayer?.getCurrentPosition() ?: 0,
                    duration = musicPlayer?.getDuration() ?: 0,
                    isPlaying = session.isPlaying
                )
            }
        }
    }
    
    private fun initStateManager() {
        musicStateManager = MusicStateManager(this)
    }
    
    private fun initNotificationManager() {
        musicNotificationManager = MusicNotificationManager(this)
        musicNotificationManager?.setMediaSessionCallback(object : android.support.v4.media.session.MediaSessionCompat.Callback() {
            override fun onPlay() {
                play()
            }
            override fun onPause() {
                pause()
            }
            override fun onSeekTo(pos: Long) {
                seekTo(pos.toInt())
            }
        })
    }
    
    private fun initBroadcastManager() {
        musicBroadcastManager = MusicBroadcastManager.getInstance(this)
    }
    
    private fun initAudioFocusManager() {
        audioFocusManager = AudioFocusManager(this) { focusChange ->
            eventHandler?.handleAudioFocusChange(focusChange)
        }
    }
    
    private fun initPlaybackStatsManager() {
        playbackStatsManager = PlaybackStatsManager(this, serviceScope)
    }
    
    private fun initHandlers() {
        broadcastHandler = MusicServiceBroadcastHandler(
            context = this,
            onPlay = { play() },
            onPause = { pause() },
            onStop = { stopSelf() },
            actions = listOf(ACTION_PLAY, ACTION_PAUSE, ACTION_STOP)
        ).also { it.register() }
        
        progressUpdater = MusicProgressUpdater(
            isPlayingProvider = { musicPlayer?.isPlaying() == true },
            getPosition = { musicPlayer?.getCurrentPosition() ?: 0 },
            getDuration = { musicPlayer?.getDuration() ?: 0 },
            onProgress = { position, duration, isPlaying ->
                musicBroadcastManager?.broadcastProgress(
                    songId = session.currentSongId,
                    currentPosition = position,
                    duration = duration,
                    isPlaying = isPlaying
                )
                musicNotificationManager?.updateNotification(
                    title = session.currentTitle,
                    isPlaying = session.isPlaying,
                    currentPosition = position,
                    duration = duration
                )
            },
            onSaveState = {
                serviceScope.launch { saveCurrentState(session.isPlaying) }
            }
        )
        
        commandHandler = MusicCommandHandler(
            onPlay = { play() },
            onPause = { pause() },
            onStop = { stopSelf() },
            onPlaySong = { songId, title, uri, duration -> playSong(songId, title, uri, duration) },
            onSeek = { position -> seekTo(position) },
            onGetState = { shouldRestore ->
                if (shouldRestore && session.currentSongId.isEmpty()) {
                    restoreState()
                }
                sendCurrentState()
            }
        )
        
        eventHandler = MusicServiceEventHandler(
            service = this,
            musicPlayer = musicPlayer,
            musicNotificationManager = musicNotificationManager,
            musicBroadcastManager = musicBroadcastManager,
            audioFocusManager = audioFocusManager,
            musicStateManager = musicStateManager,
            playbackStatsManager = playbackStatsManager
        )
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        commandHandler?.handle(intent)
        return START_STICKY
    }
    
    /**
     * Play a new song by id, title, uri, and duration.
     */
    private fun playSong(songId: String, title: String, uri: Uri, duration: String) {
        session.currentSongId = songId
        session.currentTitle = title
        session.currentUri = uri
        session.currentDuration = duration
        session.shouldAutoPlay = true
        if (audioFocusManager?.requestAudioFocus() != true) {
            Log.e("MusicService", "Could not get audio focus")
            return
        }
        musicPlayer?.playSong(songId, title, uri, duration) {
            session.isPlaying = true
            musicNotificationManager?.updateNotification(
                title = title,
                isPlaying = true,
                currentPosition = musicPlayer?.getCurrentPosition() ?: 0,
                duration = musicPlayer?.getDuration() ?: 0
            )
            musicBroadcastManager?.broadcastSongState(
                songId = songId,
                title = title,
                duration = duration,
                isPlaying = true
            )
            musicBroadcastManager?.broadcastProgress(
                songId = session.currentSongId,
                currentPosition = musicPlayer?.getCurrentPosition() ?: 0,
                duration = musicPlayer?.getDuration() ?: 0,
                isPlaying = session.isPlaying
            )
            startProgressUpdates()
            serviceScope.launch { saveCurrentState(session.isPlaying) }
        }
    }
    
    /**
     * Resume playback if paused.
     */
    fun play() {
        if (!session.isPlaying) {
            if (audioFocusManager?.requestAudioFocus() == true) {
                try {
                    if (musicPlayer?.isReady() == true) {
                        musicPlayer?.play()
                        session.isPlaying = true
                        serviceScope.launch {
                            delay(100)
                            updateUI()
                        }
                        startProgressUpdates()
                        serviceScope.launch { saveCurrentState(session.isPlaying) }
                        incrementPlayCount()
                        musicBroadcastManager?.broadcastSongState(
                            songId = session.currentSongId,
                            title = session.currentTitle,
                            duration = session.currentDuration,
                            isPlaying = session.isPlaying
                        )
                        musicBroadcastManager?.broadcastProgress(
                            songId = session.currentSongId,
                            currentPosition = musicPlayer?.getCurrentPosition() ?: 0,
                            duration = musicPlayer?.getDuration() ?: 0,
                            isPlaying = session.isPlaying
                        )
                    } else {
                        audioFocusManager?.abandonAudioFocus()
                    }
                } catch (e: Exception) {
                    Log.e("MusicService", "Error starting playback", e)
                    session.isPlaying = false
                    audioFocusManager?.abandonAudioFocus()
                    updateUI()
                }
            }
        }
    }
    
    /**
     * Pause playback if playing.
     */
    fun pause() {
        if (session.isPlaying) {
            musicPlayer?.pausePlayback()
            session.isPlaying = false
            serviceScope.launch {
                delay(100)
                updateUI()
            }
            stopProgressUpdates()
            serviceScope.launch { saveCurrentState(session.isPlaying) }
        }
    }
    
    /**
     * Seek to a specific position in the current song.
     */
    private fun seekTo(position: Int) {
        musicPlayer?.seekToPosition(position)
        serviceScope.launch { saveCurrentState(session.isPlaying) }
    }
    
    private fun updateUI() {
        musicNotificationManager?.updateUI(
            title = session.currentTitle,
            isPlaying = session.isPlaying,
            currentPosition = musicPlayer?.getCurrentPosition() ?: 0,
            duration = musicPlayer?.getDuration() ?: 0,
            startForeground = { notification ->
                startForeground(MusicNotificationManager.NOTIFICATION_ID, notification)
            },
            broadcastSongState = {
                musicBroadcastManager?.broadcastSongState(
                    songId = session.currentSongId,
                    title = session.currentTitle,
                    duration = session.currentDuration,
                    isPlaying = session.isPlaying
                )
            }
        )
    }
    
    private fun startProgressUpdates() {
        progressUpdater?.start()
    }
    
    private fun stopProgressUpdates() {
        progressUpdater?.stop()
    }
    
    private suspend fun saveCurrentState(isPlaying: Boolean) {
        musicStateManager?.saveCurrentState(
            songId = session.currentSongId,
            title = session.currentTitle,
            uri = session.currentUri,
            duration = session.currentDuration,
            position = musicPlayer?.getCurrentPosition() ?: 0,
            isPlaying = isPlaying
        )
    }
    
    private fun restoreState(): MusicStateManager.MusicState? {
        return musicStateManager?.restoreState()
    }
    
    private fun restorePreviousState(): MusicStateManager.MusicState? {
        return musicStateManager?.restorePreviousState()
    }
    
    private fun sendCurrentState() {
        eventHandler?.sendCurrentState()
    }
    
    private fun incrementPlayCount() {
        eventHandler?.incrementPlayCount()
    }
    
    /**
     * Stop playback and release resources.
     */
    fun stopPlayback() {
        musicPlayer?.stopPlayback()
        musicNotificationManager?.cancelNotification()
        audioFocusManager?.abandonAudioFocus()
        musicStateManager?.clearState()
        stopSelf()
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch { saveCurrentState(session.isPlaying) }
        try { broadcastHandler?.unregister() } catch (_: Exception) {}
        try {
            unregisterReceiver(notificationActionReceiver)
        } catch (_: Exception) {}
        try {
            musicPlayer?.release()
            audioFocusManager?.abandonAudioFocus()
            musicNotificationManager?.release()
        } catch (e: Exception) {
            Log.e("MusicService", "Error in onDestroy", e)
        }
    }
} 