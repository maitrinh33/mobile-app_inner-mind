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

object DropboxHelper {
    // Note: This is a long-lived access token that doesn't expire
    private const val ACCESS_TOKEN = "sl.u.AFzKjE1LWXUCtCt976t-Td6xMlzS8UEaj-HWBHnF4Imd6VFMgxQfci6Z5upMxGLVRgXAQ2aYL8rrKWaVw7msm4e3MF6nDa0YVVjOPEtQZKbgRDorhWnwGL9u1RDXlUSifTQkAPcBnqfQJ6OPdTSOiXZNHbb0mHnZzdW0UO1P7WsV5kEe-Npw-uDWmHe7zZe6ixqqD1FGRUgCsaclYcv9Cjh9v2oIjLq1lm91DWG-jn0NWQXML5O2AECWtCwlXFvP3eInXc3DTfR_7SPZNJ4o1Z9Dg8guUggpOqBPK8CDMDDcaKP1Fd1SXLbRre5CHhx3Qu0r7VdKm-SICRbSz95Vj4iqMdhDRkgASc7WsQPH4iX8aDy4Qat5f0aGx840LvM_3etxKVpkjPB30C5RgEIwCfXYcc3K3EyonkddNYjemldlrNACKlvbQ7HXOxbjrLWw0_0M4afUmfOtG2mMTPcHb-YMY8AXzAqiEdNHQlW_cj7basxT5eWWySVYAzRL0UxaU27WoPs_WK2glIF_tZJ-ziLNFSxtHg0GIcz4wHAWnrYAtt2lkQQUc-2P_1zaBAniQAVNg8UIDwEh62f4O0WCUpKZycyypyhmhBRx5aVUl0meOU4S5UlsTOn7I2-3LMHeysZ_9lkZPimVaUdMudH8RWQOLrnRZpSpzBFMDbUroNLxYM5gtX32EJ0VDKH-jhjgiLmtU4PYeKw8HjTwlE7JmfiSmUBi6nDfEn-z5o3WSDj4qeMaylScoRn-u6gseVi2d1njIWwZbMhE9TNw7fp7Wx3jHd5auz9cQxwC1BHiXhROm3FYuze61FdnVMtgD7OeTYHnNk1t5uFQZKlQQ4HafBubnl4brauP4fpE2w9XesfwPWKY3h71lDvtGln6p8_VhUy_XcLroWvfDi_vzdoa7dBVCixUixR1_7D076E7wh2LVnoC7Zugd2_RnXn_BZIUGW5nBBYcD-MnETmpjarSnuxzz7EX-0CZFwQxy2BPM1ZIblRjNoyJCZORN80cvjacnB4zNAXdqs_aOGmB76dwjz18MABOtbhgvOjI49jL_uJfK_gcUH3I0JXW9F3nbarCWCxc_Es-pRagWPOreQPcyKeMoRsoAE9tj15KebqMeMpgPH0mfQhlsKlDgLYaSD3rs4xHqWP7AUUYVbtZZHhz4PbEJt2sfKH5mpftpXaKBP7EvVnNXSu820vFDUyxl7DIwJsLf68tJbDJi5GXwTuFy3ydhq5LEFr13n97KlnSWo54I1l7PAteSzVJoMHgROTV3ycuVjn5hF9kpKxS9nUTMBAT1eFA2GpKSFNPuEFuDtAV-U_eQpYNXR1BjANffTsOXED4fqtPP-ZMzBsnJW7hpeAFO3sTkN39UbfRwPp9aOF1pgwPJh-yM4B1ekrOg8BClJcrWdxXltIp4ZMAKbe9irnxH1ZU_R4WuR32ZBK006lESPUzYG___GDji6iVrfJbYmSylNqrFU0mE5az7H-QW4om" 
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
            } catch (e: Exception) {
                onError(e)
                return
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
}
