package com.miu.meditationapp.helper

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miu.meditationapp.data.repositories.MusicRepository
import com.miu.meditationapp.databases.SongEntity
import com.miu.meditationapp.viewmodels.MusicViewModel
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.auth.FirebaseAuth

class UploadManager(
    private val context: Context,
    private val viewModel: MusicViewModel,
    private val auth: FirebaseAuth
) {
    suspend fun handleAdminUpload(
        uri: Uri,
        onError: (String) -> Unit
    ) {
        try {
            val progressDialog = DialogHelper.showUploadProgressDialog(context)
            val dialog = progressDialog.show()

            try {
                viewModel.uploadAdminSongFromUri(
                    uri = uri,
                    onProgress = { progress: Int ->
                        dialog.setMessage("Uploading: $progress%")
                    },
                    onSuccess = { song: SongEntity ->
                        dialog.dismiss()
                        DialogHelper.showError(context, "Admin song added: ${song.title}")
                    },
                    onError = { e: Exception ->
                        dialog.dismiss()
                        onError("Failed to upload admin song: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                dialog.dismiss()
                onError(e.message ?: "Upload failed")
            }
        } catch (e: Exception) {
            onError(e.message ?: "Failed to prepare upload")
        }
    }

    suspend fun handleUserUpload(uri: Uri): SongEntity {
        val userId = auth.currentUser?.uid ?: ""
        return SongEntity(
            id = 0,
            title = getFileName(uri),
            uri = uri.toString(),
            userId = userId,
            dateAdded = Date(),
            duration = getDuration(uri).toString(),
            isAdminSong = false,
            isFavorite = false
        )
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    result = cursor.getString(nameIndex)
                }
            }
        }
        return result ?: "Unknown"
    }

    private fun getDuration(uri: Uri): Long {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun createSongMetadata(uri: Uri, userId: String): Map<String, String> {
        return mapOf(
            "fileName" to getFileName(uri),
            "userId" to userId,
            "uploadTime" to System.currentTimeMillis().toString(),
            "duration" to getDuration(uri).toString()
        )
    }
} 