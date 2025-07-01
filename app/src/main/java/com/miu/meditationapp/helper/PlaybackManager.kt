package com.miu.meditationapp.helper

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LifecycleCoroutineScope
import com.miu.meditationapp.databases.SongEntity
import com.miu.meditationapp.viewmodels.MusicViewModel
import kotlinx.coroutines.launch
import com.miu.meditationapp.helper.FilePickerHelper

class PlaybackManager(
    private val context: Context,
    private val viewModel: MusicViewModel,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val recyclerViewManager: RecyclerViewManager,
    private val uploadManager: UploadManager,
    private val adminSongCacheHelper: AdminSongCacheHelper
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
                var playSong = song
                if (song.isAdminSong) {
                    recyclerViewManager.setOnlyLoadingState(song.id)
                    if (!adminSongCacheHelper.isSongCached(song)) {
                        if (song.uri.startsWith("content://")) {
                            val file = FilePickerHelper.copyContentUriToCache(context, Uri.parse(song.uri), song.title)
                            playSong = song.copy(uri = file.toURI().toString())
                        } else {
                            adminSongCacheHelper.cacheSong(song)
                        }
                    }
                }
                viewModel.playSong(playSong)
            } catch (e: Exception) {
                if (song.isAdminSong) {
                    recyclerViewManager.clearLoadingStates()
                }
                DialogHelper.showError(context, "Error playing song: ${e.message}")
            }
        }
    }
} 