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
import com.google.firebase.auth.FirebaseAuth

class RecyclerViewManager(
    private val context: Context,
    private val binding: FragmentMusicBinding,
    private val viewModel: MusicViewModel
) {
    private lateinit var userSongAdapter: ModernSongAdapter
    private lateinit var adminSongAdapter: AdminSongAdapter
    private val auth = FirebaseAuth.getInstance()

    fun setupRecyclerViews(
        onSongClick: (SongEntity) -> Unit,
        onMoreOptionsClick: (SongEntity, android.view.View) -> Unit,
        onSwipeToDelete: (SongEntity) -> Unit,
        onSwipeToEdit: (SongEntity) -> Unit
    ) {
        val currentUserId = auth.currentUser?.uid ?: ""
        userSongAdapter = ModernSongAdapter(currentUserId, onSongClick, onMoreOptionsClick)
        adminSongAdapter = AdminSongAdapter(currentUserId, onSongClick, onMoreOptionsClick)

        binding.recyclerViewUserSongs.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = userSongAdapter
            setupSwipeHandler(onSwipeToDelete, onSwipeToEdit)
        }

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

    fun stopLoading() {
        adminSongAdapter.stopLoading()
    }

    fun setCurrentPlayingAdminSong(songId: Int?) {
        adminSongAdapter.setCurrentPlayingSongId(songId)
    }
} 