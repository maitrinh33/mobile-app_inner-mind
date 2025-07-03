package com.miu.meditationapp.views

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import com.miu.meditationapp.databinding.MusicPlayerBarBinding
import com.miu.meditationapp.services.MusicServiceRefactored
import com.miu.meditationapp.services.music.MusicBroadcastManager
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat

class MusicPlayerBarManager(
    private val context: Context,
    private val binding: MusicPlayerBarBinding
) : MusicBroadcastManager.MusicBroadcastListener {
    
    private var currentSongId: String = ""
    private var currentSongTitle: String = ""
    private var currentSongDurationMs: Int = 0
    private var currentSongUri: Uri? = null
    private var isFavorite: Boolean = false
    private var onFavoriteChanged: ((Boolean) -> Unit)? = null
    private var isPlaying: Boolean = false
    private var isDragging: Boolean = false
    
    init {
        setupClickListeners()
        setupSlider()
        MusicBroadcastManager.addListener(this)
    }
    
    private fun setupClickListeners() {
        binding.buttonPlayPause.setOnClickListener {
            togglePlayPause()
        }
        
        binding.textTitle.setOnClickListener {
            expandToFullPlayer()
        }
    }

    private fun setupSlider() {
        binding.songProgressBar.apply {
            addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    isDragging = true
                    val newPosition = (currentSongDurationMs * (value / 100.0)).roundToInt()
                    updateTimeDisplay(newPosition, currentSongDurationMs)
                }
            }

            addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {
                    isDragging = true
                }

                override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                    isDragging = false
                    val newPosition = (currentSongDurationMs * (slider.value / 100.0)).roundToInt()
                    android.util.Log.d("MusicPlayerBarManager", "SeekBar stop: duration=$currentSongDurationMs, value=${slider.value}, newPosition=$newPosition")
                    seekToPosition(newPosition)
                }
            })
        }
    }
    
    override fun onSongStateChanged(songId: String, songTitle: String, songDuration: String, isPlaying: Boolean) {
        try {
            // Always update song info, even if songId is the same
            updateSongInfo(songId, songTitle, songDuration)
            this.isPlaying = isPlaying
            updatePlayPauseButton(isPlaying)
            showMusicPlayerBar()
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerBarManager", "Error in onSongStateChanged", e)
        }
    }
    
    override fun onProgressUpdate(songId: String, currentPosition: Int, duration: Int, isPlaying: Boolean) {
        try {
            if (songId == currentSongId && !isDragging) {
                this.isPlaying = isPlaying
                updatePlayPauseButton(isPlaying)
                if (duration > 0) {
                    currentSongDurationMs = duration
                }
                updateProgress(currentPosition, currentSongDurationMs)
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerBarManager", "Error in onProgressUpdate", e)
        }
    }
    
    private fun updateSongInfo(songId: String, title: String, duration: String) {
        currentSongId = songId
        currentSongTitle = title
        val parsedDuration = duration.toIntOrNull() ?: 0
        if (parsedDuration > 0) {
            currentSongDurationMs = parsedDuration
        }
        try {
            binding.textTitle.text = title
            binding.textEndTime.text = formatTime(currentSongDurationMs)
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

    private fun updateTimeDisplay(currentPosition: Int, duration: Int) {
        binding.textStartTime.text = formatTime(currentPosition)
        binding.textEndTime.text = formatTime(duration)
    }
    
    private fun updateProgress(currentPosition: Int, duration: Int) {
        try {
            updateTimeDisplay(currentPosition, duration)
            
            if (duration > 0) {
                val progress = (currentPosition.toDouble() / duration.toDouble() * 100).coerceIn(0.0, 100.0)
                binding.songProgressBar.value = progress.toFloat()
            }
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
        ContextCompat.startForegroundService(context, intent)
    }
    
    private fun seekToPosition(position: Int) {
        val intent = Intent(context, MusicServiceRefactored::class.java).apply {
            action = MusicServiceRefactored.ACTION_SEEK
            putExtra("seekPosition", position)
        }
        ContextCompat.startForegroundService(context, intent)
    }
    
    private fun isCurrentlyPlaying(): Boolean {
        return isPlaying
    }

    private fun expandToFullPlayer() {
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

    override fun getContext(): Context = context
} 