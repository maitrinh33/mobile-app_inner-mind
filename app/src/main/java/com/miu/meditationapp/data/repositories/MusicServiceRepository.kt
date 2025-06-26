package com.miu.meditationapp.data.repositories

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.miu.meditationapp.databases.SongEntity
import com.miu.meditationapp.services.MusicServiceRefactored
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicServiceRepository @Inject constructor(
    private val context: Context
) {
    private var musicService: MusicServiceRefactored? = null
    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MusicServiceRefactored.MusicBinder
            musicService = binder?.getService()
            _isServiceBound.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            _isServiceBound.value = false
        }
    }

    fun bindService() {
        Intent(context, MusicServiceRefactored::class.java).also { intent ->
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindService() {
        if (_isServiceBound.value) {
            context.unbindService(serviceConnection)
            _isServiceBound.value = false
        }
    }

    fun playSong(song: SongEntity) {
        val intent = Intent(context, MusicServiceRefactored::class.java).apply {
            action = MusicServiceRefactored.ACTION_PLAY_SONG
            putExtra("songId", song.id.toString())
            putExtra("title", song.title)
            putExtra("uri", song.uri)
            putExtra("duration", song.duration)
        }
        context.startService(intent)
    }

    fun pauseSong() {
        val intent = Intent(context, MusicServiceRefactored::class.java).apply {
            action = MusicServiceRefactored.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeSong() {
        val intent = Intent(context, MusicServiceRefactored::class.java).apply {
            action = MusicServiceRefactored.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun stopSong() {
        val intent = Intent(context, MusicServiceRefactored::class.java).apply {
            action = MusicServiceRefactored.ACTION_STOP
        }
        context.startService(intent)
    }
} 