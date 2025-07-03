package com.miu.meditationapp.services.music

class MusicPlayerCallbackHandler(
    private val musicPlayer: MusicPlayer,
    private val onPrepared: () -> Unit,
    private val onCompletion: () -> Unit,
    private val onError: (Int, Int) -> Boolean,
    private val onProgressUpdate: (Int, Int) -> Unit
) {
    fun setup() {
        musicPlayer.onPrepared = onPrepared
        musicPlayer.onCompletion = onCompletion
        musicPlayer.onError = onError
        musicPlayer.onProgressUpdate = onProgressUpdate
    }
} 