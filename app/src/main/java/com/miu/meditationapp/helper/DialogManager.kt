package com.miu.meditationapp.helper

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.firebase.auth.FirebaseAuth
import com.miu.meditationapp.databases.SongEntity
import com.miu.meditationapp.viewmodels.MusicViewModel
import kotlinx.coroutines.launch

/**
 * DialogManager coordinates between UI dialogs/menus and business logic.
 * It uses DialogHelper for basic dialog UI operations while adding business logic coordination.
 */
class DialogManager(
    private val context: Context,
    private val viewModel: MusicViewModel,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val rootView: View,
    private val auth: FirebaseAuth
) {
    /**
     * Shows options menu for user songs and coordinates actions with business logic
     */
    fun showSongOptionsMenu(song: SongEntity, anchor: View) {
        val currentUserId = auth.currentUser?.uid ?: ""
        lifecycleScope.launch {
            MenuHelper.showSongOptionsMenu(
                context = context,
                song = song,
                anchor = anchor,
                currentUserId = currentUserId,
                isAdmin = viewModel.isAdmin.value,
                onEditClick = { showEditSongDialog(song) },
                onDeleteClick = { showDeleteConfirmationDialog(song) },
                onShareClick = { shareSong(song) },
                onFavoriteClick = { viewModel.toggleFavorite(song.id, !song.isFavorite) }
            )
        }
    }

    /**
     * Shows edit dialog and handles title update in ViewModel
     */
    fun showEditSongDialog(song: SongEntity) {
        DialogHelper.showEditSongDialog(
            context = context,
            song = song,
            onSave = { newTitle -> viewModel.updateSongTitle(song.id, newTitle) },
            rootView = rootView
        )
    }

    /**
     * Shows delete confirmation dialog and handles deletion in ViewModel
     */
    fun showDeleteConfirmationDialog(song: SongEntity) {
        DialogHelper.showDeleteConfirmationDialog(
            context = context,
            song = song,
            onDelete = { viewModel.deleteSong(song) },
            rootView = rootView
        )
    }

    private fun shareSong(song: SongEntity) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Check out this song: ${song.title} by ${song.artist}")
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Song"))
    }

    /**
     * Shows error message using DialogHelper
     */
    fun showError(message: String) {
        DialogHelper.showError(context, message)
    }
} 