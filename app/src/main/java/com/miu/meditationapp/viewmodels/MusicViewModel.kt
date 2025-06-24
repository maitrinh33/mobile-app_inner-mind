package com.miu.meditationapp.viewmodels

import android.app.Application
import android.content.Intent
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
import java.util.Date
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val db = MusicDatabase.getInstance(application)
    private val auth = FirebaseAuth.getInstance()
    private val adminSongService = AdminSongService()
    private val userId: String get() = auth.currentUser?.uid ?: ""
    
    private val _databaseError = MutableStateFlow<Exception?>(null)
    val databaseError: StateFlow<Exception?> = _databaseError.asStateFlow()
    
    // State flows for different song lists
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _adminSongs = MutableStateFlow<List<SongEntity>>(emptyList())
    val adminSongs: StateFlow<List<SongEntity>> = _adminSongs.asStateFlow()
    
    // Main songs list with search functionality and admin songs
    val songs: StateFlow<List<SongEntity>> = combine(
        _searchQuery,
        _adminSongs,
        db.songDao().getAllSongs(userId)
    ) { query, adminSongs, userSongs ->
        Log.d("MusicViewModel", "Combining songs - Admin songs: ${adminSongs.size}, User songs: ${userSongs.size}")
        val allSongs = adminSongs + userSongs
        val filteredSongs = if (query.isBlank()) {
            allSongs
        } else {
            allSongs.filter { song ->
                song.title.contains(query, ignoreCase = true) ||
                song.artist.contains(query, ignoreCase = true)
            }
        }
        Log.d("MusicViewModel", "Final songs list size: ${filteredSongs.size}")
        filteredSongs
    }.stateIn(
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

    // Admin functionality
    val isAdmin: StateFlow<Boolean> = flow {
        emit(adminSongService.isCurrentUserAdmin())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    private var musicService: MusicServiceRefactored? = null
    private val dropboxHelper = DropboxHelper

    private val _currentPlayingSong = MutableStateFlow<SongEntity?>(null)
    val currentPlayingSong = _currentPlayingSong.asStateFlow()

    private val _playbackError = MutableStateFlow<Pair<Int, String>?>(null)
    val playbackError = _playbackError.asStateFlow()

    // Cache for Dropbox URLs
    private val urlCache = mutableMapOf<String, Pair<String, Long>>()
    private val URL_CACHE_DURATION = 86400000L // 24 hours in milliseconds - Dropbox URLs are stable for longer

    // Track current loading song to clear previous loading states
    private var currentLoadingSongId: Int? = null
    private var prefetchJob: Job? = null

    init {
        // Start listening for admin songs
        listenForAdminSongs()
        
        // Start prefetching URLs in background
        viewModelScope.launch(Dispatchers.IO) {
            songs.collect { songs ->
                prefetchAdminSongUrls(songs)
            }
        }
    }

    fun verifyDatabaseAccess() {
        viewModelScope.launch {
            try {
                // Verify admin songs access
                adminSongService.verifyDatabaseRules()
                
                // Clear any previous errors
                _databaseError.value = null
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Database access verification failed", e)
                _databaseError.value = e
            }
        }
    }

    private fun listenForAdminSongs() {
        Log.d("MusicViewModel", "Starting to listen for admin songs")
        val adminSongsRef = FirebaseDatabase.getInstance().getReference("admin_songs")
        
        // Enable offline persistence for admin songs
        adminSongsRef.keepSynced(true)
        
        // First check if we have read permission
        adminSongsRef.limitToFirst(1).get()
            .addOnSuccessListener { snapshot ->
                Log.d("MusicViewModel", "Successfully verified admin songs read permission")
                
                // Now set up the listener
                adminSongsRef.addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        Log.d("MusicViewModel", "Admin songs data changed. Children count: ${snapshot.childrenCount}")
                        val adminSongsList = mutableListOf<SongEntity>()
                        for (songSnapshot in snapshot.children) {
                            try {
                                val songMap = songSnapshot.value as? Map<*, *>
                                Log.d("MusicViewModel", "Processing admin song: ${songMap?.get("title")}, Key: ${songSnapshot.key}")
                                
                                if (songMap == null) {
                                    Log.e("MusicViewModel", "Invalid song data format for key: ${songSnapshot.key}")
                                    continue
                                }

                                val songEntity = SongEntity(
                                    title = songMap["title"] as? String ?: "Unknown",
                                    artist = songMap["artist"] as? String ?: "Unknown Artist",
                                    album = songMap["album"] as? String ?: "Unknown Album",
                                    duration = songMap["duration"] as? String ?: "--:--",
                                    uri = songMap["uri"] as? String ?: "",
                                    fileSize = (songMap["fileSize"] as? Number)?.toLong() ?: 0L,
                                    dateAdded = Date((songMap["dateAdded"] as? Number)?.toLong() ?: 0L),
                                    isAdminSong = songMap["isAdminSong"] as? Boolean ?: false,
                                    firebaseId = songSnapshot.key,
                                    userId = "admin"
                                )

                                if (songEntity.uri.isNotEmpty()) {
                                    Log.d("MusicViewModel", "Adding admin song to list: ${songEntity.title}")
                                    adminSongsList.add(songEntity)
                                } else {
                                    Log.w("MusicViewModel", "Skipping admin song with empty URI: ${songEntity.title}")
                                }
                            } catch (e: Exception) {
                                Log.e("MusicViewModel", "Error processing admin song: ${songSnapshot.key}", e)
                            }
                        }
                        Log.d("MusicViewModel", "Setting admin songs list. Size: ${adminSongsList.size}")
                        _adminSongs.value = adminSongsList
                        _databaseError.value = null
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("MusicViewModel", "Error listening for admin songs", error.toException())
                        _databaseError.value = error.toException()
                        // If permission denied, just set empty admin songs list
                        if (error.code == DatabaseError.PERMISSION_DENIED) {
                            _adminSongs.value = emptyList()
                        }
                    }
                })
            }
            .addOnFailureListener { e ->
                Log.e("MusicViewModel", "Failed to verify admin songs read permission", e)
                _databaseError.value = e
                // If permission denied, just set empty admin songs list
                _adminSongs.value = emptyList()
            }
    }

    // CRUD Operations
    fun addSong(song: SongEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (song.isAdminSong && !adminSongService.isCurrentUserAdmin()) {
                    throw SecurityException("Only admins can add admin songs")
                }
                
                // For admin songs, they're already in Firebase
                if (!song.isAdminSong) {
                    // Ensure the song has the current user's ID
                    val songWithUserId = song.copy(userId = userId)
                    db.songDao().insert(songWithUserId)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updateSong(song: SongEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (song.isAdminSong) {
                    adminSongService.updateAdminSong(song)
                } else {
                    // Ensure the song has the current user's ID
                    val songWithUserId = song.copy(userId = userId)
                    db.songDao().update(songWithUserId)
                }
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
                if (song.isAdminSong) {
                    adminSongService.deleteAdminSong(song)
                } else {
                    // Delete the actual file first
                    deleteSongFile(song.uri)
                    // Then delete from database
                    db.songDao().delete(song)
                }
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

    fun uploadAdminSong(
        inputStream: java.io.InputStream,
        fileName: String,
        title: String,
        artist: String,
        album: String,
        duration: String,
        fileSize: Long,
        onProgress: (Int) -> Unit,
        onSuccess: (SongEntity) -> Unit,
        onError: (Exception) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                adminSongService.uploadAdminSong(
                    inputStream = inputStream,
                    fileName = fileName,
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                    fileSize = fileSize,
                    onProgress = onProgress,
                    onSuccess = onSuccess,
                    onError = onError
                )
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun setMusicService(service: MusicServiceRefactored?) {
        musicService = service
    }

    suspend fun playSong(song: SongEntity) {
        viewModelScope.launch {
            try {
                // Clear previous loading state if exists
                currentLoadingSongId?.let { previousId ->
                    _playbackError.value = Pair(previousId, "Cancelled - New song selected")
                }
                currentLoadingSongId = song.id.toInt()

                val finalUri = if (song.isAdminSong) {
                    getDropboxUrl(song.uri)
                } else {
                    song.uri
                }
                
                val intent = Intent(getApplication(), MusicServiceRefactored::class.java).apply {
                    action = MusicServiceRefactored.ACTION_PLAY_SONG
                    putExtra("songId", song.id.toString())
                    putExtra("title", song.title)
                    putExtra("uri", finalUri)
                    putExtra("duration", song.duration)
                }
                getApplication<Application>().startService(intent)
                
                _currentPlayingSong.value = song
                incrementPlayCount(song.id.toInt())
                
                // Clear current loading song ID after successful play
                currentLoadingSongId = null
            } catch (e: Exception) {
                _playbackError.value = Pair(song.id.toInt(), e.message ?: "Unknown error")
                currentLoadingSongId = null
                throw e
            }
        }
    }

    private suspend fun getDropboxUrl(originalUrl: String): String {
        // Check cache first
        val cachedUrl = urlCache[originalUrl]
        if (cachedUrl != null) {
            val (url, timestamp) = cachedUrl
            if (System.currentTimeMillis() - timestamp < URL_CACHE_DURATION) {
                return url
            }
        }

        // If not in cache or expired, get new URL
        return withContext(Dispatchers.IO) {
            try {
                val newUrl = dropboxHelper.getStreamingUrl(originalUrl)
                // Cache the new URL
                urlCache[originalUrl] = Pair(newUrl, System.currentTimeMillis())
                newUrl
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error getting Dropbox URL", e)
                throw e
            }
        }
    }

    // Prefetch URLs for admin songs to improve loading time
    fun prefetchAdminSongUrls(songs: List<SongEntity>) {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            val adminSongs = songs.filter { it.isAdminSong }
            val deferredUrls = adminSongs.map { song ->
                async(Dispatchers.IO) {
                    try {
                        val needsUpdate = !urlCache.containsKey(song.uri) || 
                            System.currentTimeMillis() - (urlCache[song.uri]?.second ?: 0) > URL_CACHE_DURATION
                            
                        if (needsUpdate) {
                            val url = dropboxHelper.getStreamingUrl(song.uri)
                            urlCache[song.uri] = Pair(url, System.currentTimeMillis())
                            true
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        Log.e("MusicViewModel", "Error prefetching URL for song ${song.id}", e)
                        false
                    }
                }
            }
            
            try {
                deferredUrls.awaitAll()
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error during URL prefetching", e)
            }
        }
    }

    fun clearPlaybackError() {
        _playbackError.value = null
    }
} 