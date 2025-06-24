package com.miu.meditationapp.services.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.miu.meditationapp.R
import com.miu.meditationapp.activities.MainActivity

/**
 * Handles music playback notification creation and management
 */
class MusicNotificationManager(private val context: Context) {
    
    private var notificationManager: NotificationManager? = null
    private var mediaSession: MediaSessionCompat? = null
    private var currentNotification: Notification? = null
    
    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "music_playback_channel"
    }
    
    init {
        setupNotificationManager()
        createNotificationChannel()
        setupMediaSession()
    }
    
    private fun setupNotificationManager() {
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(context, "MusicService").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }
    }
    
    fun createNotification(
        title: String,
        isPlaying: Boolean,
        onPlayPauseClick: () -> Unit,
        onStopClick: () -> Unit
    ): Notification {
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        
        // Create pending intents for actions
        val playPauseIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent("com.miu.meditationapp.PLAY_PAUSE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent("com.miu.meditationapp.STOP"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create content intent
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.nav_music)
            .setContentTitle(title)
            .setContentText("Now Playing")
            .setOngoing(isPlaying)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPauseIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(0, 1))
            .build()
        
        currentNotification = notification
        return notification
    }
    
    fun updateNotification(title: String, isPlaying: Boolean) {
        val notification = createNotification(
            title = title,
            isPlaying = isPlaying,
            onPlayPauseClick = {},
            onStopClick = {}
        )
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }
    
    fun updatePlaybackState(state: Int) {
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build()
        )
    }
    
    fun cancelNotification() {
        notificationManager?.cancel(NOTIFICATION_ID)
        currentNotification = null
    }
    
    fun release() {
        mediaSession?.release()
        mediaSession = null
        cancelNotification()
    }
} 