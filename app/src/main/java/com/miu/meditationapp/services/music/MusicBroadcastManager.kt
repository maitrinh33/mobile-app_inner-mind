package com.miu.meditationapp.services.music

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.lang.ref.WeakReference

/**
 * Singleton manager for music-related broadcasts
 */
object MusicBroadcastManager {
    
    const val BROADCAST_SONG_STATE_CHANGED = "com.miu.meditationapp.SONG_STATE_CHANGED"
    const val BROADCAST_PROGRESS_UPDATE = "com.miu.meditationapp.PROGRESS_UPDATE"
    
    private val listeners = mutableListOf<WeakReference<MusicBroadcastListener>>()
    private var isRegistered = false
    private var context: Context? = null
    
    fun init(context: Context) {
        this.context = context.applicationContext
        registerBroadcastReceiver()
    }
    
    fun addListener(listener: MusicBroadcastListener) {
        // Remove any null references first
        listeners.removeAll { it.get() == null }
        
        // Add new listener
        listeners.add(WeakReference(listener))
    }
    
    fun removeListener(listener: MusicBroadcastListener) {
        listeners.removeAll { it.get() == listener || it.get() == null }
    }
    
    private fun registerBroadcastReceiver() {
        if (isRegistered || context == null) return
        
        try {
            LocalBroadcastManager.getInstance(context!!).registerReceiver(
                broadcastReceiver,
                IntentFilter().apply {
                    addAction(BROADCAST_SONG_STATE_CHANGED)
                    addAction(BROADCAST_PROGRESS_UPDATE)
                }
            )
            isRegistered = true
        } catch (e: Exception) {
            Log.e("MusicBroadcastManager", "Error registering broadcast receiver", e)
        }
    }
    
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                // Remove null references
                listeners.removeAll { it.get() == null }
                
                // Notify all listeners
                listeners.forEach { weakRef ->
                    weakRef.get()?.let { listener ->
                        when (intent?.action) {
                            BROADCAST_SONG_STATE_CHANGED -> {
                                val songId = intent.getStringExtra("currentSongId") ?: ""
                                val songTitle = intent.getStringExtra("currentSongTitle") ?: ""
                                val songDuration = intent.getStringExtra("currentSongDuration") ?: ""
                                val isPlaying = intent.getBooleanExtra("isPlaying", false)
                                listener.onSongStateChanged(songId, songTitle, songDuration, isPlaying)
                            }
                            BROADCAST_PROGRESS_UPDATE -> {
                                val songId = intent.getStringExtra("currentSongId") ?: ""
                                val currentPosition = intent.getIntExtra("currentPosition", 0)
                                val duration = intent.getIntExtra("duration", 0)
                                val isPlaying = intent.getBooleanExtra("isPlaying", false)
                                listener.onProgressUpdate(songId, currentPosition, duration, isPlaying)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicBroadcastManager", "Error in broadcast receiver", e)
            }
        }
    }
    
    fun sendStateBroadcast(
        isPlaying: Boolean,
        songId: String,
        songTitle: String,
        songDuration: String
    ) {
        try {
            val intent = Intent(BROADCAST_SONG_STATE_CHANGED).apply {
                putExtra("isPlaying", isPlaying)
                putExtra("currentSongId", songId)
                putExtra("currentSongTitle", songTitle)
                putExtra("currentSongDuration", songDuration)
            }
            context?.let { ctx ->
                LocalBroadcastManager.getInstance(ctx).sendBroadcast(intent)
            }
        } catch (e: Exception) {
            Log.e("MusicBroadcastManager", "Error sending state broadcast", e)
        }
    }
    
    fun sendProgressBroadcast(
        currentPosition: Int,
        duration: Int,
        isPlaying: Boolean,
        songId: String,
        songTitle: String
    ) {
        try {
            val intent = Intent(BROADCAST_PROGRESS_UPDATE).apply {
                putExtra("currentPosition", currentPosition)
                putExtra("duration", duration)
                putExtra("isPlaying", isPlaying)
                putExtra("currentSongId", songId)
                putExtra("currentSongTitle", songTitle)
            }
            context?.let { ctx ->
                LocalBroadcastManager.getInstance(ctx).sendBroadcast(intent)
            }
        } catch (e: Exception) {
            Log.e("MusicBroadcastManager", "Error sending progress broadcast", e)
        }
    }
    
    fun cleanup() {
        try {
            if (isRegistered && context != null) {
                LocalBroadcastManager.getInstance(context!!).unregisterReceiver(broadcastReceiver)
                isRegistered = false
            }
            listeners.clear()
            context = null
        } catch (e: Exception) {
            Log.e("MusicBroadcastManager", "Error cleaning up", e)
        }
    }
    
    interface MusicBroadcastListener {
        fun onSongStateChanged(songId: String, songTitle: String, songDuration: String, isPlaying: Boolean)
        fun onProgressUpdate(songId: String, currentPosition: Int, duration: Int, isPlaying: Boolean)
    }
} 