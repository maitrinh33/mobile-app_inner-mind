package com.miu.meditationapp.services.music

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles the core MediaPlayer functionality and audio playback
 */
class MusicPlayer(private val context: Context) {
    
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var isPlaying = false
    private var currentPosition = 0
    private var currentDuration = 0
    private var isPreparing = false
    private var currentUri: Uri? = null
    
    // Callbacks
    var onPrepared: (() -> Unit)? = null
    var onCompletion: (() -> Unit)? = null
    var onError: ((Int, Int) -> Boolean)? = null
    var onProgressUpdate: ((Int, Int) -> Unit)? = null
    var onPlaybackStarted: (() -> Unit)? = null
    
    init {
        setupAudioManager()
    }
    
    private fun setupAudioManager() {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    
    fun loadSong(uri: Uri, onPreparedCallback: (() -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("MusicPlayer", "loadSong called for URI: $uri")
                if (isPreparing && uri == currentUri) {
                    Log.d("MusicPlayer", "Same song is already preparing, waiting...")
                    return@launch
                }
                currentUri = uri
                isPreparing = true
                withContext(Dispatchers.Main) { release() }
                val fileCheckPassed = if (uri.scheme == "file" || uri.scheme == null) {
                    val file = File(uri.path ?: "")
                    if (!file.exists() || !file.canRead()) {
                        Log.e("MusicPlayer", "File does not exist or is not readable: "+file.absolutePath)
                        false
                    } else {
                        Log.d("MusicPlayer", "File exists and is readable: "+file.absolutePath)
                        true
                    }
                } else true
                if (!fileCheckPassed) return@launch

                val player = MediaPlayer()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    player.setAudioAttributes(AudioAttributes.Builder().apply {
                        setUsage(AudioAttributes.USAGE_MEDIA)
                        setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    }.build())
                } else {
                    @Suppress("DEPRECATION")
                    player.setAudioStreamType(AudioManager.STREAM_MUSIC)
                }
                val headers = if (uri.scheme?.startsWith("http") == true) {
                    HashMap<String, String>().apply {
                        put("User-Agent", "MeditationApp/1.0")
                    }
                } else null
                var setDataSourceOnMain = false
                when {
                    uri.scheme?.startsWith("http") == true -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            player.setDataSource(context, uri, headers ?: emptyMap())
                        } else {
                            player.setDataSource(uri.toString())
                        }
                    }
                    uri.scheme == "file" || uri.scheme == null -> {
                        uri.path?.let { player.setDataSource(it) }
                    }
                    else -> {
                        setDataSourceOnMain = true
                    }
                }
                if (setDataSourceOnMain) {
                    withContext(Dispatchers.Main) {
                        player.setDataSource(context, uri)
                    }
                }
                withContext(Dispatchers.Main) {
                    mediaPlayer = player
                    player.setOnPreparedListener {
                        Log.d("MusicPlayer", "MediaPlayer prepared for URI: $uri")
                        isPreparing = false
                        setVolume(1.0f, 1.0f)
                        onPreparedCallback?.invoke()
                        onPrepared?.invoke()
                        play()
                        onPlaybackStarted?.invoke()
                    }
                    player.setOnCompletionListener {
                        Log.d("MusicPlayer", "MediaPlayer completed for URI: $uri")
                        this@MusicPlayer.isPlaying = false
                        onCompletion?.invoke()
                    }
                    player.setOnErrorListener { mp, what, extra ->
                        Log.e("MusicPlayer", "MediaPlayer error: what=$what, extra=$extra, uri=$uri")
                        isPreparing = false
                        this@MusicPlayer.isPlaying = false
                        if (what == MediaPlayer.MEDIA_ERROR_UNSUPPORTED) {
                            Log.e("MusicPlayer", "Unsupported format or URL error")
                        }
                        onError?.invoke(what, extra) ?: true
                    }
                    player.prepareAsync()
                }
            } catch (e: Exception) {
                isPreparing = false
                Log.e("MusicPlayer", "Exception in loadSong for URI: $uri", e)
                withContext(Dispatchers.Main) { release() }
                throw e
            }
        }
    }
    
    private fun checkFileAccessibility(uri: Uri) {
        try {
            when (uri.scheme) {
                "file" -> {
                    val file = File(uri.path ?: "")
                    if (!file.exists() || !file.canRead()) {
                        Log.w("MusicPlayer", "File not accessible: ${file.absolutePath}")
                    }
                }
                "content" -> {
                    // For content URIs, we'll let MediaPlayer handle it
                }
                "android.resource" -> {
                    // For resource URIs, we'll let MediaPlayer handle it
                }
                else -> {
                    Log.w("MusicPlayer", "Unknown URI scheme: ${uri.scheme}")
                }
            }
        } catch (e: Exception) {
            Log.w("MusicPlayer", "Could not check file accessibility: ${e.message}")
        }
    }
    
    @Synchronized
    fun play() {
        try {
            if (isPreparing) {
                Log.d("MusicPlayer", "Cannot play while preparing")
                return
            }
            if (!(mediaPlayer?.isPlaying ?: false)) {
                Log.d("MusicPlayer", "Starting playback at position: ${mediaPlayer?.currentPosition}")
                mediaPlayer?.start()
                isPlaying = true
            }
        } catch (e: Exception) {
            Log.e("MusicPlayer", "Error playing", e)
        }
    }
    
    @Synchronized
    fun pause() {
        try {
            if (mediaPlayer?.isPlaying ?: false) {
                mediaPlayer?.pause()
                isPlaying = false
            }
        } catch (e: Exception) {
            Log.e("MusicPlayer", "Error pausing", e)
        }
    }
    
    @Synchronized
    fun stop() {
        try {
            if (mediaPlayer?.isPlaying ?: false) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.reset()
            isPlaying = false
            isPreparing = false
        } catch (e: Exception) {
            Log.e("MusicPlayer", "Error stopping", e)
        }
    }
    
    fun seekTo(position: Int) {
        try {
            Log.d("MusicPlayer", "Seeking to position: $position (ms)")
            mediaPlayer?.seekTo(position)
        } catch (e: Exception) {
            Log.e("MusicPlayer", "Error seeking", e)
        }
    }
    
    fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: currentPosition
        } catch (e: Exception) {
            Log.e("MusicPlayer", "Error getting current position", e)
            0
        }
    }
    
    fun getDuration(): Int {
        return try {
            mediaPlayer?.duration ?: currentDuration
        } catch (e: Exception) {
            Log.e("MusicPlayer", "Error getting duration", e)
            0
        }
    }
    
    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying ?: false
        } catch (e: Exception) {
            Log.e("MusicPlayer", "Error checking if playing", e)
            false
        }
    }
    
    fun isReady(): Boolean {
        return mediaPlayer?.let { player ->
            try {
                // Check if player is in a valid state
                (player.isPlaying ?: false) || player.currentPosition >= 0
            } catch (e: Exception) {
                Log.w("MusicPlayer", "Error checking player state", e)
                false
            }
        } ?: false
    }
    
    fun setVolume(leftVolume: Float, rightVolume: Float) {
        mediaPlayer?.setVolume(leftVolume, rightVolume)
    }
    
    @Synchronized
    fun release() {
        try {
            Log.d("MusicPlayer", "Releasing MediaPlayer")
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
            mediaPlayer = null
            isPlaying = false
            isPreparing = false
            currentUri = null
            Log.d("MusicPlayer", "MediaPlayer released and set to null")
        } catch (e: Exception) {
            Log.e("MusicPlayer", "Error releasing MediaPlayer", e)
        }
    }
    
    fun playSong(
        songId: String,
        title: String,
        uri: Uri,
        duration: String,
        onPreparedCallback: (() -> Unit)? = null
    ) {
        loadSong(uri) {
            play()
            onPreparedCallback?.invoke()
        }
    }

    fun pausePlayback() {
        pause()
    }

    fun seekToPosition(position: Int) {
        seekTo(position)
    }

    fun stopPlayback() {
        stop()
    }
} 