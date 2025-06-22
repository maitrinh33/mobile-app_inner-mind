package com.miu.meditationapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.miu.meditationapp.databinding.ActivityMeditationBinding
import com.miu.meditationapp.ui.main.HomeViewModel
import java.util.*
import java.util.concurrent.TimeUnit

class MeditationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMeditationBinding
    private lateinit var mediaPlayer: MediaPlayer
    private var isFullscreen: Boolean = false
    private lateinit var tts: TextToSpeech
    private lateinit var values: Array<String>
    private var minutes = 20L
    private var startMin = 20L
    private lateinit var textIndicator: TextView
    private lateinit var timer: CountDownTimer
    private var isVoiceEnabled: Boolean = true
    private lateinit var viewModel: HomeViewModel
    lateinit var currentUser: FirebaseUser
    private var isRunning: Boolean = false
    private var isManuallyFinishing: Boolean = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMeditationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        isFullscreen = true

        // Firebase user & ViewModel
        currentUser = FirebaseAuth.getInstance().currentUser!!
        val uid = FirebaseAuth.getInstance().uid
        viewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        viewModel.init(applicationContext, uid!!)

        // TextToSpeech khởi tạo một lần
        tts = TextToSpeech(applicationContext) {
            if (it == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                tts.setSpeechRate(0.8F)
            }
        }

        textIndicator = binding.indicator

        // Spinner chọn phút
        values = resources.getStringArray(R.array.minutes)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, values)
        binding.spinner.adapter = adapter
        binding.spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                minutes = values[position].toLongOrNull() ?: 20L
                startMin = minutes
                textIndicator.text = "$minutes:00"
                if (::timer.isInitialized) {
                    timer.cancel()
                }
                timer = createTimer()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        mediaPlayer = MediaPlayer.create(applicationContext, R.raw.back_sound)
        timer = createTimer()

        // Back callback (thay cho onBackPressed)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showDialog(this@MeditationActivity)
            }
        })

        binding.close.setOnClickListener {
            showDialog(this)
        }

        binding.speech.setOnClickListener {
            isVoiceEnabled = !isVoiceEnabled
            binding.speech.setImageResource(if (isVoiceEnabled) R.drawable.mic else R.drawable.mic_no)
        }

        binding.start.setOnClickListener {
            timer.start()
            binding.spinner.isEnabled = false
            binding.spinner.isClickable = false
            mediaPlayer.isLooping = true
            mediaPlayer.start()
            binding.start.isClickable = false
            binding.start.text = "Started"
            isRunning = true
        }

        binding.sound.setOnClickListener {
            if (::mediaPlayer.isInitialized) {
                if (mediaPlayer.isPlaying) {
                    binding.sound.setImageResource(R.drawable.sound_no)
                    mediaPlayer.pause()
                } else {
                    binding.sound.setImageResource(R.drawable.sound)
                    mediaPlayer.start()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isManuallyFinishing) {
            cleanupResources()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupResources()
    }

    private fun cleanupResources() {
        if (::mediaPlayer.isInitialized) {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
            } catch (_: Exception) {}
        }

        if (::timer.isInitialized) {
            timer.cancel()
        }

        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }

        if (isRunning && !isManuallyFinishing) {
            viewModel.updateMeditationMinutes((startMin - minutes).toInt())
            viewModel.updateMeditationCount()
        }
    }

    private fun createTimer(): CountDownTimer {
        return object : CountDownTimer(minutes * 60000, 1000) {
            override fun onTick(ms: Long) {
                if (isManuallyFinishing) {
                    cancel()
                    return
                }
                val mins = TimeUnit.MILLISECONDS.toMinutes(ms)
                val secs = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(mins)
                minutes = mins

                if (((startMin == 10L && mins == 9L) || mins == 19L) && secs == 55L) {
                    speak("Sit down in a relaxing position and close your eyes")
                }

                if (mins == 2L && secs == 10L) {
                    speak("Please keep your eyes closed and stop thinking mantra, take two more minutes")
                }

                if (mins == 0L && secs == 10L) {
                    speak("Now you can open your eyes.")
                }

                textIndicator.text = "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
            }

            override fun onFinish() {
                if (!isManuallyFinishing) {
                    speak("Thank you for doing meditation with me. I hope you're feeling great right now.")
                    viewModel.updateMeditationMinutes(startMin.toInt())
                    viewModel.updateMeditationCount()
                }
            }
        }
    }

    private fun speak(text: String) {
        if (isVoiceEnabled && ::tts.isInitialized && !isManuallyFinishing) {
            tts.speak(text, TextToSpeech.QUEUE_ADD, null)
        }
    }

    private fun showDialog(context: Context) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Are you sure")
        builder.setMessage("Do you want to stop the meditation session?")
        builder.setPositiveButton("Yes") { _, _ ->
            isManuallyFinishing = true
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.create().show()
    }
}
