package com.miu.meditationapp.services.music

import android.util.Log
import com.miu.meditationapp.services.MusicServiceRefactored

class MusicServiceEventHandler(
    private val service: MusicServiceRefactored,
    private val musicPlayer: MusicPlayer?,
    private val musicNotificationManager: MusicNotificationManager?,
    private val musicBroadcastManager: MusicBroadcastManager?,
    private val audioFocusManager: AudioFocusManager?,
    private val musicStateManager: MusicStateManager?,
    private val playbackStatsManager: PlaybackStatsManager?
) {
    fun handlePrepared() {
        // Start playback immediately after preparation
        musicPlayer?.play()
    }

    fun handleCompletion() {
        service.stopPlayback()
    }

    fun handleError(what: Int, extra: Int) {
        Log.e("MusicService", "MediaPlayer error: what=$what, extra=$extra")
        service.stopPlayback()
    }

    fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioFocusManager.AUDIOFOCUS_LOSS,
            AudioFocusManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                service.pause()
            }
            AudioFocusManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                musicPlayer?.setVolume(0.3f, 0.3f)
            }
            AudioFocusManager.AUDIOFOCUS_GAIN -> {
                musicPlayer?.setVolume(1.0f, 1.0f)
                service.play()
            }
        }
    }

    fun handleProgressUpdate(position: Int, duration: Int) {
        musicBroadcastManager?.broadcastProgress(
            songId = service.sessionPublic.currentSongId,
            currentPosition = position,
            duration = duration,
            isPlaying = musicPlayer?.isPlaying() ?: false
        )
    }

    fun incrementPlayCount() {
        playbackStatsManager?.incrementPlayCount(service.sessionPublic.currentSongId)
    }

    fun sendCurrentState() {
        musicBroadcastManager?.broadcastSongState(
            songId = service.sessionPublic.currentSongId,
            title = service.sessionPublic.currentTitle,
            duration = service.sessionPublic.currentDuration,
            isPlaying = service.sessionPublic.isPlaying
        )
        if (service.sessionPublic.isPlaying) {
            val position = musicPlayer?.getCurrentPosition() ?: 0
            val duration = musicPlayer?.getDuration() ?: 0
            musicBroadcastManager?.broadcastProgress(
                songId = service.sessionPublic.currentSongId,
                currentPosition = position,
                duration = duration,
                isPlaying = service.sessionPublic.isPlaying
            )
        } else {
            musicBroadcastManager?.broadcastProgress(
                songId = service.sessionPublic.currentSongId,
                currentPosition = 0,
                duration = 0,
                isPlaying = false
            )
        }
    }
} 