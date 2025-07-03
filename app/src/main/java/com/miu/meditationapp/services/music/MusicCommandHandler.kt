package com.miu.meditationapp.services.music

import android.content.Intent
import android.net.Uri
import com.miu.meditationapp.services.MusicServiceRefactored

class MusicCommandHandler(
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onStop: () -> Unit,
    private val onPlaySong: (songId: String, title: String, uri: Uri, duration: String) -> Unit,
    private val onSeek: (position: Int) -> Unit,
    private val onGetState: (shouldRestore: Boolean) -> Unit
) {
    fun handle(intent: Intent?): Boolean {
        when (intent?.action) {
            MusicServiceRefactored.ACTION_PLAY -> onPlay()
            MusicServiceRefactored.ACTION_PAUSE -> onPause()
            MusicServiceRefactored.ACTION_STOP -> onStop()
            MusicServiceRefactored.ACTION_PLAY_SONG -> {
                val songId = intent.getStringExtra("songId") ?: return false
                val title = intent.getStringExtra("title") ?: return false
                val uriString = intent.getStringExtra("uri") ?: return false
                val duration = intent.getStringExtra("duration") ?: return false
                onPlaySong(songId, title, Uri.parse(uriString), duration)
            }
            MusicServiceRefactored.ACTION_SEEK -> {
                val position = intent.getIntExtra("seekPosition", 0)
                onSeek(position)
            }
            MusicServiceRefactored.ACTION_GET_STATE -> {
                val shouldRestore = intent.getBooleanExtra("restore", false)
                onGetState(shouldRestore)
            }
            else -> return false
        }
        return true
    }
} 