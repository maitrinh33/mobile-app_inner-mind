package com.miu.meditationapp.viewmodels

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ChildEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

class HomeViewModel: ViewModel() {
    private val TAG = "HomeViewModel"
    var times: Int = 0;
    private var sharedPreferences: SharedPreferences? = null
    val MED_COUNT = "meditation_count"
    val MED_MINUTES = "meditation_minutes"
    val BREATHE_COUNT = "breathe_count"
    val BREATHE_MINUTES = "breathe_minutes"
    var test = MutableLiveData<String>("")

    // Store Firebase listeners
    private val valueListeners = mutableListOf<ValueEventListener>()
    private val childListeners = mutableListOf<ChildEventListener>()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var isCleaningUp = false

    fun init(context: Context, prefName: String) {
        Log.d(TAG, "Initializing ViewModel")
        try {
            sharedPreferences = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            Log.d(TAG, "ViewModel initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ViewModel", e)
        }
    }

    fun cleanup() {
        if (isCleaningUp) {
            Log.d(TAG, "Cleanup already in progress, skipping")
            return
        }
        
        isCleaningUp = true
        Log.d(TAG, "Starting cleanup")
        try {
            // Cancel coroutines
            coroutineScope.cancel()
            Log.d(TAG, "Coroutines cancelled")

            // Remove all Firebase listeners in a non-blocking way
            val database = FirebaseDatabase.getInstance().reference
            valueListeners.forEach { listener ->
                try {
                    database.removeEventListener(listener)
                    Log.d(TAG, "ValueEventListener removed")
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing ValueEventListener", e)
                }
            }
            childListeners.forEach { listener ->
                try {
                    database.removeEventListener(listener)
                    Log.d(TAG, "ChildEventListener removed")
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing ChildEventListener", e)
                }
            }
            valueListeners.clear()
            childListeners.clear()
            Log.d(TAG, "All listeners cleared")
            
            // Reset LiveData to empty string
            test.postValue("")
            Log.d(TAG, "LiveData reset")

            // Clear shared preferences reference
            sharedPreferences = null
            Log.d(TAG, "SharedPreferences reference cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error in cleanup", e)
        } finally {
            isCleaningUp = false
        }
        Log.d(TAG, "Cleanup completed")
    }

    override fun onCleared() {
        Log.d(TAG, "onCleared called")
        super.onCleared()
        cleanup()
    }

    // Helper methods to add listeners
    fun addValueListener(listener: ValueEventListener) {
        Log.d(TAG, "Adding ValueEventListener")
        valueListeners.add(listener)
    }

    fun addChildListener(listener: ChildEventListener) {
        Log.d(TAG, "Adding ChildEventListener")
        childListeners.add(listener)
    }

    fun getMeditationCount(): Int {
        return sharedPreferences?.getInt(MED_COUNT, 0) ?: 0
    }

    fun updateMeditationCount() {
        sharedPreferences?.edit()?.putInt(MED_COUNT, getMeditationCount() + 1)?.apply()
    }

    fun getMeditationMin(): Int {
        return sharedPreferences?.getInt(MED_MINUTES, 0) ?: 0
    }

    fun updateMeditationMinutes(minutes: Int) {
        sharedPreferences?.edit()?.putInt(MED_MINUTES, getMeditationMin() + minutes)?.apply()
    }

    fun getBreatheCount(): Int {
        return sharedPreferences?.getInt(BREATHE_COUNT, 0) ?: 0
    }

    fun updateBreatheCount() {
        sharedPreferences?.edit()?.putInt(BREATHE_COUNT, getBreatheCount() + 1)?.apply()
    }

    fun getBreatheMin(): Int {
        return sharedPreferences?.getInt(BREATHE_MINUTES, 0) ?: 0
    }

    fun updateBreatheMin(minutes: Int) {
        sharedPreferences?.edit()?.putInt(BREATHE_MINUTES, getBreatheMin() + minutes)?.apply()
    }
} 