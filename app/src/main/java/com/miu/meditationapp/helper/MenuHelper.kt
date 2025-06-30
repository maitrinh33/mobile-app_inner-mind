package com.miu.meditationapp.helper

import android.content.Context
import android.view.View
import android.widget.PopupMenu
import com.miu.meditationapp.R
import com.miu.meditationapp.databases.SongEntity

object MenuHelper {
    fun showSongOptionsMenu(
        context: Context,
        song: SongEntity,
        anchor: View,
        currentUserId: String,
        isAdmin: Boolean,
        onEditClick: () -> Unit,
        onDeleteClick: () -> Unit,
        onShareClick: () -> Unit,
        onFavoriteClick: () -> Unit
    ) {
        val popup = PopupMenu(context, anchor)
        
        // Determine which menu to show
        val isOwner = song.userId == currentUserId
        val menuRes = if (isOwner || (song.isAdminSong && isAdmin)) {
            R.menu.song_menu_owner
        } else {
            R.menu.song_menu_viewer
        }
        popup.inflate(menuRes)

        // Set favorite text dynamically
        val favoriteItem = popup.menu.findItem(R.id.menu_add_favourite)
        favoriteItem?.title = if (song.isFavorite) "Remove from Favorites" else "Add to Favorites"
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_edit -> onEditClick()
                R.id.menu_delete -> onDeleteClick()
                R.id.menu_share -> onShareClick()
                R.id.menu_add_favourite -> onFavoriteClick()
            }
            true
        }
        popup.show()
    }
} 