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

/**
 * Refactored MusicService that uses modular components
 * Much cleaner and easier to maintain
 */
class MusicServiceRefactored : Service() {
    
    // Modular components
    private var musicPlayer: MusicPlayer? = null
    private var musicStateManager: MusicStateManager? = null
    private var musicNotificationManager: MusicNotificationManager? = null
    private var musicBroadcastManager: MusicBroadcastManager? = null
    private var audioFocusManager: AudioFocusManager? = null
    
    // Service state
    private var currentSongId: String = ""
    private var currentTitle: String = ""
    private var currentUri: Uri? = null
    private var currentDuration: String = ""
    private var isPlaying = false
    private var shouldAutoPlay = false // Flag to track if song should auto-play
    
    private val binder = MusicBinder()
    private val handler = Handler(Looper.getMainLooper())
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
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
    
    private val progressUpdater = object : Runnable {
        override fun run() {
            try {
                if (musicPlayer?.isPlaying() == true) {
                    val position = musicPlayer?.getCurrentPosition() ?: 0
                    val duration = musicPlayer?.getDuration() ?: 0
                    
                    // Send progress broadcast on background thread
                    handler.post {
                        musicBroadcastManager?.broadcastProgress(
                            songId = currentSongId,
                            currentPosition = position,
                            duration = duration,
                            isPlaying = isPlaying
                        )
                    }
                    
                    // Save state every 10 seconds (reduced frequency)
                    if (position % 15000 < 1000) {
                        handler.post {
                            saveCurrentState(isPlaying)
                        }
                    }
                    
                    handler.postDelayed(this, 1000)
                } else {
                    handler.removeCallbacks(this)
                }
            } catch (e: Exception) {
                Log.e("MusicService", "Error in progress updater", e)
                handler.removeCallbacks(this)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        musicPlayer = MusicPlayer(this).apply {
            onPrepared = {
                handlePrepared()
            }
            onCompletion = {
                handleCompletion()
            }
            onError = { what, extra ->
                handleError(what, extra)
                true
            }
            onProgressUpdate = { position, duration ->
                handleProgressUpdate(position, duration)
            }
        }
        
        musicStateManager = MusicStateManager(this)
        musicNotificationManager = MusicNotificationManager(this)
        musicBroadcastManager = MusicBroadcastManager.getInstance(this)
        audioFocusManager = AudioFocusManager(this) { focusChange ->
            handleAudioFocusChange(focusChange)
        }
        
        // Setup callbacks
        setupAudioFocusCallbacks()
        setupMusicPlayerCallbacks()
        registerBroadcastReceiver()
        
        // Start foreground service
        startForeground(
            MusicNotificationManager.NOTIFICATION_ID,
            musicNotificationManager?.createNotification("", false, {}, {})
        )
        
        // Restore previous state if any
        restorePreviousState()
    }
    
    private fun setupAudioFocusCallbacks() {
        // No longer needed as we're using the callback in constructor
    }
    
    private fun setupMusicPlayerCallbacks() {
        musicPlayer?.onPrepared = {
            if (shouldAutoPlay) {
                if (audioFocusManager?.requestAudioFocus() == true) {
                    musicPlayer?.play()
                    isPlaying = true
                    updateUI()
                    startProgressUpdates()
                    saveCurrentState(isPlaying)
                }
            } else {
                updateUI()
                musicBroadcastManager?.broadcastSongState(
                    songId = currentSongId,
                    title = currentTitle,
                    duration = currentDuration,
                    isPlaying = isPlaying
                )
            }
        }
        
        musicPlayer?.onCompletion = {
            isPlaying = false
            updateUI()
            stopProgressUpdates()
            musicStateManager?.clearState()
        }
        
        musicPlayer?.onError = { what, extra ->
            Log.e("MusicService", "MediaPlayer error: what=$what, extra=$extra")
            isPlaying = false
            updateUI()
            stopProgressUpdates()
            true
        }
    }
    
    private fun registerBroadcastReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(ACTION_PLAY)
            addAction(ACTION_PAUSE)
            addAction(ACTION_STOP)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, intentFilter)
        }
    }
    
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY -> play()
                ACTION_PAUSE -> pause()
                ACTION_STOP -> stopSelf()
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stopSelf()
            ACTION_PLAY_SONG -> {
                val songId = intent.getStringExtra("songId") ?: return START_NOT_STICKY
                val title = intent.getStringExtra("title") ?: return START_NOT_STICKY
                val uriString = intent.getStringExtra("uri") ?: return START_NOT_STICKY
                val duration = intent.getStringExtra("duration") ?: return START_NOT_STICKY
                
                playSong(songId, title, Uri.parse(uriString), duration)
            }
            ACTION_SEEK -> {
                val position = intent.getIntExtra("position", 0)
                seekTo(position)
            }
            ACTION_GET_STATE -> {
                val shouldRestore = intent.getBooleanExtra("restore", false)
                if (shouldRestore && currentSongId.isEmpty()) {
                    restoreState()
                }
                sendCurrentState()
            }
        }
        
        return START_STICKY
    }
    
    private fun playSong(songId: String, title: String, uri: Uri, duration: String) {
        try {
            currentSongId = songId
            currentTitle = title
            currentUri = uri
            currentDuration = duration
            shouldAutoPlay = true  // Set this flag to true before loading song
            
            // Request audio focus before playing
            if (!audioFocusManager?.requestAudioFocus()!!) {
                Log.e("MusicService", "Could not get audio focus")
                return
            }
            
            musicPlayer?.loadSong(uri) {
                // Start playback when prepared
                musicPlayer?.play()
                isPlaying = true  // Set playing state immediately
                
                // Update notification
                musicNotificationManager?.updateNotification(
                    title = title,
                    isPlaying = true
                )
                
                // Broadcast state
                musicBroadcastManager?.broadcastSongState(
                    songId = songId,
                    title = title,
                    duration = duration,
                    isPlaying = true
                )
                
                // Save state
                saveCurrentState(isPlaying = true)
            }
        } catch (e: Exception) {
            Log.e("MusicService", "Error playing song", e)
            // Reset flags and broadcast error state
            shouldAutoPlay = false
            isPlaying = false
            musicBroadcastManager?.broadcastSongState(
                songId = songId,
                title = title,
                duration = duration,
                isPlaying = false
            )
        }
    }
    
    fun play() {
        if (!isPlaying) {
            if (audioFocusManager?.requestAudioFocus() == true) {
                try {
                    if (musicPlayer?.isReady() == true) {
                        musicPlayer?.play()
                        isPlaying = true
                        updateUI()
                        startProgressUpdates()
                        saveCurrentState(isPlaying)
                        
                        // Increment play count when starting playback
                        incrementPlayCount()
                    } else {
                        audioFocusManager?.abandonAudioFocus()
                    }
                } catch (e: Exception) {
                    Log.e("MusicService", "Error starting playback", e)
                    isPlaying = false
                    audioFocusManager?.abandonAudioFocus()
                    updateUI()
                }
            }
        }
    }
    
    fun pause() {
        if (isPlaying) {
            musicPlayer?.pause()
            isPlaying = false
            updateUI()
            stopProgressUpdates()
            saveCurrentState(isPlaying)
        }
    }
    
    fun seekTo(position: Int) {
        musicPlayer?.seekTo(position)
        saveCurrentState(isPlaying)
    }
    
    private fun updateUI() {
        musicNotificationManager?.updatePlaybackState(
            if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        )
        
        startForeground(
            MusicNotificationManager.NOTIFICATION_ID,
            musicNotificationManager?.createNotification(
                currentTitle, isPlaying,
                { if (isPlaying) pause() else play() },
                { stopSelf() }
            )
        )
        
        musicBroadcastManager?.broadcastSongState(
            songId = currentSongId,
            title = currentTitle,
            duration = currentDuration,
            isPlaying = isPlaying
        )
    }
    
    private fun startProgressUpdates() {
        handler.post(progressUpdater)
    }
    
    private fun stopProgressUpdates() {
        handler.removeCallbacks(progressUpdater)
    }
    
    private fun saveCurrentState(isPlaying: Boolean) {
        currentUri?.let { uri ->
            serviceScope.launch {
                try {
                    musicStateManager?.saveState(
                        songId = currentSongId,
                        title = currentTitle,
                        uri = uri,
                        duration = currentDuration,
                        position = musicPlayer?.getCurrentPosition() ?: 0,
                        isPlaying = isPlaying
                    )
                } catch (e: Exception) {
                    Log.e("MusicService", "Error saving state", e)
                }
            }
        }
    }
    
    private fun restoreState(): Boolean {
        val state = musicStateManager?.getState()
        return if (state != null) {
            currentSongId = state.songId
            currentTitle = state.title
            currentUri = Uri.parse(state.uri)
            currentDuration = state.duration
            isPlaying = false // Start paused
            shouldAutoPlay = false // Don't auto-play restored songs
            
            musicPlayer?.loadSong(Uri.parse(state.uri)) {
                musicPlayer?.seekTo(state.position)
                updateUI()
                // Don't auto-play, let user decide
            }
            true
        } else {
            false
        }
    }
    
    private fun sendCurrentState() {
        musicBroadcastManager?.broadcastSongState(
            songId = currentSongId,
            title = currentTitle,
            duration = currentDuration,
            isPlaying = isPlaying
        )
        
        if (isPlaying) {
            val position = musicPlayer?.getCurrentPosition() ?: 0
            val duration = musicPlayer?.getDuration() ?: 0
            musicBroadcastManager?.broadcastProgress(
                songId = currentSongId,
                currentPosition = position,
                duration = duration,
                isPlaying = isPlaying
            )
        } else {
            musicBroadcastManager?.broadcastProgress(
                songId = currentSongId,
                currentPosition = 0,
                duration = 0,
                isPlaying = false
            )
        }
    }
    
    private fun incrementPlayCount() {
        try {
            val songId = currentSongId.toIntOrNull()
            if (songId != null) {
                serviceScope.launch {
                    try {
                        val db = com.miu.meditationapp.databases.MusicDatabase.getInstance(this@MusicServiceRefactored)
                        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                        db.songDao().incrementPlayCount(songId, userId, java.util.Date())
                    } catch (e: Exception) {
                        Log.e("MusicService", "Error incrementing play count", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MusicService", "Error in incrementPlayCount", e)
        }
    }
    
    private fun handlePrepared() {
        // Start playback immediately after preparation
        musicPlayer?.play()
    }
    
    private fun handleCompletion() {
        stopPlayback()
    }
    
    private fun handleError(what: Int, extra: Int) {
        Log.e("MusicService", "MediaPlayer error: what=$what, extra=$extra")
        stopPlayback()
    }
    
    private fun handleProgressUpdate(position: Int, duration: Int) {
        musicBroadcastManager?.broadcastProgress(
            songId = currentSongId,
            currentPosition = position,
            duration = duration,
            isPlaying = musicPlayer?.isPlaying() ?: false
        )
    }
    
    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioFocusManager.AUDIOFOCUS_LOSS,
            AudioFocusManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pause()
            }
            AudioFocusManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                musicPlayer?.setVolume(0.3f, 0.3f)
            }
            AudioFocusManager.AUDIOFOCUS_GAIN -> {
                musicPlayer?.setVolume(1.0f, 1.0f)
                play()
            }
        }
    }
    
    private fun stopPlayback() {
        musicPlayer?.stop()
        musicNotificationManager?.cancelNotification()
        audioFocusManager?.abandonAudioFocus()
        musicStateManager?.clearState()
        stopSelf()
    }
    
    private fun restorePreviousState() {
        val state = musicStateManager?.getState()
        if (state != null) {
            playSong(
                songId = state.songId,
                title = state.title,
                uri = Uri.parse(state.uri),
                duration = state.duration
            )
            musicPlayer?.seekTo(state.position)
            if (!state.isPlaying) {
                pause()
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onDestroy() {
        super.onDestroy()
        
        saveCurrentState(isPlaying)
        
        try {
            unregisterReceiver(broadcastReceiver)
        } catch (e: Exception) {
            Log.e("MusicService", "Error unregistering broadcast receiver", e)
        }

        try {
            musicPlayer?.release()
            audioFocusManager?.abandonAudioFocus()
            musicNotificationManager?.release()
            // No need to unregister broadcast receiver since we're using singleton approach
        } catch (e: Exception) {
            Log.e("MusicService", "Error in onDestroy", e)
        }
    }
} 