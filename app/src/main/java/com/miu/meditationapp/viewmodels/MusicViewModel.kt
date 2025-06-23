package com.miu.meditationapp.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miu.meditationapp.databases.MusicDatabase
import com.miu.meditationapp.databases.SongEntity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val db = MusicDatabase.getInstance(application)
    private val auth = FirebaseAuth.getInstance()
    private val userId: String get() = auth.currentUser?.uid ?: ""
    
    // State flows for different song lists
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // Main songs list with search functionality
    val songs: StateFlow<List<SongEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                db.songDao().getAllSongs(userId)
            } else {
                db.songDao().searchSongs(userId, query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Favorite songs
    val favoriteSongs: StateFlow<List<SongEntity>> = db.songDao()
        .getFavoriteSongs(userId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Most played songs
    val mostPlayedSongs: StateFlow<List<SongEntity>> = db.songDao()
        .getMostPlayedSongs(userId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Recently played songs
    val recentlyPlayedSongs: StateFlow<List<SongEntity>> = db.songDao()
        .getRecentlyPlayedSongs(userId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Statistics
    val songCount: StateFlow<Int> = flow {
        emit(db.songDao().getSongCount(userId))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )
    
    val favoriteCount: StateFlow<Int> = flow {
        emit(db.songDao().getFavoriteCount(userId))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    // CRUD Operations
    fun addSong(song: SongEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ensure the song has the current user's ID
                val songWithUserId = song.copy(userId = userId)
                db.songDao().insert(songWithUserId)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updateSong(song: SongEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ensure the song has the current user's ID
                val songWithUserId = song.copy(userId = userId)
                db.songDao().update(songWithUserId)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun updateSongTitle(songId: Int, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.songDao().updateTitle(songId, userId, newTitle.trim())
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun toggleFavorite(songId: Int, isFavorite: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.songDao().updateFavorite(songId, userId, isFavorite)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun incrementPlayCount(songId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.songDao().incrementPlayCount(songId, userId, Date())
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun deleteSong(song: SongEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Delete the actual file first
                deleteSongFile(song.uri)
                
                // Then delete from database
                db.songDao().delete(song)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun deleteSongById(songId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val song = db.songDao().getSongById(songId, userId)
                song?.let {
                    deleteSongFile(it.uri)
                    db.songDao().deleteById(songId, userId)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    private fun deleteSongFile(uri: String) {
        try {
            if (uri.startsWith("file://")) {
                val file = File(uri.substring(7)) // Remove "file://" prefix
                if (file.exists()) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // Handle file deletion error
        }
    }
    
    // Search functionality
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun clearSearch() {
        _searchQuery.value = ""
    }
    
    // Utility functions
    fun getSongById(songId: Int): SongEntity? {
        return songs.value.find { it.id == songId }
    }
    
    fun getSongByUri(uri: String): SongEntity? {
        return songs.value.find { it.uri == uri }
    }
} 