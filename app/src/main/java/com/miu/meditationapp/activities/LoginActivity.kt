package com.miu.meditationapp.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayoutMediator
import com.miu.meditationapp.adapters.AuthViewPagerAdapter
import com.miu.meditationapp.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = FirebaseAuth.getInstance()

        // Setup ViewPager and TabLayout
        val adapter = AuthViewPagerAdapter(supportFragmentManager, lifecycle)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Login"
                1 -> "Sign Up"
                else -> null
            }
        }.attach()

        // Handle navigation from logout
        val selectedTab = intent?.getIntExtra("SELECT_TAB", 0) ?: 0
        binding.viewPager.setCurrentItem(selectedTab, false)
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Handle navigation from logout if activity is already running
        val selectedTab = intent?.getIntExtra("SELECT_TAB", 0) ?: 0
        binding.viewPager.setCurrentItem(selectedTab, false)
    }
} 