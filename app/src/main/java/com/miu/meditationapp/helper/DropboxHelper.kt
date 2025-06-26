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

object DropboxHelper {
    // Note: This is a long-lived access token that doesn't expire
    private const val ACCESS_TOKEN =
        "sl.u.AF1LxOWtRZ6NZ_OUFK9VI2V5_ppJhuD5PoFq9cTxXYtoD2zsslrpe7XEkVDN9jLBKUmkLIfRgic7ioDWqDFgDyXa7UkFHITX2yAqKBG0-vKxsdCInBlQIAsA3TcJ22CqnGOAiy5oSD2l967GGRhZHHnFVDOph3Q_3pwkSu-tPAlzEv6isPI1Zl1kuAeBMTJeMz55s1g166-naTOvUuwAdO-cPUbQ23zs6UETit9S6qHDRkEBy_3QBMAwrtsJGS_h3wB2q0bU3eQbNx1oRVN1PoS_xg7wwwCdF4BBE58dMqGwpOedbTSm9WOhPkYJJry4dstog_0mT6svJQphOCu15l95VSK_tWFXGqgHlqRRMv0vzGJponeUYDNaLkEqQU9ftmzojwYnEs2QBAR5kdV5R2FYn68Tw7VSXBVITloiwvB7iKIyUQdaOEVYlafZeEnP7LMNelxRQQR970GSB_xRq0y_R9HgjG-Bv3UZcAG-0GL2qJaqHMHnLL0Fd2o0M4QoqruOmXM_jVXIHWYp7nlHkE3rdm9E72Qk-BP-iGJZppKxBUbM9T1ZliOwrBuEx5U4LIrF5Ll1L9oNTkLDmfiiXHXw9Tsf9c5KlZbmMzNPkqepvXcUZNGYCI7wOgDM0D9GPz8d1PJVDWob7-82J5Ee4DeNDE15OdTBtK0EDqNuCBsDYaQ2eYBkbAVF4wA-bocGPGmY9chy0jMdvwWzocAJFwI3Y4J1QfgPSZgTuKn8QtZ2iYZI-QEbn-JsZvVjRUKTIVEeQCFWrJrnbqTiULcMtrz-cXPT843xNlZONcTnxvVDXLOXFyF7_oYP9r8I3j5hI9zfC9Lku8GkpfRjkZp03rW96cNARglz53CnDdJh85qQRNct34QTu0bYkoO04i9NzVYEgwAFympGSZgMjhK4E_MOjLkR4C3EmFY92jQ81O38AeMB4j1ItdCJDae6avjheacxpLfgWSNpT6TC0qEXbSdxatrVipXsdhgWQqyRMQFnfm4EcBcsaj3d_aU2FY2omhtbZ_19p8pO4v9FGjtVbpmxnhxqGyfqhOdxlz60bgW1iQ4zL2YSUzdpm4N6zYQG0UOaV5Czbnp-q-WJnRqikJUoP5u2g_PHa3saCyrf5DNpKKuvXr_sKNI50F3Z9FBOFDf2d13qqInbioLuNojin7yIylz24uuH0GTNZ0KWHJvY_LhJfHUsx8PyiMwQoICj0KX3b2x81GU-WN6iD0C91P46cVR8nsolt1UZhDtXv052qyV9wIrBzdBWVCQQ8MaBGktZRGTaIZ9ci6W79suGrAmlgZdiYKdkKp8-Z_11zpxnLas3bZ5TO6oZjdgNUzAn4TJ-sPlehijjhcI2g2zbzhKZUDV6PuGhX2RunD0MlmY59z6UJGrL8Dl88TIqXNmkrJAEZwBfMpZz70uf-EchzOydoRyFxdsYobVy4R2q_2xbOSPRX_Jr3OkXcu7-ptSC5AbFn4pKWoQnmpGFeV7mlBjG"
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
}
