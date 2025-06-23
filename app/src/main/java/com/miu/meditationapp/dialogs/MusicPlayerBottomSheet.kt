package com.miu.meditationapp.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.miu.meditationapp.R
import com.miu.meditationapp.databinding.BottomSheetMusicPlayerBinding
import com.miu.meditationapp.services.MusicServiceRefactored
import com.miu.meditationapp.services.music.MusicBroadcastManager
import android.util.Log

class MusicPlayerBottomSheet : BottomSheetDialogFragment(), MusicBroadcastManager.MusicBroadcastListener {
    private var _binding: BottomSheetMusicPlayerBinding? = null
    private val binding get() = _binding!!
    
    var songTitle: String = ""
    var songDuration: String = ""
    var songUri: Uri? = null
    var songId: String = ""
    private var isFavorite: Boolean = false
    var onFavoriteChanged: ((Boolean) -> Unit)? = null
    private var isPlaying: Boolean = false
    
    internal val handler = Handler(Looper.getMainLooper())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make the bottom sheet non-dismissible and compact
        isCancelable = false
        setStyle(STYLE_NORMAL, R.style.CompactBottomSheetDialog)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetMusicPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        updateUI()
        setupClickListeners()
        MusicBroadcastManager.addListener(this)
    }
    
    override fun onSongStateChanged(songId: String, songTitle: String, songDuration: String, isPlaying: Boolean) {
        try {
            if (songId == this.songId) {
                this.isPlaying = isPlaying
                updatePlayPauseButton(isPlaying)
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerBottomSheet", "Error in onSongStateChanged", e)
        }
    }
    
    override fun onProgressUpdate(songId: String, currentPosition: Int, duration: Int, isPlaying: Boolean) {
        try {
            if (songId == this.songId) {
                this.isPlaying = isPlaying
                updateProgress(currentPosition, duration)
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerBottomSheet", "Error in onProgressUpdate", e)
        }
    }
    
    override fun onStart() {
        super.onStart()
        // Ensure the dialog cannot be dismissed by touching outside
        dialog?.setCanceledOnTouchOutside(false)
        
        // Set the bottom sheet to be compact and positioned above navigation
        dialog?.window?.let { window ->
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            
            // Position above navigation menu
            val layoutParams = window.attributes
            layoutParams.gravity = Gravity.BOTTOM
            layoutParams.y = 80 // Add some margin above navigation
            window.attributes = layoutParams
        }
    }
    
    private fun updateUI() {
        binding.textTitle.text = songTitle
        updateTimeDisplay()
        updatePlayPauseButton(isPlaying)
        updateFavoriteButton()
    }
    
    private fun updateTimeDisplay() {
        binding.textCurrentTime.text = "0:00 / $songDuration"
    }
    
    private fun updatePlayPauseButton() {
        updatePlayPauseButton(isPlaying)
    }
    
    private fun updateFavoriteButton() {
        binding.buttonFavorite.setImageResource(
            if (isFavorite) android.R.drawable.btn_star_big_on
            else android.R.drawable.btn_star_big_off
        )
    }
    
    private fun setupClickListeners() {
        binding.buttonPlayPause.setOnClickListener {
            Log.d("MusicPlayerBottomSheet", "buttonPlayPause clicked")
            togglePlayPause()
        }
        
        binding.buttonFavorite.setOnClickListener {
            toggleFavorite()
        }
    }
    
    private fun togglePlayPause() {
        Log.d("MusicPlayerBottomSheet", "togglePlayPause called: songId=$songId, isPlaying=$isPlaying")
        if (songId.isNotEmpty()) {
            val intent = Intent(requireContext(), MusicServiceRefactored::class.java).apply {
                action = if (isCurrentlyPlaying()) MusicServiceRefactored.ACTION_PAUSE else MusicServiceRefactored.ACTION_PLAY
            }
            Log.d("MusicPlayerBottomSheet", "Sending intent with action: ${intent.action}")
            requireContext().startService(intent)
        } else {
            Log.w("MusicPlayerBottomSheet", "togglePlayPause: songId is empty, cannot send intent")
        }
    }
    
    private fun isCurrentlyPlaying(): Boolean {
        return isPlaying
    }
    
    private fun toggleFavorite() {
        isFavorite = !isFavorite
        updateFavoriteButton()
        onFavoriteChanged?.invoke(isFavorite)
    }
    
    private fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.buttonPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }
    
    private fun updateProgress(currentPosition: Int, duration: Int) {
        val currentTime = formatTime(currentPosition)
        val totalTime = formatTime(duration)
        binding.textCurrentTime.text = "$currentTime / $totalTime"
    }
    
    private fun formatTime(milliseconds: Int): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
    
    fun requestSongSwitch() {
        if (songUri != null && isAdded && !isDetached && context != null) {
            val intent = Intent(requireContext(), MusicServiceRefactored::class.java).apply {
                action = MusicServiceRefactored.ACTION_PLAY_SONG
                putExtra("songId", songId)
                putExtra("title", songTitle)
                putExtra("uri", songUri.toString())
                putExtra("duration", songDuration)
            }
            requireContext().startService(intent)
        }
    }
    
    fun safeRequestSongSwitch() {
        try {
            if (isAdded && !isDetached && context != null) {
                requestSongSwitch()
            } else {
                // If not attached, schedule the request for when the fragment is ready
                handler.postDelayed({
                    if (isAdded && !isDetached && context != null) {
                        requestSongSwitch()
                    }
                }, 100)
            }
        } catch (e: Exception) {
            // Handle any exceptions gracefully
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // Unregister broadcast receiver
        MusicBroadcastManager.removeListener(this)
        
        _binding = null
    }
} 