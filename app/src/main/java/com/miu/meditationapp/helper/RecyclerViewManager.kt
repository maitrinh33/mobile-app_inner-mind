package com.miu.meditationapp.helper

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.miu.meditationapp.adapters.AdminSongAdapter
import com.miu.meditationapp.adapters.ModernSongAdapter
import com.miu.meditationapp.databases.SongEntity
import com.miu.meditationapp.databinding.FragmentMusicBinding
import com.miu.meditationapp.viewmodels.MusicViewModel

class RecyclerViewManager(
    private val context: Context,
    private val binding: FragmentMusicBinding,
    private val viewModel: MusicViewModel
) {
    private lateinit var userSongAdapter: ModernSongAdapter
    private lateinit var adminSongAdapter: AdminSongAdapter

    fun setupRecyclerViews(
        onSongClick: (SongEntity) -> Unit,
        onUserOptionsClick: (SongEntity, android.view.View) -> Unit,
        onAdminOptionsClick: (SongEntity, android.view.View) -> Unit,
        onSwipeToDelete: (SongEntity) -> Unit,
        onSwipeToEdit: (SongEntity) -> Unit
    ) {
        setupUserSongsRecyclerView(onSongClick, onUserOptionsClick, onSwipeToDelete, onSwipeToEdit)
        setupAdminSongsRecyclerView(onSongClick, onAdminOptionsClick)
    }

    private fun setupUserSongsRecyclerView(
        onSongClick: (SongEntity) -> Unit,
        onOptionsClick: (SongEntity, android.view.View) -> Unit,
        onSwipeToDelete: (SongEntity) -> Unit,
        onSwipeToEdit: (SongEntity) -> Unit
    ) {
        userSongAdapter = ModernSongAdapter(
            onSongClick = onSongClick,
            onMoreOptionsClick = onOptionsClick
        )

        binding.recyclerViewUserSongs.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = userSongAdapter
            setupSwipeHandler(onSwipeToDelete, onSwipeToEdit)
        }
    }

    private fun setupAdminSongsRecyclerView(
        onSongClick: (SongEntity) -> Unit,
        onOptionsClick: (SongEntity, android.view.View) -> Unit
    ) {
        adminSongAdapter = AdminSongAdapter(
            onSongClick = onSongClick,
            onMoreOptionsClick = onOptionsClick
        )

        binding.recyclerViewAdminSongs.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = adminSongAdapter
        }
    }

    private fun RecyclerView.setupSwipeHandler(
        onSwipeToDelete: (SongEntity) -> Unit,
        onSwipeToEdit: (SongEntity) -> Unit
    ) {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val song = userSongAdapter.songs[position]
                when (direction) {
                    ItemTouchHelper.LEFT -> onSwipeToDelete(song)
                    ItemTouchHelper.RIGHT -> onSwipeToEdit(song)
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(this)
    }

    fun updateAdapters(userSongs: List<SongEntity>, adminSongs: List<SongEntity>) {
        userSongAdapter.updateSongs(userSongs)
        adminSongAdapter.updateSongs(adminSongs)
    }

    fun clearLoadingStates() {
        adminSongAdapter.clearLoadingStates()
    }

    fun setOnlyLoadingState(songId: Int) {
        adminSongAdapter.setOnlyLoading(songId)
    }
} 