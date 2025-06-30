package com.miu.meditationapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MeditationApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.google.firebase.database.FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }
} 