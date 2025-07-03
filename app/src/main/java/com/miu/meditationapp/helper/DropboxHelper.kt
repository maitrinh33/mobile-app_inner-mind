package com.miu.meditationapp.helper

import android.util.Log
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.InvalidAccessTokenException
import com.dropbox.core.v2.DbxClientV2
import java.io.InputStream
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.io.FileOutputStream

object DropboxHelper {
    private const val ACCESS_TOKEN =
"sl.u.AF17TlT8bCyu4BsRA3Cejl2DHuu8tJfFMX3_5LoyXp__E8tLe0dUh8LvwC096JvEamtGsC3yFwg3_TzwbM87RvVdS1jql6nPzyg_FUR9wqNwXBIrSst_wupoxXfO29QzWU--Fy9ErXLReDd6YlEB2IgnNKh9K91pCYO6L8mBNb1V8pJXWZNkBfsEhMbG65XOBZfyIV0-apWWDb8LQdjUh3qHETC_7RJJIPy-Im_7YMzvHTWUlz0C2IJGIELGrU_Twmu0hFs7SD8O2BdVOScxciNEaEpRkRlBb0qMaz42hoQ1t5uB2BRcttkEHSTKK8QKUAOqESBsHv9B9rwu4ixKZSWhdXdL-qO6uYQIFs0Z91P4DHc0FWt6CkzLZC0NHOn63a63yEF1lLpfN-_q49ouZ7H10U8c85RLY0IWGoclBOcesPFNvldS4F0sf2S1QzddD2y8qzmIkhFL_AC8BKUOV4yOyWy2OQsyw2yD-oj-SjWsHMW7RTZOqTjozWZIvXssVKjzQ0Yr78-t5v8uuy202zvwkHQcZtEvpS9cL_V-R4SgboQ4KlqUthxMk0ikQhUfjP_fU-qRIn5P_Rcbo48XhA_SmjYi8602tCbkf21IfgmyO7ip3Ep-zz3xx-zfw8KujIMDsq0-fM1EnwV4SEgn_KXdVa7AiJ_79nZBwlcFzOIUuKNUVjdhXrUkYCLVw49rlShbBtTNc8w3K0e25atdOv-gV4rwhkobsQ-NdsyVTNezsSrJoF1uTf1gbfKBj6U5ju_p23Q8M9IaKNrCdj79E2bLQsVqfrus_FtSpFFr0vm4NipUXHFSWX1JqVIcSgq-Q6cS0Pq-EaYy52BDZBXnmoO1Jv6OH7dI1QADKVTgcyCpd1kYbX_RcUdv4hF-jG7-uKuOgUooZvgSJ1sgNOAbWngv4Tf-Nq4DPNyhb5BlI0gWuzxlKZBq7DUYKFZf__ZbWx_0mo_-KFCYTp4wWrwAHrUOyjmlPBXR8AJ3nvgRG66IY9DyCuMyLWg-7iBHWCzy2whDcslwo6j-tynDXbRT_NEanccoky4U40QW3HysgMR0Djckxwdnp0ql7yCrmIMszVhiwVezQLA9giuVCBUZP8OKoIIQSeJ5KFBkt-UqEn11q2ZbwpDlriqQ0xOokamcZgWxqQ65jfB9JOjmki-6pdPo7eju-3ehwY4PMTT8N5-tQ7TFuQ7puquF0OapW9CjHLmabSO-nQ3Qm0MsRKKn1J13uXSb7twCm9OBeeo6CmUAprq7RvoMtq10ihDl7Yc1DErvWBHpeL8pBNcDxudwWQU7OC4mBcM8abg5WEcsHRG7yo9f6n3Yg4U8sfMt15Cyz8txLknOP-CKTtVsNlo2ZfEnQoSYCyFi7U98tof80sGecguPyngnlMS6iO69zd9yJl5Xgd89ZyPfHKQaOYjt8PqdxdNslprThvAuhNbSJskjA4UE71U4O_XY_uBSzg8RWtTpdDvJ5D5zr6kWYIiJtCcd"
    private val config = DbxRequestConfig.newBuilder("meditation-app").build()
    private var client = DbxClientV2(config, ACCESS_TOKEN)

