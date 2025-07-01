package com.miu.meditationapp.helper

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import com.google.firebase.auth.FirebaseAuth
import com.miu.meditationapp.data.repositories.MusicRepository
import com.miu.meditationapp.databases.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object FilePickerHelper {
    fun launchSongPicker(
        context: Context,
        auth: FirebaseAuth,
        launcher: ActivityResultLauncher<Array<String>>
    ) {
        if (auth.currentUser == null) {
            DialogHelper.showError(context, "Please sign in to add songs")
            return
        }
        launcher.launch(arrayOf("audio/*"))
    }

    fun launchAdminSongPicker(
        context: Context,
        isAdmin: Boolean,
        launcher: ActivityResultLauncher<Array<String>>
    ) {
        if (!isAdmin) {
            DialogHelper.showError(context, "Only admins can upload songs")
            return
        }
        launcher.launch(arrayOf("audio/*"))
    }

    fun createSongEntity(
        uri: Uri,
        userId: String
    ): SongEntity {
        return SongEntity(
            title = uri.lastPathSegment ?: "Unknown",
            duration = "--:--",
            uri = uri.toString(),
            artist = "Unknown Artist",
            album = "Unknown Album",
            fileSize = 0L,
            userId = userId
        )
    }

    fun createSongMetadata(
        uri: Uri,
        userId: String
    ): MusicRepository.SongMetadata {
        return MusicRepository.SongMetadata(
            title = uri.lastPathSegment ?: "Unknown",
            duration = "--:--",
            artist = "Unknown Artist",
            album = "Unknown Album",
            fileSize = 0L,
            userId = userId
        )
    }

    fun persistUriPermission(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // Ignore if already granted or not needed
        }
    }

    suspend fun copyContentUriToCache(context: Context, uri: Uri, fileName: String): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        file
    }
} 