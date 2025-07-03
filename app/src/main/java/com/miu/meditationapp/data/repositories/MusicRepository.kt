package com.miu.meditationapp.data.repositories

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.media.MediaMetadataRetriever
import com.miu.meditationapp.databases.SongEntity
import com.miu.meditationapp.services.AdminSongService
import com.miu.meditationapp.databases.SongDao
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Date
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.StateFlow
import com.miu.meditationapp.helper.DropboxHelper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import android.util.Log

@Singleton
class MusicRepository @Inject constructor(
    private val context: Context,
    private val songDao: SongDao,
    private val adminSongService: AdminSongService,
    private val auth: FirebaseAuth
) {
    private val _playbackError = MutableStateFlow<Pair<Int, String>?>(null)
    val playbackError: StateFlow<Pair<Int, String>?> = _playbackError.asStateFlow()

    private val _currentPlayingSong = MutableStateFlow<SongEntity?>(null)
    val currentPlayingSong: StateFlow<SongEntity?> = _currentPlayingSong.asStateFlow()

    private var adminSongCacheJob: Job? = null

    private val userId: String get() = auth.currentUser?.uid ?: ""

    fun getSongs(): Flow<List<SongEntity>> = songDao.getSongsByUser(userId)

    suspend fun addSong(song: SongEntity) {
        if (song.isAdminSong && !adminSongService.isCurrentUserAdmin()) {
            throw SecurityException("Only admins can add admin songs")
        }
        val songWithUserId = song.copy(userId = userId)
        songDao.insert(songWithUserId)
    }

    suspend fun updateSong(song: SongEntity) {
        if (song.isAdminSong) {
            adminSongService.updateAdminSong(song)
        } else {
            val songWithUserId = song.copy(userId = userId)
            songDao.insert(songWithUserId)
        }
    }

    suspend fun incrementPlayCount(songId: Int) {
        songDao.incrementPlayCount(songId, userId, Date())
    }

    suspend fun deleteSongById(songId: Int) {
        val song = songDao.getSongById(songId)
        song?.let {
            deleteSongFile(it.uri)
            songDao.deleteById(songId, userId)
        }
    }

    private fun deleteSongFile(uri: String) {
        if (uri.startsWith("file://")) {
            val file = File(uri.substring(7))
            if (file.exists()) {
                file.delete()
            }
        }
    }

    suspend fun getSongById(songId: Int): SongEntity? {
        return songDao.getSongById(songId)
    }

    suspend fun getSongByUri(uri: String): SongEntity? {
        return songDao.getSongByUri(uri, userId)
    }

    suspend fun updateSongTitle(songId: Int, newTitle: String) {
        songDao.updateTitle(songId, userId, newTitle.trim())
    }

    suspend fun deleteSong(song: SongEntity) {
        if (song.isAdminSong) {
            adminSongService.deleteAdminSong(song)
        } else {
            songDao.delete(song)
        }
    }

    suspend fun toggleFavorite(songId: Int, isFavorite: Boolean) {
        songDao.updateFavorite(songId, userId, isFavorite)
    }

    fun isAdmin(): Flow<Boolean> = flow { emit(adminSongService.isCurrentUserAdmin()) }

    suspend fun uploadAdminSongFromUri(
        uri: Uri,
        onSuccess: (SongEntity) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            if (!isAdmin().first()) {
                throw SecurityException("Only admins can upload admin songs")
            }

            val metadata = extractMetadata(uri)
            val fileName = metadata.title.replace(" ", "_") + "_" + System.currentTimeMillis() + ".mp3"

            adminSongService.uploadAdminSong(
                uri = uri,
                fileName = fileName,
                title = metadata.title,
                artist = metadata.artist,
                album = metadata.album,
                duration = metadata.duration,
                fileSize = metadata.fileSize,
                onSuccess = onSuccess,
                onError = onError
            )
        } catch (e: Exception) {
            onError(e)
        }
    }

    suspend fun addSongFromUri(uri: Uri): SongEntity? {
        return try {
            val metadata = extractMetadata(uri)
            val internalUri = copyFileToInternalStorage(uri)
            
            internalUri?.let {
                SongEntity(
                    title = metadata.title,
                    duration = metadata.duration,
                    uri = it.toString(),
                    artist = metadata.artist,
                    album = metadata.album,
                    fileSize = metadata.fileSize,
                    userId = userId
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractMetadata(uri: Uri): SongMetadata {
        var title = "Unknown"
        var fileSize = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) title = cursor.getString(nameIndex)
                if (sizeIndex >= 0) fileSize = cursor.getLong(sizeIndex)
            }
        }

        val mmr = MediaMetadataRetriever()
        var duration: String
        var artist: String
        var album: String
        try {
            mmr.setDataSource(context, uri)
            val durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            duration = formatDuration(durationMs)
            artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"

            if (title == "Unknown") {
                title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "Unknown"
            }
        } finally {
            mmr.release()
        }

        return SongMetadata(title, duration, artist, album, fileSize, userId)
    }

    private fun copyFileToInternalStorage(uri: Uri): Uri? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName = "song_${System.currentTimeMillis()}.mp3"
            val file = File(context.filesDir, fileName)

            inputStream.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            file.setReadable(true, false)
            Uri.fromFile(file)
        } catch (e: Exception) {
            null
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    data class SongMetadata(
        val title: String,
        val duration: String,
        val artist: String,
        val album: String,
        val fileSize: Long,
        val userId: String = ""
    )

    suspend fun playSong(song: SongEntity, onPrepared: (SongEntity) -> Unit) {
        if (song.isAdminSong) {
            val fileName = Uri.parse(song.uri).lastPathSegment ?: song.uri
            val cachedFile = File(context.cacheDir, fileName)
            if (cachedFile.exists()) {
                val updatedSong = song.copy(localPath = cachedFile.toURI().toString())
                onPrepared(updatedSong)
            } else {
                try {
                    val dropboxPath = "/admin_songs/$fileName"
                    Log.d("MusicRepository", "Attempting to download from Dropbox path: $dropboxPath")
                    DropboxHelper.downloadFile(dropboxPath, cachedFile)
                    Log.d("MusicRepository", "Downloaded file for song: ${song.title}, path: ${cachedFile.absolutePath}")
                    val updatedSong = song.copy(localPath = cachedFile.toURI().toString())
                    onPrepared(updatedSong)
                } catch (e: Exception) {
                    _playbackError.value = Pair(song.id, e.message ?: "Unknown error")
                }
            }
        } else {
            onPrepared(song)
        }
    }

    fun clearPlaybackError() {
        _playbackError.value = null
    }

    fun getFavoriteSongs(): Flow<List<SongEntity>> = songDao.getFavoriteSongs(userId)
    fun getMostPlayedSongs(): Flow<List<SongEntity>> = songDao.getMostPlayedSongs(userId)
    fun getRecentlyPlayedSongs(): Flow<List<SongEntity>> = songDao.getRecentlyPlayedSongs(userId)
    fun getSongCount(): Flow<Int> = flow { emit(songDao.getSongCount(userId)) }
    fun getFavoriteCount(): Flow<Int> = flow { emit(songDao.getFavoriteCount(userId)) }
    fun getAdminSongs(): Flow<List<SongEntity>> = adminSongService.getAdminSongs()

    suspend fun prefetchAdminSongUrls(@Suppress("UNUSED_PARAMETER") songs: List<SongEntity>) {
        // Stub: implement prefetch logic if needed
    }
} 