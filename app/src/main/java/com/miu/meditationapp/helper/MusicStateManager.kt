package com.miu.meditationapp.helper

import android.view.View
import com.miu.meditationapp.databinding.FragmentMusicBinding
import com.miu.meditationapp.databases.SongEntity

class MusicStateManager(private val binding: FragmentMusicBinding) {
    fun updateSongListsVisibility(songs: List<SongEntity>) {
        val hasUserSongs = songs.any { !it.isAdminSong }
        val hasAdminSongs = songs.any { it.isAdminSong }
        
        binding.apply {
            userSongsSection.visibility = if (hasUserSongs) View.VISIBLE else View.GONE
            adminSongsSection.visibility = if (hasAdminSongs) View.VISIBLE else View.GONE
            emptyStateLayout.root.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    fun updateAdminButtonVisibility(isAdmin: Boolean) {
        binding.fabAdminUpload.visibility = if (isAdmin) View.VISIBLE else View.GONE
    }

    fun updateSongCount(count: Int) {
        binding.textSongCount.text = "$count songs"
    }

    fun splitSongsByType(songs: List<SongEntity>): Pair<List<SongEntity>, List<SongEntity>> {
        val userSongs = songs.filter { !it.isAdminSong }
        val adminSongs = songs.filter { it.isAdminSong }
        return Pair(userSongs, adminSongs)
    }
} 