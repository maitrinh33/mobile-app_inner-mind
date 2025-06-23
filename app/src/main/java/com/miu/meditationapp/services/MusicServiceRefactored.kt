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
import android.widget.Toast
import com.miu.meditationapp.services.music.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth

/**
 * Refactored MusicService that uses modular components
 * Much cleaner and easier to maintain
 */
class MusicServiceRefactored : Service() {
    
    // Modular components
    private lateinit var musicPlayer: MusicPlayer
    private lateinit var audioFocusManager: AudioFocusManager
    private lateinit var stateManager: MusicStateManager
    private lateinit var notificationManager: MusicNotificationManager
    private lateinit var broadcastManager: MusicBroadcastManager
    
    // Service state
    private var currentSongId = ""
    private var currentSongTitle = ""
    private var currentSongUri: Uri? = null
    private var currentSongDuration = ""
    private var isPlaying = false
    private var shouldAutoPlay = false // Flag to track if song should auto-play
    
    private val binder = MusicBinder()
    private val handler = Handler(Looper.getMainLooper())
    
    companion object {
        const val ACTION_PLAY = "com.miu.meditationapp.PLAY"
        const val ACTION_PAUSE = "com.miu.meditationapp.PAUSE"
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
                if (musicPlayer.isPlaying()) {
                    val position = musicPlayer.getCurrentPosition()
                    val duration = musicPlayer.getDuration()
                    
                    // Send progress broadcast on background thread
                    handler.post {
                        broadcastManager.sendProgressBroadcast(
                            position, duration, isPlaying,
                            currentSongId, currentSongTitle
                        )
                    }
                    
                    // Save state every 10 seconds (reduced frequency)
                    if (position % 15000 < 1000) {
                        handler.post {
                            saveCurrentState()
                        }
                    }
                    
                    handler.postDelayed(this, 2000) // Reduced frequency from 1s to 2s
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
        
        // Initialize modular components
        musicPlayer = MusicPlayer(this)
        audioFocusManager = AudioFocusManager(this)
        stateManager = MusicStateManager(this)
        notificationManager = MusicNotificationManager(this)
        broadcastManager = MusicBroadcastManager
        
        // Setup callbacks
        setupAudioFocusCallbacks()
        setupMusicPlayerCallbacks()
        registerBroadcastReceiver()
        
        // Start foreground service
        startForeground(
            MusicNotificationManager.NOTIFICATION_ID,
            notificationManager.createNotification("", false, {}, {})
        )
    }
    
    private fun setupAudioFocusCallbacks() {
        audioFocusManager.onAudioFocusLost = { pause() }
        audioFocusManager.onAudioFocusLostTransient = { pause() }
        audioFocusManager.onAudioFocusGained = { /* Let user decide */ }
        audioFocusManager.onAudioFocusLostTransientCanDuck = {
            musicPlayer.setVolume(0.3f, 0.3f)
        }
    }
    
    private fun setupMusicPlayerCallbacks() {
        musicPlayer.onPrepared = {
            if (shouldAutoPlay) {
                if (audioFocusManager.requestAudioFocus()) {
                    musicPlayer.play()
                    isPlaying = true
                    updateUI()
                    startProgressUpdates()
                    saveCurrentState()
                }
            } else {
                updateUI()
                broadcastManager.sendStateBroadcast(isPlaying, currentSongId, currentSongTitle, currentSongDuration)
            }
        }
        
        musicPlayer.onCompletion = {
            isPlaying = false
            updateUI()
            stopProgressUpdates()
            stateManager.clearState()
        }
        
        musicPlayer.onError = { what, extra ->
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
                val songId = intent.getStringExtra("songId") ?: ""
                val title = intent.getStringExtra("title") ?: ""
                val uri = intent.getStringExtra("uri") ?: ""
                val duration = intent.getStringExtra("duration") ?: ""
                
                if (songId.isNotEmpty() && uri.isNotEmpty()) {
                    playSongById(songId, title, uri, duration)
                }
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
    
    fun playSongById(songId: String, title: String, uri: String, duration: String) {
        val effectiveTitle = if (title.isNotEmpty()) title else "Unknown Song"
        
        if (currentSongId == songId) {
            if (isPlaying) pause() else play()
        } else {
            switchToSong(songId, effectiveTitle, Uri.parse(uri), duration)
        }
    }
    
    private fun switchToSong(songId: String, title: String, uri: Uri, duration: String) {
        // Abandon current audio focus before switching
        audioFocusManager.abandonAudioFocus()
        
        saveCurrentState()
        stopProgressUpdates()
        
        // Reset playing state for new song
        isPlaying = false
        shouldAutoPlay = false
        
        currentSongId = songId
        currentSongTitle = title
        currentSongUri = uri
        currentSongDuration = duration
        
        // Send initial state broadcast with isPlaying=false
        broadcastManager.sendStateBroadcast(false, currentSongId, currentSongTitle, currentSongDuration)
        
        try {
            musicPlayer.loadSong(uri) {
                // onPrepared callback will handle the rest
            }
        } catch (e: Exception) {
            Log.e("MusicService", "Error loading song: $title", e)
            isPlaying = false
            updateUI()
            broadcastManager.sendStateBroadcast(false, currentSongId, currentSongTitle, currentSongDuration)
        }
    }
    
    fun play() {
        if (!isPlaying) {
            if (audioFocusManager.requestAudioFocus()) {
                try {
                    if (musicPlayer.isReady()) {
                        musicPlayer.play()
                        isPlaying = true
                        updateUI()
                        startProgressUpdates()
                        saveCurrentState()
                        
                        // Increment play count when starting playback
                        incrementPlayCount()
                    } else {
                        audioFocusManager.abandonAudioFocus()
                    }
                } catch (e: Exception) {
                    Log.e("MusicService", "Error starting playback", e)
                    isPlaying = false
                    audioFocusManager.abandonAudioFocus()
                    updateUI()
                }
            }
        }
    }
    
    fun pause() {
        if (isPlaying) {
            musicPlayer.pause()
            isPlaying = false
            updateUI()
            stopProgressUpdates()
            saveCurrentState()
        }
    }
    
    fun seekTo(position: Int) {
        musicPlayer.seekTo(position)
        saveCurrentState()
    }
    
    private fun updateUI() {
        notificationManager.updatePlaybackState(
            if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        )
        
        startForeground(
            MusicNotificationManager.NOTIFICATION_ID,
            notificationManager.createNotification(
                currentSongTitle, isPlaying,
                { if (isPlaying) pause() else play() },
                { stopSelf() }
            )
        )
        
        broadcastManager.sendStateBroadcast(isPlaying, currentSongId, currentSongTitle, currentSongDuration)
    }
    
    private fun startProgressUpdates() {
        handler.post(progressUpdater)
    }
    
    private fun stopProgressUpdates() {
        handler.removeCallbacks(progressUpdater)
    }
    
    private fun saveCurrentState() {
        currentSongUri?.let { uri ->
            stateManager.saveState(
                currentSongId, currentSongTitle, uri,
                currentSongDuration, musicPlayer.getCurrentPosition(), isPlaying
            )
        }
    }
    
    private fun restoreState(): Boolean {
        val state = stateManager.restoreState()
        return if (state != null) {
            currentSongId = state.songId
            currentSongTitle = state.title
            currentSongUri = Uri.parse(state.uri)
            currentSongDuration = state.duration
            isPlaying = false // Start paused
            shouldAutoPlay = false // Don't auto-play restored songs
            
            musicPlayer.loadSong(Uri.parse(state.uri)) {
                musicPlayer.seekTo(state.position)
                updateUI()
                // Don't auto-play, let user decide
            }
            true
        } else {
            false
        }
    }
    
    private fun sendCurrentState() {
        broadcastManager.sendStateBroadcast(isPlaying, currentSongId, currentSongTitle, currentSongDuration)
        
        if (isPlaying) {
            val position = musicPlayer.getCurrentPosition()
            val duration = musicPlayer.getDuration()
            broadcastManager.sendProgressBroadcast(position, duration, isPlaying, currentSongId, currentSongTitle)
        } else {
            broadcastManager.sendProgressBroadcast(0, 0, false, currentSongId, currentSongTitle)
        }
    }
    
    private fun incrementPlayCount() {
        try {
            val songId = currentSongId.toIntOrNull()
            if (songId != null) {
                CoroutineScope(Dispatchers.IO).launch {
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
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onDestroy() {
        super.onDestroy()
        
        saveCurrentState()
        
        try {
            musicPlayer.release()
            audioFocusManager.abandonAudioFocus()
            notificationManager.release()
            // No need to unregister broadcast receiver since we're using singleton approach
        } catch (e: Exception) {
            Log.e("MusicService", "Error in onDestroy", e)
        }
    }
} 