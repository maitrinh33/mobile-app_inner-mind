package com.miu.meditationapp.helper

import android.content.Context
import com.miu.meditationapp.databases.SongEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.net.URL

class AdminSongCacheHelper(private val context: Context) {
    fun isSongCached(song: SongEntity): Boolean {
        val file = getCacheFile(song)
        return file.exists() && file.canRead()
    }

    suspend fun cacheSong(song: SongEntity) = withContext(Dispatchers.IO) {
        val file = getCacheFile(song)
        if (file.exists() && file.canRead()) return@withContext
        try {
            // Example using OkHttp (recommended for production)
            // val client = OkHttpClient()
            // val request = Request.Builder().url(song.uri).build()
            // val response = client.newCall(request).execute()
            // if (!response.isSuccessful) throw IOException("Failed to download file: ${response.code}")
            // val sink = file.sink().buffer()
            // sink.writeAll(response.body!!.source())
            // sink.close()

            // Simple Java URL fallback (not recommended for large files or production)
            URL(song.uri).openStream().use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            if (file.exists()) file.delete()
            throw e
        }
    }

    fun getCacheFile(song: SongEntity): File {
        // Use a safe filename
        val safeTitle = song.title.replace(Regex("[^a-zA-Z0-9_]"), "_")
        return File(context.cacheDir, "${safeTitle}_${song.id}.mp3")
    }
} 