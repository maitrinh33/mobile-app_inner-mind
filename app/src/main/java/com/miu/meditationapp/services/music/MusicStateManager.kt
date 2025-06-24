package com.miu.meditationapp.services.music

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles music playback state persistence and restoration
 */
class MusicStateManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        const val PREFS_NAME = "music_service_state"
        const val KEY_CURRENT_SONG_ID = "current_song_id"
        const val KEY_CURRENT_POSITION = "current_position"
        const val KEY_IS_PLAYING = "is_playing"
        const val KEY_CURRENT_TITLE = "current_title"
        const val KEY_CURRENT_URI = "current_uri"
        const val KEY_CURRENT_DURATION = "current_duration"
        
        // Memory cache for frequently accessed values
        @Volatile private var cachedState: MusicState? = null
    }
    
    data class MusicState(
        val songId: String,
        val title: String,
        val uri: String,
        val duration: String,
        val position: Int,
        val isPlaying: Boolean
    )
    
    suspend fun saveState(
        songId: String,
        title: String,
        uri: Uri,
        duration: String,
        position: Int,
        isPlaying: Boolean
    ) {
        // Update memory cache first
        val newState = MusicState(songId, title, uri.toString(), duration, position, isPlaying)
        cachedState = newState
        
        // Then save to disk asynchronously
        withContext(Dispatchers.IO) {
            try {
                prefs.edit().apply {
                    putString(KEY_CURRENT_SONG_ID, songId)
                    putString(KEY_CURRENT_TITLE, title)
                    putString(KEY_CURRENT_URI, uri.toString())
                    putString(KEY_CURRENT_DURATION, duration)
                    putInt(KEY_CURRENT_POSITION, position)
                    putBoolean(KEY_IS_PLAYING, isPlaying)
                }.commit() // Use commit() on background thread instead of apply()
            } catch (e: Exception) {
                Log.e("MusicStateManager", "Error saving state", e)
            }
        }
    }
    
    fun getState(): MusicState? {
        // Return cached state if available
        cachedState?.let { return it }
        
        // Otherwise read from SharedPreferences
        return try {
            val songId = prefs.getString(KEY_CURRENT_SONG_ID, null) ?: return null
            val title = prefs.getString(KEY_CURRENT_TITLE, "") ?: ""
            val uri = prefs.getString(KEY_CURRENT_URI, "") ?: ""
            val duration = prefs.getString(KEY_CURRENT_DURATION, "") ?: ""
            val position = prefs.getInt(KEY_CURRENT_POSITION, 0)
            val isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false)
            
            MusicState(songId, title, uri, duration, position, isPlaying).also {
                cachedState = it
            }
        } catch (e: Exception) {
            Log.e("MusicStateManager", "Error reading state", e)
            null
        }
    }
    
    fun clearState() {
        cachedState = null
        prefs.edit().clear().apply()
    }
} 