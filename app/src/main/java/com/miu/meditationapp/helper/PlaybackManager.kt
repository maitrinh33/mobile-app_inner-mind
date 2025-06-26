package com.miu.meditationapp.helper

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LifecycleCoroutineScope
import com.miu.meditationapp.databases.SongEntity
import com.miu.meditationapp.viewmodels.MusicViewModel
import kotlinx.coroutines.launch

class PlaybackManager(
    private val context: Context,
    private val viewModel: MusicViewModel,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val recyclerViewManager: RecyclerViewManager,
    private val uploadManager: UploadManager
) {
    suspend fun handleSelectedAudioFile(uri: Uri, isAdmin: Boolean, onError: (String) -> Unit) {
        try {
            if (isAdmin) {
                uploadManager.handleAdminUpload(uri, onError)
            } else {
                val songEntity = uploadManager.handleUserUpload(uri)
                viewModel.addSong(songEntity)
            }
        } catch (e: Exception) {
            onError("Error adding song: ${e.message}")
        }
    }

    fun handleSongClick(song: SongEntity) {
        lifecycleScope.launch {
            try {
                if (song.isAdminSong) {
                    recyclerViewManager.setOnlyLoadingState(song.id)
                }
                viewModel.playSong(song)
            } catch (e: Exception) {
                if (song.isAdminSong) {
                    recyclerViewManager.clearLoadingStates()
                }
                DialogHelper.showError(context, "Error playing song: ${e.message}")
            }
        }
    }
} 