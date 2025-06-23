package com.miu.meditationapp.activities

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.miu.meditationapp.R
import com.miu.meditationapp.databinding.ActivityBreathBinding
import com.miu.meditationapp.viewmodels.HomeViewModel
import java.util.*
import java.util.concurrent.TimeUnit

class BreathActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBreathBinding
    private lateinit var textIndicator: TextView
    private lateinit var timer: CountDownTimer
    private lateinit var viewModel: HomeViewModel
    private lateinit var currentUser: FirebaseUser
    private lateinit var tts: TextToSpeech

    private var isRunning = false
    private var minutes = 3L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBreathBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUser = FirebaseAuth.getInstance().currentUser!!
        val uid = currentUser.uid
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        viewModel.init(applicationContext, uid)

        textIndicator = binding.indicator
        timer = createTimer()

        // Tăng âm lượng hệ thống (media) lên tối đa
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            0
        )

        // Khởi tạo TextToSpeech
        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                tts.setSpeechRate(0.8F)
            }
        }

        binding.start.setOnClickListener {
            toggle()
        }

        binding.close.setOnClickListener {
            showDialog(this)
        }
    }

    private fun toggle() {
        if (isRunning) {
            stopExercise()
            binding.start.text = "Start"
        } else {
            binding.breathe.playAnimation()
            binding.start.text = getString(R.string.str_end)

            // Delay khởi động đếm giờ để tránh trắng màn hình
            binding.root.postDelayed({
                timer.start()
            }, 500)
        }
    }

    private fun createTimer(): CountDownTimer {
        return object : CountDownTimer(3 * 60000, 1000) {
            var sec = 0L

            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun onTick(ms: Long) {
                isRunning = true
                minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
                sec = TimeUnit.MILLISECONDS.toSeconds(ms) % 60

                textIndicator.text = "${minutes.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"

                if (minutes == 2L && sec == 57L) {
                    tts.speak(
                        "Inhale through your nose and exhale through your mouth",
                        TextToSpeech.QUEUE_ADD,
                        null,
                        null
                    )
                }
            }

            override fun onFinish() {
                stopExercise()
            }
        }
    }

    private fun stopExercise() {
        binding.breathe.pauseAnimation()
        isRunning = false
        timer.cancel()
    }

    private fun showDialog(context: Context) {
        val builder = AlertDialog.Builder(context)

        builder.setMessage("Do you want to stop breathing exercise ?").setCancelable(true)

        builder.setPositiveButton("Yes") { _, _ ->
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        val alert = builder.create()
        alert.setTitle("Are you sure")
        alert.show()
    }

    override fun onStop() {
        super.onStop()
        viewModel.updateBreatheMin((3L - minutes).toInt())
        viewModel.updateBreatheCount()
        timer.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
    }
} 