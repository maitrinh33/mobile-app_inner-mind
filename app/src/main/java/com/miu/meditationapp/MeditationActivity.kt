package com.miu.meditationapp

import androidx.appcompat.app.AppCompatActivity
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
    private lateinit var values : Array<String>
    private var minutes = 20L
    private var startMin = 20L
    private lateinit var textIndicator:TextView
    private lateinit var timer: CountDownTimer
    private var isVoiceEnabled: Boolean = true
    private lateinit var viewModel: HomeViewModel
    lateinit var currentUser: FirebaseUser
    private var isRunning: Boolean = false
    private var isFinishing: Boolean = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMeditationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        isFullscreen = true

        currentUser = FirebaseAuth.getInstance().currentUser!!
        val uid = FirebaseAuth.getInstance().uid
        viewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        viewModel.init(applicationContext, uid!!)

        textIndicator = binding.indicator

        values = resources.getStringArray(R.array.minutes);
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, values)
        binding.spinner.adapter = adapter;
        binding.spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                minutes = if(position == 0) 20L else 10L
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

        binding.close.setOnClickListener {
            showDialog(this)
        }

        binding.speech.setOnClickListener {
            if(isVoiceEnabled) {
                isVoiceEnabled = false
                binding.speech.setImageResource(R.drawable.mic_no)
            } else {
                isVoiceEnabled = true
                binding.speech.setImageResource(R.drawable.mic)
            }
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
            if(mediaPlayer.isPlaying) {
                binding.sound.setImageResource(R.drawable.sound_no)
                mediaPlayer.pause()
            } else {
                binding.sound.setImageResource(R.drawable.sound)
                mediaPlayer.start()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isFinishing) {
            cleanupResources()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupResources()
    }

    private fun cleanupResources() {
        if (::mediaPlayer.isInitialized) {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.release()
        }
        
        if (::timer.isInitialized) {
            timer.cancel()
        }
        
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }

        if (isRunning && !isFinishing) {
            viewModel.updateMeditationMinutes((startMin - minutes).toInt())
            viewModel.updateMeditationCount()
        }
    }

    override fun onBackPressed() {
        showDialog(this)
    }

    private fun createTimer(): CountDownTimer {
        return object: CountDownTimer(minutes * 60000, 1000) {
            var sec = 0L
            override fun onTick(ms: Long) {
                if (isFinishing) {
                    cancel()
                    return
                }
                minutes = TimeUnit.MILLISECONDS.toMinutes(ms) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(ms))
                sec = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(ms))

                if(((startMin == 10L && minutes == 9L) || minutes == 19L) && sec == 55L) {
                    speak("Sit down and relaxing position and close your eyes")
                }

                if(minutes == 2L && sec == 10L) {
                    speak("Please keep your eyes closed and stop thinking mantra, take two more minutes")
                }

                if(minutes == 0L && sec == 10L) {
                    speak("No you can open your eyes.")
                }
                textIndicator.text = "${minutes.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
            }

            override fun onFinish() {
                if (!isFinishing) {
                    speak("Thank you for doing meditation with me. I hope your feeling great right now after doing this meditation")
                    viewModel.updateMeditationMinutes(20)
                    viewModel.updateMeditationCount()
                }
            }
        }
    }

    fun speak(text: String) {
        if (isFinishing) return
        tts = TextToSpeech(applicationContext, TextToSpeech.OnInitListener {
            if(isVoiceEnabled && it == TextToSpeech.SUCCESS && !isFinishing) {
                tts.language = Locale.US
                tts.setSpeechRate(0.8F)
                tts.speak(text, TextToSpeech.QUEUE_ADD, null)
            }
        })
    }

    private fun showDialog(context: Context){
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)

        builder.setMessage("Do you want to stop meditating ?").setCancelable(true)
        builder.setPositiveButton("Yes", DialogInterface.OnClickListener { dialog, which ->
            isFinishing = true
            cleanupResources()
            finish()
        })

        builder.setNegativeButton("Cancel", DialogInterface.OnClickListener { dialog, which -> dialog.cancel() })
        val alert = builder.create()
        alert.setTitle("Are you sure")
        alert.show()
    }
}