package com.miu.meditationapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.miu.meditationapp.databinding.ActivityMainBinding
import com.miu.meditationapp.ui.main.AboutFragment
import com.miu.meditationapp.ui.main.HomeFragment
import com.miu.meditationapp.ui.main.ForumFragment
import com.miu.meditationapp.ui.main.LearnFragment
import com.miu.meditationapp.ui.main.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import android.app.ActivityManager
import android.content.Context
import android.os.Process
import java.lang.Runtime

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: HomeViewModel
    private var isFinishing = false
    private var isCleaningUp = false
    private val handler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val TAG = "MainActivity"
    private var viewPagerAdapter: ViewPagerAdapter? = null
    private var lastMemoryLogTime = 0L
    private val MEMORY_LOG_INTERVAL = 30000L // 30 seconds

    private fun logMemoryUsage() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastMemoryLogTime < MEMORY_LOG_INTERVAL) {
            return
        }
        lastMemoryLogTime = currentTime

        try {
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            val maxMemory = runtime.maxMemory() / 1024 / 1024
            val freeMemory = runtime.freeMemory() / 1024 / 1024

            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val availableMemory = memoryInfo.availMem / 1024 / 1024
            val totalMemory = memoryInfo.totalMem / 1024 / 1024
            val lowMemory = memoryInfo.lowMemory

            Log.d(TAG, """
                Memory Usage:
                - App Used Memory: $usedMemory MB
                - App Free Memory: $freeMemory MB
                - App Max Memory: $maxMemory MB
                - System Available Memory: $availableMemory MB
                - System Total Memory: $totalMemory MB
                - Low Memory Warning: $lowMemory
                - Process ID: ${Process.myPid()}
            """.trimIndent())
        } catch (e: Exception) {
            Log.e(TAG, "Error logging memory usage", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called with savedInstanceState: ${savedInstanceState != null}")
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // Initialize ViewModel
            viewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
            val uid = FirebaseAuth.getInstance().uid
            if (uid != null) {
                viewModel.init(applicationContext, uid)
                Log.d(TAG, "ViewModel initialized with UID: $uid")
            } else {
                Log.w(TAG, "Firebase UID is null")
            }

            setupViewPager()
            logMemoryUsage()
            Log.d(TAG, "onCreate completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            throw e // Re-throw to ensure proper error reporting
        }
    }

    private fun setupViewPager() {
        Log.d(TAG, "Setting up ViewPager")
        try {
            viewPagerAdapter = ViewPagerAdapter(supportFragmentManager)
            viewPagerAdapter?.let { adapter ->
                adapter.addFragment(HomeFragment(), "Home")
                adapter.addFragment(LearnFragment(), "Learn")
                adapter.addFragment(ForumFragment(), "Forum")
                adapter.addFragment(AboutFragment(), "About")

                binding.viewPager.adapter = adapter
                binding.tabs.setupWithViewPager(binding.viewPager)

                binding.tabs.getTabAt(0)?.setIcon(R.drawable.home)
                binding.tabs.getTabAt(1)?.setIcon(R.drawable.learn)
                binding.tabs.getTabAt(2)?.setIcon(R.drawable.forum)
                binding.tabs.getTabAt(3)?.setIcon(R.drawable.about)
            }
            Log.d(TAG, "ViewPager setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupViewPager", e)
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart called")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        logMemoryUsage()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called")
        logMemoryUsage()
        if (!isFinishing) {
            if (!isCleaningUp) {
                cleanupResources()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop called")
        logMemoryUsage()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
        logMemoryUsage()
        if (!isCleaningUp) {
            cleanupResources()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val levelString = when (level) {
            TRIM_MEMORY_RUNNING_CRITICAL -> "TRIM_MEMORY_RUNNING_CRITICAL"
            TRIM_MEMORY_RUNNING_LOW -> "TRIM_MEMORY_RUNNING_LOW"
            TRIM_MEMORY_RUNNING_MODERATE -> "TRIM_MEMORY_RUNNING_MODERATE"
            TRIM_MEMORY_UI_HIDDEN -> "TRIM_MEMORY_UI_HIDDEN"
            TRIM_MEMORY_BACKGROUND -> "TRIM_MEMORY_BACKGROUND"
            TRIM_MEMORY_MODERATE -> "TRIM_MEMORY_MODERATE"
            TRIM_MEMORY_COMPLETE -> "TRIM_MEMORY_COMPLETE"
            else -> "UNKNOWN($level)"
        }
        Log.d(TAG, "onTrimMemory called with level: $levelString")
        logMemoryUsage()

        when (level) {
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_RUNNING_LOW,
            TRIM_MEMORY_RUNNING_MODERATE -> {
                if (!isCleaningUp) {
                    cleanupResources()
                }
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.d(TAG, "onLowMemory called")
        logMemoryUsage()
        if (!isCleaningUp) {
            cleanupResources()
        }
    }

    private fun cleanupResources() {
        if (isCleaningUp) {
            Log.d(TAG, "Cleanup already in progress, skipping")
            return
        }

        isCleaningUp = true
        Log.d(TAG, "Starting cleanupResources")
        try {
            // Cancel any pending operations
            handler.removeCallbacksAndMessages(null)
            Log.d(TAG, "Handler callbacks removed")

            // Cancel coroutines and wait for completion
            coroutineScope.cancel()
            Log.d(TAG, "Coroutines cancelled")

            // Clear ViewPager and adapter
            binding.viewPager.adapter = null
            viewPagerAdapter = null
            Log.d(TAG, "ViewPager adapter cleared")

            // Clear any Firebase listeners or operations
            try {
                // Get the database reference and disconnect
                val databaseRef = FirebaseDatabase.getInstance().reference
                databaseRef.keepSynced(false)
                Log.d(TAG, "Firebase database sync disabled")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing Firebase database listeners", e)
            }

            // Clear auth state listeners
            try {
                FirebaseAuth.getInstance().removeAuthStateListener { }
                Log.d(TAG, "Firebase auth listeners cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing Firebase auth listeners", e)
            }

            viewModel.cleanup()
            Log.d(TAG, "ViewModel cleanup completed")

            // Clear any cached data
            binding.tabs.removeAllTabs()
            Log.d(TAG, "Tabs cleared")

            // Clear any bitmap caches or large objects
            System.gc()
            Log.d(TAG, "Garbage collection requested")

            // Log final memory state
            logMemoryUsage()
        } catch (e: Exception) {
            Log.e(TAG, "Error in cleanupResources", e)
        } finally {
            isCleaningUp = false
        }
        Log.d(TAG, "cleanupResources completed")
    }

    override fun onBackPressed() {
        Log.d(TAG, "onBackPressed called")
        isFinishing = true
        cleanupResources()
        super.onBackPressed()
    }

    override fun finish() {
        Log.d(TAG, "finish called")
        isFinishing = true
        cleanupResources()
        super.finish()
    }
}