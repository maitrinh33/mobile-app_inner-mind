package com.miu.meditationapp.activities

import android.os.Bundle
import android.util.Log
import android.widget.MediaController
import androidx.appcompat.app.AppCompatActivity
import com.miu.meditationapp.databinding.ActivityVideoBinding
import com.miu.meditationapp.models.Videos

class VideosView : AppCompatActivity() {
    private var TAG = "VideoPlayer"
    private var mediaController: MediaController? = null
    private lateinit var videos: Videos
    private lateinit var binding: ActivityVideoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        videos = intent.getSerializableExtra("videos") as Videos

        binding.textVideoType.text = videos.title
        binding.textVideoTitle.text = videos.description

        configureVideoView()
    }

    private fun configureVideoView() {
        binding.videoView.setVideoPath(videos.url)
        mediaController = MediaController(this)

        mediaController?.setAnchorView(binding.videoView)
        binding.videoView.setMediaController(mediaController)

        binding.videoView.setOnPreparedListener { mp ->
            mp.isLooping = true
            Log.i(TAG, "Duration = " + binding.videoView.duration)
        }
        binding.videoView.start()
    }

    override fun onPause() {
        super.onPause()
        binding.videoView.stopPlayback()
    }
} 