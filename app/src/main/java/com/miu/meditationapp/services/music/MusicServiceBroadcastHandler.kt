package com.miu.meditationapp.services.music

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

class MusicServiceBroadcastHandler(
    private val context: Context,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onStop: () -> Unit,
    private val actions: List<String>
) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                actions.getOrNull(0) -> onPlay()
                actions.getOrNull(1) -> onPause()
                actions.getOrNull(2) -> onStop()
            }
        }
    }

    fun register() {
        val intentFilter = IntentFilter().apply {
            actions.forEach { addAction(it) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, intentFilter)
        }
    }

    fun unregister() {
        context.unregisterReceiver(receiver)
    }
} 