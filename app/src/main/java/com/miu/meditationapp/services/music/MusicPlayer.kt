package com.miu.meditationapp.services.music

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File

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
    
    init {
        setupAudioManager()
    }
    
    private fun setupAudioManager() {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    
    @Synchronized
    fun loadSong(uri: Uri, onPreparedCallback: (() -> Unit)? = null) {
        try {
            // If same song is loading, wait
            if (isPreparing && uri == currentUri) {
                Log.d("MusicPlayer", "Same song is already preparing, waiting...")
                return
            }
            
            currentUri = uri
            isPreparing = true
            
            // Release any existing MediaPlayer
            release()
            
            // Create new MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                // Set audio attributes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(AudioAttributes.Builder().apply {
                        setUsage(AudioAttributes.USAGE_MEDIA)
                        setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    }.build())
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                }
                
                // For HTTP/HTTPS URLs, set proper headers and buffering
                val headers = if (uri.scheme?.startsWith("http") == true) {
                    HashMap<String, String>().apply {
                        put("User-Agent", "MeditationApp/1.0")
                    }
                } else null
                
                // Set data source based on URI type
                when {
                    uri.scheme?.startsWith("http") == true -> {
                        // Set larger buffer size for streaming
                        setOnBufferingUpdateListener { _, percent ->
                            Log.d("MusicPlayer", "Buffering: $percent%")
                        }
                        
                        // Set network timeout and data source
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            setDataSource(context, uri, headers ?: emptyMap())
                        } else {
                            setDataSource(uri.toString())
                        }
                    }
                    else -> setDataSource(context, uri)
                }
                
                setOnPreparedListener {
                    isPreparing = false
                    // Set initial volume to ensure audio is audible
                    setVolume(1.0f, 1.0f)
                    onPreparedCallback?.invoke()
                    onPrepared?.invoke()
                }
                
                setOnCompletionListener {
                    this@MusicPlayer.isPlaying = false
                    onCompletion?.invoke()
                }
                
                setOnErrorListener { mp, what, extra ->
                    Log.e("MusicPlayer", "MediaPlayer error: what=$what, extra=$extra, uri=$uri")
                    isPreparing = false
                    this@MusicPlayer.isPlaying = false
                    if (what == MediaPlayer.MEDIA_ERROR_UNSUPPORTED) {
                        Log.e("MusicPlayer", "Unsupported format or URL error")
                    }
                    onError?.invoke(what, extra) ?: true
                }
                
                // Prepare asynchronously
                prepareAsync()
            }
        } catch (e: Exception) {
            isPreparing = false
            Log.e("MusicPlayer", "Error loading song: $uri", e)
            throw e
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
    
    fun setDataSource(uri: Uri) {
        try {
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(context, uri)
            mediaPlayer?.prepare()
        } catch (e: Exception) {
            Log.e("MusicPlayer", "Error setting data source", e)
            throw e
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
        } catch (e: Exception) {
            Log.e("MusicPlayer", "Error releasing MediaPlayer", e)
        }
    }
} 