    private fun refreshClientIfNeeded() {
        try {
            // Test the client with a simple API call
            client.users().getCurrentAccount()
        } catch (e: InvalidAccessTokenException) {
            Log.e("DropboxHelper", "Access token expired, please update the token", e)
            throw e
        } catch (e: Exception) {
            Log.e("DropboxHelper", "Error testing Dropbox client", e)
            throw e
        }
    }

    fun uploadFile(
        inputStream: InputStream,
        dropboxPath: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            // Create a copy of the input stream data first
            val byteArray = try {
                inputStream.readBytes()
            } finally {
                try { inputStream.close() } catch (_: Exception) {}
            }

            thread {
                try {
                    // Verify token is valid before upload
                    refreshClientIfNeeded()

                    // Upload from the byte array
                    byteArray.inputStream().use { input ->
                        client.files().uploadBuilder(dropboxPath)
                            .uploadAndFinish(input)
                    }
                    
                    onSuccess()
                } catch (e: Exception) {
                    Log.e("DropboxHelper", "Failed to upload file", e)
                    when (e) {
                        is InvalidAccessTokenException -> {
                            Log.e("DropboxHelper", "Access token expired, please update the token", e)
                            onError(Exception("Dropbox authentication failed. Please contact administrator."))
                        }
                        else -> onError(e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DropboxHelper", "Error in uploadFile", e)
            onError(e)
        }
    }

    fun getSharedLink(dropboxPath: String): String {
        return try {
            refreshClientIfNeeded()
            val sharedLinkMetadata = client.sharing().createSharedLinkWithSettings(dropboxPath)
            // Convert to content API URL
            sharedLinkMetadata.url
                .replace("www.dropbox.com", "dl.dropboxusercontent.com")
                .replace("?dl=0", "")
                .replace("?raw=1", "")
        } catch (e: Exception) {
            Log.e("DropboxHelper", "Failed to get shared link", e)
            when (e) {
                is InvalidAccessTokenException -> {
                    Log.e("DropboxHelper", "Access token expired, please update the token", e)
                    throw Exception("Dropbox authentication failed. Please contact administrator.")
                }
                else -> throw e
            }
        }
    }

    suspend fun getStreamingUrl(originalUrl: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // If it's already a raw=1 URL, just return it
                if (originalUrl.contains("raw=1")) {
                    return@withContext originalUrl
                }

                // Convert shared link to direct download link
                val url = if (originalUrl.contains("?")) {
                    "${originalUrl}&raw=1"
                } else {
                    "${originalUrl}?raw=1"
                }

                // Skip HEAD request verification - Dropbox URLs are stable
                // We'll let the MediaPlayer handle any URL issues
                url
            } catch (e: Exception) {
                Log.e("DropboxHelper", "Error getting streaming URL", e)
                throw e
            }
        }
    }

    suspend fun prefetchUrls(
        songs: List<com.miu.meditationapp.databases.SongEntity>,
        cache: MutableMap<String, Pair<String, Long>>,
        cacheDuration: Long
    ) = coroutineScope {
        val adminSongs = songs.filter { it.isAdminSong }
        val deferredUrls = adminSongs.map { song ->
            async {
                try {
                    val needsUpdate = !cache.containsKey(song.uri) ||
                        System.currentTimeMillis() - (cache[song.uri]?.second ?: 0) > cacheDuration

                    if (needsUpdate) {
                        val url = getStreamingUrl(song.uri)
                        cache[song.uri] = Pair(url, System.currentTimeMillis())
                        true
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    Log.e("DropboxHelper", "Error prefetching URL for song ${song.id}", e)
                    false
                }
            }
        }
        try {
            deferredUrls.awaitAll()
        } catch (e: Exception) {
            Log.e("DropboxHelper", "Error during URL prefetching", e)
        }
    }

    suspend fun downloadFile(dropboxPath: String, destFile: File) {
        withContext(Dispatchers.IO) {
            try {
                DropboxHelper.refreshClientIfNeeded()
                client.files().download(dropboxPath).inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DropboxHelper", "Error downloading file", e)
                throw e
            }
        }
    }
}
