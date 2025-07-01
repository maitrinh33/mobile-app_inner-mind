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
    // Note: This is a long-lived access token that doesn't expire
    private const val ACCESS_TOKEN =
"sl.u.AF0NuDanvsRo7F5qg69ap9MqG4tNCbQ1jTc1L6cTwSxWUQIOggwLGackS3nPQ4Ex67CAaauM38bo8IKcOyRPSu3Bi8Xx5eJWLeO3HCLZ1MS0aTFv2BQoXaSgzuncXSz5jwGFJDWHe0e-2xJVT8KginC7q_4Ofi-ApzPquul6y2_kV5JgYzGSTZhwyWaEUf1FV3euEJwBrEnaXYpqs9vqN8eQz4LDOX7L-ufQ_srizK09PADoP61bEUXahczNetf0ffd3qzjTuMZ4Ucj2F0JaxgegOo52jioOXaMcT--NXrT9Poj6FOvSu6H-ZI1qUgBd3TlpllMbiG4H_fl9f0fGFSsLjDmWsG7KQtnW6zIwNHB22Tdll4LvsGlXXabttWT8th_LGMPdKgnYQUM1W8798k67-lge5XYs6nD0L5vPE8W-bVifUMf-BsJ7tIS8ERyCFjL8WKNqycomYmnS4LStg4-dT934D5u1_ZdnTi0O3khw9pGDRgR-3pjtOSwvGbkYAQFrN9-LC1HcpDkYJRJajfVv6ukL79z0eilcd9TBVAZTprG5ZudjxUuNWszfzvnFoSMOCbdzpGXudYaRycbSkEhrto1__XbNiaD7EReDOD5CqESwrP265yTsF8t94e9BA7Kr75HYFYcjQYvcTojT9U-Q7FJ9HB7mfeq0bLYLFTMevaupABe7peWVYBhtpOGqFEcT4ONHKllQbkf9MasTBGbm6sOc6KPjfq0KcRqgqsipYIE2VE9zQ4aUNkFMgYx0Y5SQN7VAqIJVphuw9dF1Olfb2DDwSZsNRumiSr_YMOoUWvbM9WF6oAgcht7aTTtVcvZKgVZAnkbgFJRJS4b6_vAxRXNA7BtuKvWT_Bn6r-vZE8KDSEtq_tec1Jnq7XEw70wDAXhS7rcKXw742_IGKhII_nlMtec21KNWFkN8gMVyHDsdj1NfAxMRXoK_JbqddpjHFUUvJD2Pdp0dLASmXY6sZbeaP6KgR0ZaRSU6QzTVZJVIetl6YQ8UTZH_MEr4A7uPLk5bWSbQzxq9p9LvpNoPOmn_iID_Q5QJyJI52TSEYwAA71DwmZtbo7DUY1KhV2gIiysGXa9CREzSpHfu7TIBiTSUY6W24bb18XPADQni7K4ZeYCaDLjjNSViVWJiZAdHIz1n5b07HUl1duUtgGuGY45DJNpaSLbZ714WYyFI9j4gIRBoX8H6s2V7BlyHBDzO5dJ-EzPVjmckiiqVZ5Da165LlpOxn46ETgGETKNeQDAQTRmGIukmsTDeQ8VAfUMzU0pcVlXB1eifYmzLecGbI8-SA_L6fYayzgQILsw1VUnDyTs4aKQDbz71DLBIVhZMJyKxrRY2kCMfRVaG3vqyDyDBlA8W-DMxCyyZ_Raqthb80oVDz0pEteLZXbq_Ylo3vR3XROyZEHn9FTU5NOTRKopBVJknBPyWhwXcb9sfjvICKkF_GTKDHozlC-P7QAFgucMNtg-tPrhCd_OGpPVv"
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
