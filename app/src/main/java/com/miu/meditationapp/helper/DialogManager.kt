package com.miu.meditationapp.helper

import android.content.Context
import android.view.View
import androidx.lifecycle.LifecycleCoroutineScope
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
    private val rootView: View
) {
    /**
     * Shows options menu for user songs and coordinates actions with business logic
     */
    fun showUserSongOptionsMenu(song: SongEntity, anchor: View) {
        MenuHelper.showUserSongOptionsMenu(
            context = context,
            song = song,
            anchor = anchor,
            onEditClick = { showEditSongDialog(song) },
            onDeleteClick = { showDeleteConfirmationDialog(song) },
            onFavoriteClick = { viewModel.toggleFavorite(song.id, !song.isFavorite) }
        )
    }

    /**
     * Shows options menu for admin songs and coordinates actions with business logic
     */
    fun showAdminSongOptionsMenu(song: SongEntity, anchor: View) {
        lifecycleScope.launch {
            MenuHelper.showAdminSongOptionsMenu(
                context = context,
                song = song,
                anchor = anchor,
                isAdmin = viewModel.isAdmin.value,
                onEditClick = { showEditSongDialog(song) },
                onDeleteClick = { showDeleteConfirmationDialog(song) },
                onFavoriteClick = { viewModel.toggleFavorite(song.id.toInt(), !song.isFavorite) }
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

    /**
     * Shows error message using DialogHelper
     */
    fun showError(message: String) {
        DialogHelper.showError(context, message)
    }
} 