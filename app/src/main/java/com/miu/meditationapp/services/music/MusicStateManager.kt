package com.miu.meditationapp.services.music

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log

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
    }
    
    data class MusicState(
        val songId: String,
        val title: String,
        val uri: String,
        val duration: String,
        val position: Int,
        val isPlaying: Boolean
    )
    
    fun saveState(
        songId: String,
        title: String,
        uri: Uri,
        duration: String,
        position: Int,
        isPlaying: Boolean
    ) {
        try {
            prefs.edit().apply {
                putString(KEY_CURRENT_SONG_ID, songId)
                putString(KEY_CURRENT_TITLE, title)
                putString(KEY_CURRENT_URI, uri.toString())
                putString(KEY_CURRENT_DURATION, duration)
                putInt(KEY_CURRENT_POSITION, position)
                putBoolean(KEY_IS_PLAYING, isPlaying)
            }.apply()
        } catch (e: Exception) {
            Log.e("MusicStateManager", "Error saving state", e)
        }
    }
    
    fun restoreState(): MusicState? {
        return try {
            val songId = prefs.getString(KEY_CURRENT_SONG_ID, "") ?: ""
            if (songId.isNotEmpty()) {
                MusicState(
                    songId = songId,
                    title = prefs.getString(KEY_CURRENT_TITLE, "") ?: "",
                    uri = prefs.getString(KEY_CURRENT_URI, "") ?: "",
                    duration = prefs.getString(KEY_CURRENT_DURATION, "") ?: "",
                    position = prefs.getInt(KEY_CURRENT_POSITION, 0),
                    isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false)
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("MusicStateManager", "Error restoring state", e)
            null
        }
    }
    
    fun clearState() {
        prefs.edit().clear().apply()
    }
    
    fun hasValidState(): Boolean {
        val state = restoreState()
        return state != null
    }
} 