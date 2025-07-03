package com.miu.meditationapp.services.music

import android.os.Handler
import android.os.Looper

class MusicProgressUpdater(
    private val isPlayingProvider: () -> Boolean,
    private val getPosition: () -> Int,
    private val getDuration: () -> Int,
    private val onProgress: (position: Int, duration: Int, isPlaying: Boolean) -> Unit,
    private val onSaveState: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() {
            try {
                if (isPlayingProvider()) {
                    val position = getPosition()
                    val duration = getDuration()
                    onProgress(position, duration, true)
                    if (position % 15000 < 1000) {
                        onSaveState()
                    }
                    handler.postDelayed(this, 1000)
                } else {
                    handler.removeCallbacks(this)
                }
            } catch (e: Exception) {
                handler.removeCallbacks(this)
            }
        }
    }
    fun start() {
        handler.post(runnable)
    }
    fun stop() {
        handler.removeCallbacks(runnable)
    }
} 