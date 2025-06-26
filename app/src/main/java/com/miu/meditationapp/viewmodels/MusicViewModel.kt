package com.miu.meditationapp.viewmodels

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miu.meditationapp.databases.MusicDatabase
import com.miu.meditationapp.databases.SongEntity
import com.miu.meditationapp.helper.DropboxHelper
import com.miu.meditationapp.services.AdminSongService
import com.miu.meditationapp.services.MusicServiceRefactored
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.util.Date
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import com.miu.meditationapp.data.repositories.MusicRepository
import com.miu.meditationapp.data.repositories.MusicServiceRepository

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val musicServiceRepository: MusicServiceRepository
) : ViewModel() {
    private val _databaseError = MutableStateFlow<Throwable?>(null)
    val databaseError: StateFlow<Throwable?> = _databaseError.asStateFlow()
    
    // State flows for different song lists
    val songs: StateFlow<List<SongEntity>> = repository.getSongs().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    val favoriteSongs: StateFlow<List<SongEntity>> = repository.getFavoriteSongs().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    val mostPlayedSongs: StateFlow<List<SongEntity>> = repository.getMostPlayedSongs().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    val recentlyPlayedSongs: StateFlow<List<SongEntity>> = repository.getRecentlyPlayedSongs().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    val songCount: StateFlow<Int> = repository.getSongCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )
    val favoriteCount: StateFlow<Int> = repository.getFavoriteCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )
    val adminSongs: StateFlow<List<SongEntity>> = repository.getAdminSongs().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    val isAdmin: StateFlow<Boolean> = repository.isAdmin().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )
    val currentPlayingSong: StateFlow<SongEntity?> = repository.currentPlayingSong
    val playbackError: StateFlow<Pair<Int, String>?> = repository.playbackError

    fun addSong(song: SongEntity) = viewModelScope.launch {
        try { repository.addSong(song) } catch (e: Exception) { _databaseError.value = e }
    }
    fun updateSong(song: SongEntity) = viewModelScope.launch {
        try { repository.updateSong(song) } catch (e: Exception) { _databaseError.value = e }
    }
    fun updateSongTitle(songId: Int, newTitle: String) = viewModelScope.launch {
        try { repository.updateSongTitle(songId, newTitle) } catch (e: Exception) { _databaseError.value = e }
    }
    fun toggleFavorite(songId: Int, isFavorite: Boolean) = viewModelScope.launch {
        try { repository.toggleFavorite(songId, isFavorite) } catch (e: Exception) { _databaseError.value = e }
    }
    fun incrementPlayCount(songId: Int) = viewModelScope.launch {
        try { repository.incrementPlayCount(songId) } catch (e: Exception) { /* Handle error */ }
    }
    fun deleteSong(song: SongEntity) = viewModelScope.launch {
        try { repository.deleteSong(song) } catch (e: Exception) { _databaseError.value = e }
    }
    fun deleteSongById(songId: Int) = viewModelScope.launch {
        try { repository.deleteSongById(songId) } catch (e: Exception) { /* Handle error */ }
    }
    suspend fun getSongById(songId: Int): SongEntity? = repository.getSongById(songId)
    suspend fun getSongByUri(uri: String): SongEntity? = repository.getSongByUri(uri)
    fun uploadAdminSongFromUri(
        uri: Uri,
        onProgress: (Int) -> Unit,
        onSuccess: (SongEntity) -> Unit,
        onError: (Exception) -> Unit
    ) = viewModelScope.launch {
        repository.uploadAdminSongFromUri(uri, onProgress, onSuccess, onError)
    }
    fun playSong(song: SongEntity) = musicServiceRepository.playSong(song)
    fun pauseSong() = musicServiceRepository.pauseSong()
    fun resumeSong() = musicServiceRepository.resumeSong()
    fun stopSong() = musicServiceRepository.stopSong()
    fun prefetchAdminSongUrls(songs: List<SongEntity>) = viewModelScope.launch { repository.prefetchAdminSongUrls(songs) }
    fun clearPlaybackError() { repository.clearPlaybackError() }
    override fun onCleared() { super.onCleared() }
} 