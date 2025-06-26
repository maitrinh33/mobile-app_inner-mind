package com.miu.meditationapp.helper

import android.content.Context
import android.view.View
import android.widget.PopupMenu
import com.miu.meditationapp.databases.SongEntity

object MenuHelper {
    fun showUserSongOptionsMenu(
        context: Context,
        song: SongEntity,
        anchor: View,
        onEditClick: () -> Unit,
        onDeleteClick: () -> Unit,
        onFavoriteClick: () -> Unit
    ) {
        val popup = PopupMenu(context, anchor)
        popup.menu.add(0, 0, 0, "Edit")
        popup.menu.add(0, 1, 1, "Delete")
        popup.menu.add(0, 2, 2, if (song.isFavorite) "Remove from Favorites" else "Add to Favorites")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> onEditClick()
                1 -> onDeleteClick()
                2 -> onFavoriteClick()
            }
            true
        }
        popup.show()
    }

    fun showAdminSongOptionsMenu(
        context: Context,
        song: SongEntity,
        anchor: View,
        isAdmin: Boolean,
        onEditClick: () -> Unit,
        onDeleteClick: () -> Unit,
        onFavoriteClick: () -> Unit
    ) {
        val popup = PopupMenu(context, anchor)
        if (isAdmin) {
            popup.menu.add(0, 0, 0, "Edit")
            popup.menu.add(0, 1, 1, "Delete")
        }
        popup.menu.add(0, 2, 2, if (song.isFavorite) "Remove from Favorites" else "Add to Favorites")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> if (isAdmin) onEditClick()
                1 -> if (isAdmin) onDeleteClick()
                2 -> onFavoriteClick()
            }
            true
        }
        popup.show()
    }
} 