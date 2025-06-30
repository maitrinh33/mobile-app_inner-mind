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
"sl.u.AF1AHdfAbG5ebjubAppKc8I5qlAEvhA8L9c4LVbHmCaoH1hNZhlWox6dl-Kc9dCMjATC-U0yyL3Otm50gfJEfpFmm7p1kflxO362mZOD2rjfGEH7Sh6Wkzh4osWF-_94zOCa-KzvIjFogc_xdgeLkT8IO1sT0bZBpYZcOzhY8XRTS951ZYEnqc-293jd4wxEsgcc2sHPLoUSk4qhgIfXf7JdG7kVYxgLbiEqPaVjjzRoMpf78_vtSpU1TfWRgQ7ydFydK1RYvAhOrIPi1jt4ELV5ztE1wDkenroLTvGcaFqTkR0GoqfLdjf5nLVfT6cip__8JxB7ZLppyYHxeXcaZEoRlb7Csy4EZNfPJt7X3mkc7DFGOhqg6_DxJ645qgC1DItJVsGLTluA7U6LOfLcbQZnDFsmoVbkZty2fNyCGU0aRP0tIIm6iYJTeYU2NOSrjkezi0nn2PtQXDkMlFDZEex-Jys-Kg6qCcz-7SaY7sfn6eAcxbBpFl6ecG2E5HhUEyvk0EvV5pSpeL7pdyylBQIGQvvB3CyvgUiBYEWgjiCFW3lgkDXRvP76FU1gKFmyUWqr1TTFjD1e5Vq9IKLkPrsNRdfhpwtKVYOwjCXTCXewPiezrP2mK86hNKwKLYQ1xxQOSaH91SReeYQmxloIXDowv6XoBixskkmKnlASAB3jPEo-WpiVDIRDuT1D4hEKFfyPndT-LGavZ1Gy0mMd6ZXMAtApUAfXz5zi3BESs2Ch9ZVl-K44sDaMpQhsyVZdDWWkdW4GmtQDBjkAQBMso3qCOwRXa7Jte12iX8sKpINx0PMeU3usTZRztv_gTNQ6nqtdZGpBcpBQP0R5_yDvHYev-VR3wQx59LGMvMjIvJWhkyeF0nSixHMnuR5CtXySn9iY6yJGaN3_DUYJsEeLeZvYq7FkoFqjDjRerrrxnWCXgt4z-Wf7UKx57jpjdp_iA0uT9IWOHRZuavvfJoJzrFLxyK3CIPbgx-fSy17MZ25VSOoCNkajj6nT7EH9IhgNzFUm1Mn8kJXaOLGojSli3CYGV2ulqjVXv7CxPyT6zVxS8rY1HmR79BZBipJBKDT9lLTstrtXYX3QRL5CPC3f-BJMhYLdIhmMcy9WmDc-7989gLJkrm3BxE9zuDayatEuRzl_SKnNwJHms1N7w1YQDJdeuuOBssghJuoBWi9Rwvp-g8-mxweyqaHenxtCqE2FclYUIEMOVSCk9JyihSKM4Esk1Z1nBOhRfSVc42PB4UtTWgp0_0nkuZMTAjZ4fOL7M7cfDg6UGaTVZC9H1t8blOe975223FOWQxc1C7kqmYvx33nKSPVdlqlFabGzZXg2T1AqR0U7i262vFysp3L8_DOQKjqhsB2_ADP4oeo_duJBEkxuQEofuOGDN8yK612CrwGxfNrzvPWgLsq2btASruKkM__iaAYpYANcW_m7RtF7HJIRj5A2pR7Q1nvKMMpQ8Vm2uEU2R11qeP6fBAenOMi2"
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
