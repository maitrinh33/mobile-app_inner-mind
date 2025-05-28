package com.miu.meditationapp.ui.main

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.auth.FirebaseUser
import com.miu.meditationapp.R
import com.miu.meditationapp.helper.NotificationReceiver
import com.miu.meditationapp.databinding.FragmentHomeBinding
import java.util.*

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var currentUser: FirebaseUser
    private lateinit var alarmManager: AlarmManager
    private lateinit var calendar: Calendar
    private lateinit var picker: MaterialTimePicker
    private lateinit var database: DatabaseReference
    private lateinit var viewModel: HomeViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        calendar = Calendar.getInstance()
        
        // Initialize Firebase Auth
        val auth = FirebaseAuth.getInstance()
        currentUser = auth.currentUser ?: run {
            Toast.makeText(context, "Please sign in to continue", Toast.LENGTH_SHORT).show()
            return binding.root
        }

        // Create notification channel
        createNotificationChannel()

        // Initialize ViewModel
        viewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        viewModel.init(requireContext(), currentUser.uid)

        updateReportTexts()

        // Initialize Firebase Database
        database = FirebaseDatabase.getInstance().getReference("users")
        fetchUserData()

        // Initialize Alarm Manager
        alarmManager = requireContext().getSystemService(ALARM_SERVICE) as AlarmManager

        setupClickListeners()
        updateReportProgress()

        return binding.root
    }

    private fun fetchUserData() {
        database.child(currentUser.uid).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val firstName = snapshot.child("firstname").value.toString()
                    binding.textView3.text = firstName
                } else {
                    binding.textView3.text = "User"
                }
            }
            .addOnFailureListener { e ->
                binding.textView3.text = "User"
                Toast.makeText(context, "Unable to load user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupClickListeners() {
        binding.button.setOnClickListener {
            startActivity(Intent(context, MeditationActivity::class.java))
        }

        binding.breathe.setOnClickListener {
            startActivity(Intent(context, BreathActivity::class.java))
        }

        binding.remind.setOnClickListener {
            showTimePicker()
        }
    }

    private fun showTimePicker() {
        picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(12)
            .setMinute(0)
            .setTitleText("Select Reminder time")
            .build()

        activity?.let { picker.show(it.supportFragmentManager, "TIME_PICKER") }

        picker.addOnPositiveButtonClickListener {
            cancelAlarm()
            calendar.set(Calendar.HOUR_OF_DAY, picker.hour)
            calendar.set(Calendar.MINUTE, picker.minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            setAlarm(calendar.timeInMillis)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val name = "Meditation Reminder"
                val description = "Channel for meditation reminders"
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel("MORNING", name, importance).apply {
                    this.description = description
                    enableVibration(true)
                }
                
                val notificationManager = requireContext().getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to create notification channel: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setAlarm(timeInMillis: Long) {
        try {
            val intent = Intent(context, NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                200,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
            
            val timeString = String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
            Toast.makeText(context, "Reminder set for $timeString daily", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to set reminder: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelAlarm() {
        try {
            val intent = Intent(context, NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                200,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to cancel existing reminder", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateReportTexts() {
        binding.id.valMeditateTimes.text = viewModel.getMeditationCount().toString()
        binding.id.valMeditate.text = viewModel.getMeditationMin().toString()
        binding.id.valBreatheTimes.text = viewModel.getBreatheCount().toString()
        binding.id.valBreathe.text = viewModel.getBreatheMin().toString()
    }

    private fun updateReportProgress() {
        val medMin = viewModel.getMeditationMin()
        val breMin = viewModel.getBreatheMin()
        val medCount = viewModel.getMeditationCount()
        val breCount = viewModel.getBreatheCount()
        var percentage = 1
        if(medCount > 0 && breCount > 0) {
            percentage = (medMin + breMin) * 100 / (medCount * 20) + (breCount * 3)
        }
        binding.id.statusBar.progress = percentage
        binding.id.valMeditateTimes.text = medCount.toString()
        binding.id.valMeditate.text = medMin.toString()
        binding.id.valBreatheTimes.text = breCount.toString()
        binding.id.valBreathe.text = breMin.toString()
    }

    override fun onResume() {
        updateReportTexts()
        updateReportProgress()
        super.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
