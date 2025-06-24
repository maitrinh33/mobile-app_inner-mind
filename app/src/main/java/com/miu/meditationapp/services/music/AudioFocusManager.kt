package com.miu.meditationapp.services.music

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Handles audio focus management
 */
class AudioFocusManager(
    private val context: Context,
    private val onFocusChange: (Int) -> Unit
) {
    
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    
    companion object {
        const val AUDIOFOCUS_GAIN = AudioManager.AUDIOFOCUS_GAIN
        const val AUDIOFOCUS_LOSS = AudioManager.AUDIOFOCUS_LOSS
        const val AUDIOFOCUS_LOSS_TRANSIENT = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        const val AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
    }
    
    init {
        setupAudioManager()
    }
    
    private fun setupAudioManager() {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d("AudioFocusManager", "Audio focus change: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d("AudioFocusManager", "Audio focus lost permanently")
                hasAudioFocus = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d("AudioFocusManager", "Audio focus lost temporarily")
                hasAudioFocus = false
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d("AudioFocusManager", "Audio focus gained")
                hasAudioFocus = true
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d("AudioFocusManager", "Audio focus lost temporarily, can duck")
            }
        }
        onFocusChange(focusChange)
    }
    
    fun requestAudioFocus(): Boolean {
        Log.d("AudioFocusManager", "Requesting audio focus")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).apply {
                setAudioAttributes(AudioAttributes.Builder().apply {
                    setUsage(AudioAttributes.USAGE_MEDIA)
                    setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                }.build())
                setOnAudioFocusChangeListener(audioFocusChangeListener)
            }.build()
            
            val result = audioManager?.requestAudioFocus(audioFocusRequest!!)
            Log.d("AudioFocusManager", "Audio focus result: $result")
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            hasAudioFocus
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager?.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            Log.d("AudioFocusManager", "Audio focus result (legacy): $result")
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            hasAudioFocus
        }
    }
    
    fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
        Log.d("AudioFocusManager", "Audio focus abandoned")
    }
    
    fun hasAudioFocus(): Boolean = hasAudioFocus
    
    fun getCurrentVolume(): Int {
        return audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
    }
    
    fun getMaxVolume(): Int {
        return audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
    }
    
    fun setVolume(volume: Int) {
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        Log.d("AudioFocusManager", "Volume set to: $volume")
    }
    
    fun isMuted(): Boolean {
        return audioManager?.isStreamMute(AudioManager.STREAM_MUSIC) ?: false
    }
} 