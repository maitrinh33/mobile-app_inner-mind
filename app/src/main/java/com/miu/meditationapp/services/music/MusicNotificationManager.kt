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
import android.widget.RemoteViews
import android.support.v4.media.MediaMetadataCompat

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
        currentPosition: Int,
        duration: Int,
        onPlayPauseClick: () -> Unit,
        onStopClick: () -> Unit
    ): Notification {
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
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
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val remoteViews = RemoteViews(context.packageName, R.layout.notification_player).apply {
            setTextViewText(R.id.notification_title, title)
            setProgressBar(R.id.notification_progress, if (duration > 0) duration else 1, currentPosition, false)
            setTextViewText(R.id.notification_time_elapsed, formatTime(currentPosition))
            setTextViewText(R.id.notification_time_total, formatTime(duration))
            setImageViewResource(R.id.notification_play_pause, playPauseIcon)
            setOnClickPendingIntent(R.id.notification_play_pause, playPauseIntent)
            setOnClickPendingIntent(R.id.notification_stop, stopIntent)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.nav_music)
            .setContentTitle(title)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setCustomContentView(remoteViews)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView())
            .build()
        currentNotification = notification
        return notification
    }
    
    fun updateNotification(title: String, isPlaying: Boolean, currentPosition: Int, duration: Int) {
        updateMediaSessionMetadata(title, duration)
        updateMediaSessionPlaybackState(isPlaying, currentPosition, duration)
        val notification = createNotification(
            title = title,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            onPlayPauseClick = {},
            onStopClick = {}
        )
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }
    
    private fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
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
    
    fun updateUI(
        title: String,
        isPlaying: Boolean,
        currentPosition: Int,
        duration: Int,
        startForeground: (Notification) -> Unit,
        broadcastSongState: () -> Unit
    ) {
        updateMediaSessionMetadata(title, duration)
        updateMediaSessionPlaybackState(isPlaying, currentPosition, duration)
        val notification = createNotification(
            title = title,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            onPlayPauseClick = {},
            onStopClick = {}
        )
        startForeground(notification)
        broadcastSongState()
    }
    
    private fun updateMediaSessionMetadata(title: String, duration: Int) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration.toLong())
            .build()
        mediaSession?.setMetadata(metadata)
    }
    
    private fun updateMediaSessionPlaybackState(isPlaying: Boolean, position: Int, duration: Int) {
        val actions = (
            PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_SEEK_TO
        )
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, position.toLong(), 1.0f)
            .setBufferedPosition(duration.toLong())
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }
    
    fun setMediaSessionCallback(callback: MediaSessionCompat.Callback) {
        mediaSession?.setCallback(callback)
    }
} 