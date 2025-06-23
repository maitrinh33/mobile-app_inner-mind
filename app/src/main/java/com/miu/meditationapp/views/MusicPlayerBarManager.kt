package com.miu.meditationapp.views

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import com.miu.meditationapp.databinding.MusicPlayerBarBinding
import com.miu.meditationapp.services.MusicServiceRefactored
import com.miu.meditationapp.services.music.MusicBroadcastManager

class MusicPlayerBarManager(
    private val context: Context,
    private val binding: MusicPlayerBarBinding
) : MusicBroadcastManager.MusicBroadcastListener {
    
    private var currentSongId: String = ""
    private var currentSongTitle: String = ""
    private var currentSongDuration: String = ""
    private var currentSongUri: Uri? = null
    private var isFavorite: Boolean = false
    private var onFavoriteChanged: ((Boolean) -> Unit)? = null
    private var isPlaying: Boolean = false
    
    init {
        setupClickListeners()
        MusicBroadcastManager.addListener(this)
    }
    
    private fun setupClickListeners() {
        binding.buttonPlayPause.setOnClickListener {
            togglePlayPause()
        }
        
        binding.buttonFavorite.setOnClickListener {
            toggleFavorite()
        }
        
        // Make the song info area clickable to expand to full player
        binding.textTitle.setOnClickListener {
            expandToFullPlayer()
        }
        
        binding.textCurrentTime.setOnClickListener {
            expandToFullPlayer()
        }
    }
    
    override fun onSongStateChanged(songId: String, songTitle: String, songDuration: String, isPlaying: Boolean) {
        try {
            if (songId.isNotEmpty()) {
                updateSongInfo(songId, songTitle, songDuration)
                this.isPlaying = isPlaying
                updatePlayPauseButton(isPlaying)
                showMusicPlayerBar()
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerBarManager", "Error in onSongStateChanged", e)
        }
    }
    
    override fun onProgressUpdate(songId: String, currentPosition: Int, duration: Int, isPlaying: Boolean) {
        try {
            if (songId == currentSongId) {
                this.isPlaying = isPlaying
                updateProgress(currentPosition, duration)
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerBarManager", "Error in onProgressUpdate", e)
        }
    }
    
    private fun updateSongInfo(songId: String, title: String, duration: String) {
        currentSongId = songId
        currentSongTitle = title
        currentSongDuration = duration
        try {
            binding.textTitle.text = title
            binding.textCurrentTime.text = "0:00 / $duration"
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerBarManager", "Error updating song info", e)
        }
    }
    
    private fun updatePlayPauseButton(isPlaying: Boolean) {
        try {
            binding.buttonPlayPause.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerBarManager", "Error updating play/pause button", e)
        }
    }
    
    private fun updateProgress(currentPosition: Int, duration: Int) {
        try {
            val currentTime = formatTime(currentPosition)
            val totalTime = formatTime(duration)
            binding.textCurrentTime.text = "$currentTime / $totalTime"
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerBarManager", "Error updating progress", e)
        }
    }
    
    private fun formatTime(milliseconds: Int): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
    
    private fun togglePlayPause() {
        val intent = Intent(context, MusicServiceRefactored::class.java).apply {
            if (currentSongId.isNotEmpty()) {
                action = if (isCurrentlyPlaying()) MusicServiceRefactored.ACTION_PAUSE else MusicServiceRefactored.ACTION_PLAY
            }
        }
        context.startService(intent)
    }
    
    private fun isCurrentlyPlaying(): Boolean {
        return isPlaying
    }
    
    private fun toggleFavorite() {
        isFavorite = !isFavorite
        updateFavoriteButton()
        onFavoriteChanged?.invoke(isFavorite)
    }
    
    private fun updateFavoriteButton() {
        binding.buttonFavorite.setImageResource(
            if (isFavorite) android.R.drawable.btn_star_big_on
            else android.R.drawable.btn_star_big_off
        )
    }
    
    private fun expandToFullPlayer() {
        // This could open a full-screen music player or expand the current bar
        // For now, we'll just show a toast
        android.widget.Toast.makeText(context, "Full player coming soon!", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    fun showMusicPlayerBar() {
        try {
            binding.musicPlayerBar.visibility = View.VISIBLE
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerBarManager", "Error showing music player bar", e)
        }
    }
    
    fun hideMusicPlayerBar() {
        try {
            binding.musicPlayerBar.visibility = View.GONE
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerBarManager", "Error hiding music player bar", e)
        }
    }
    
    fun setFavoriteCallback(callback: (Boolean) -> Unit) {
        onFavoriteChanged = callback
    }
    
    fun cleanup() {
        try {
            MusicBroadcastManager.removeListener(this)
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerBarManager", "Error cleaning up", e)
        }
    }
} 