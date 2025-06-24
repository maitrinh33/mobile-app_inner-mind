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
class MusicBroadcastManager private constructor(private val context: Context) {
    
    companion object {
        const val BROADCAST_SONG_STATE_CHANGED = "com.miu.meditationapp.SONG_STATE_CHANGED"
        const val BROADCAST_PROGRESS_UPDATE = "com.miu.meditationapp.PROGRESS_UPDATE"
        
        @Volatile
        private var instance: MusicBroadcastManager? = null
        
        fun getInstance(context: Context): MusicBroadcastManager {
            return instance ?: synchronized(this) {
                instance ?: MusicBroadcastManager(context.applicationContext).also { instance = it }
            }
        }
        
        fun addListener(listener: MusicBroadcastListener) {
            getInstance(listener.getContext()).addListenerInternal(listener)
        }
        
        fun removeListener(listener: MusicBroadcastListener) {
            getInstance(listener.getContext()).removeListenerInternal(listener)
        }
        
        fun init(context: Context) {
            getInstance(context.applicationContext)
        }
    }
    
    private val listeners = mutableListOf<WeakReference<MusicBroadcastListener>>()
    private var isRegistered = false
    private val localBroadcastManager = LocalBroadcastManager.getInstance(context)
    
    // Initialize the broadcast receiver immediately
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
                                val songId = intent.getStringExtra("songId") ?: ""
                                val title = intent.getStringExtra("title") ?: ""
                                val duration = intent.getStringExtra("duration") ?: ""
                                val isPlaying = intent.getBooleanExtra("isPlaying", false)
                                listener.onSongStateChanged(songId, title, duration, isPlaying)
                            }
                            BROADCAST_PROGRESS_UPDATE -> {
                                val songId = intent.getStringExtra("songId") ?: ""
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
    
    init {
        registerBroadcastReceiver()
    }
    
    private fun addListenerInternal(listener: MusicBroadcastListener) {
        synchronized(listeners) {
            // Remove any null references first
            listeners.removeAll { it.get() == null }
            
            // Check if listener already exists
            if (listeners.none { it.get() == listener }) {
                // Add new listener
                listeners.add(WeakReference(listener))
            }
        }
    }
    
    private fun removeListenerInternal(listener: MusicBroadcastListener) {
        synchronized(listeners) {
            listeners.removeAll { it.get() == listener || it.get() == null }
        }
    }
    
    private fun registerBroadcastReceiver() {
        if (isRegistered) return
        
        try {
            val filter = IntentFilter().apply {
                addAction(BROADCAST_SONG_STATE_CHANGED)
                addAction(BROADCAST_PROGRESS_UPDATE)
            }
            localBroadcastManager.registerReceiver(broadcastReceiver, filter)
            isRegistered = true
        } catch (e: Exception) {
            Log.e("MusicBroadcastManager", "Error registering broadcast receiver", e)
        }
    }
    
    fun broadcastSongState(
        songId: String,
        title: String,
        duration: String,
        isPlaying: Boolean
    ) {
        try {
            val intent = Intent(BROADCAST_SONG_STATE_CHANGED).apply {
                putExtra("songId", songId)
                putExtra("title", title)
                putExtra("duration", duration)
                putExtra("isPlaying", isPlaying)
            }
            localBroadcastManager.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e("MusicBroadcastManager", "Error sending state broadcast", e)
        }
    }
    
    fun broadcastProgress(
        songId: String,
        currentPosition: Int,
        duration: Int,
        isPlaying: Boolean
    ) {
        try {
            val intent = Intent(BROADCAST_PROGRESS_UPDATE).apply {
                putExtra("songId", songId)
                putExtra("currentPosition", currentPosition)
                putExtra("duration", duration)
                putExtra("isPlaying", isPlaying)
            }
            localBroadcastManager.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e("MusicBroadcastManager", "Error sending progress broadcast", e)
        }
    }
    
    fun cleanup() {
        try {
            if (isRegistered) {
                localBroadcastManager.unregisterReceiver(broadcastReceiver)
                isRegistered = false
            }
            synchronized(listeners) {
                listeners.clear()
            }
        } catch (e: Exception) {
            Log.e("MusicBroadcastManager", "Error cleaning up", e)
        }
    }
    
    interface MusicBroadcastListener {
        fun onSongStateChanged(songId: String, songTitle: String, songDuration: String, isPlaying: Boolean)
        fun onProgressUpdate(songId: String, currentPosition: Int, duration: Int, isPlaying: Boolean)
        fun getContext(): Context
    }
} 