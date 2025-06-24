package com.miu.meditationapp.services.music

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.miu.meditationapp.services.MusicServiceRefactored

/**
 * Dedicated broadcast receiver for music control actions
 */
class MusicBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        try {
            val serviceIntent = Intent(context, MusicServiceRefactored::class.java).apply {
                action = intent.action
            }
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Log.e("MusicBroadcastReceiver", "Error handling broadcast", e)
        }
    }
} 