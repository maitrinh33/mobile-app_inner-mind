package com.miu.meditationapp.services.music

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.miu.meditationapp.databases.MusicDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class PlaybackStatsManager(private val context: Context, private val scope: CoroutineScope) {
    fun incrementPlayCount(songId: String?) {
        val id = songId?.toIntOrNull() ?: return
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val db = MusicDatabase.getInstance(context)
                db.songDao().incrementPlayCount(id, userId, Date())
            } catch (e: Exception) {
                // Log error if needed
            }
        }
    }
} 