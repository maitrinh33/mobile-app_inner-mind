package com.miu.meditationapp.services

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.miu.meditationapp.databases.SongEntity
import com.miu.meditationapp.helper.DropboxHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.InputStream
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class AdminSongService {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val adminSongsRef = database.getReference("admin_songs").apply {
        // Enable persistence for offline access
        keepSynced(true)
    }
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Enable Firebase persistence
        try {
            database.setPersistenceEnabled(true)
        } catch (e: Exception) {
            Log.w("AdminSongService", "Firebase persistence already enabled")
        }

        // Initialize admin users in Firebase if not exists
        initializeAdminUsers()
    }

    private fun initializeAdminUsers() {
        val adminUids = listOf(
            "cTWZcSFUnfOXNf8yWKdsIlvSgPx2" // Your admin UID
        )
        
        val adminsRef = database.getReference("admins")
        for (uid in adminUids) {
            adminsRef.child(uid).setValue(true)
                .addOnFailureListener { e ->
                    Log.e("AdminSongService", "Failed to initialize admin user: $uid", e)
                }
        }
    }

    // Check if current user is admin
    fun isCurrentUserAdmin(): Boolean {
        val currentUser = auth.currentUser
        val adminUids = listOf(
            "cTWZcSFUnfOXNf8yWKdsIlvSgPx2" // Your admin UID
        )
        val isAdmin = currentUser?.uid in adminUids
        Log.d("AdminSongService", "Checking admin status for user ${currentUser?.uid}: $isAdmin")
        return isAdmin
    }

    // Verify database rules
    fun verifyDatabaseRules() {
        // Test read access
        adminSongsRef.limitToFirst(1).get()
            .addOnSuccessListener {
                Log.d("AdminSongService", "Successfully read admin songs")
            }
            .addOnFailureListener { e ->
                Log.e("AdminSongService", "Failed to read admin songs. Check database rules", e)
            }
    }

    // Upload song to both Dropbox and Firebase
    fun uploadAdminSong(
        inputStream: InputStream,
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
        coroutineScope.launch {
            try {
                Log.d("AdminSongService", "Starting admin song upload: $fileName")
                if (!isCurrentUserAdmin()) {
                    throw SecurityException("Only admins can upload admin songs")
                }

                // Upload to Dropbox first
                val dropboxPath = "/admin_songs/$fileName"
                Log.d("AdminSongService", "Uploading to Dropbox path: $dropboxPath")

                // Upload to Dropbox
                suspendCoroutine<Unit> { continuation ->
                    DropboxHelper.uploadFile(
                        inputStream = inputStream,
                        dropboxPath = dropboxPath,
                        onSuccess = { continuation.resume(Unit) },
                        onError = { e -> continuation.resumeWithException(e) }
                    )
                }

                Log.d("AdminSongService", "Successfully uploaded to Dropbox, getting shared link")
                
                // Get shared link from Dropbox
                val songUri = DropboxHelper.getSharedLink(dropboxPath)
                Log.d("AdminSongService", "Got Dropbox shared link: $songUri")

                if (songUri.isNotEmpty()) {
                    // Create song entry in Firebase
                    val songMap = hashMapOf(
                        "title" to title,
                        "artist" to artist,
                        "album" to album,
                        "duration" to duration,
                        "uri" to songUri,
                        "fileSize" to fileSize,
                        "dateAdded" to Date().time,
                        "uploadedBy" to auth.currentUser?.uid,
                        "isAdminSong" to true,
                        "dropboxPath" to dropboxPath
                    )

                    Log.d("AdminSongService", "Creating Firebase entry with data: $songMap")

                    // Push to Firebase and get the key
                    val newSongRef = adminSongsRef.push()
                    
                    // Use await() for Firebase operation
                    newSongRef.setValue(songMap).await()
                    Log.d("AdminSongService", "Successfully saved to Firebase with key: ${newSongRef.key}")

                    // Create SongEntity after successful Firebase save
                    val songEntity = SongEntity(
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        uri = songUri,
                        fileSize = fileSize,
                        isAdminSong = true,
                        firebaseId = newSongRef.key,
                        userId = "admin", // Special userId for admin songs
                        dateAdded = Date()
                    )

                    withContext(Dispatchers.Main) {
                        onSuccess(songEntity)
                    }
                } else {
                    throw Exception("Failed to get Dropbox shared link")
                }
            } catch (e: Exception) {
                Log.e("AdminSongService", "Error in uploadAdminSong", e)
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    // Delete admin song
    suspend fun deleteAdminSong(songEntity: SongEntity) {
        if (!isCurrentUserAdmin()) {
            throw SecurityException("Only admins can delete admin songs")
        }

        songEntity.firebaseId?.let { firebaseId ->
            // Get the song data to find Dropbox path
            val songSnapshot = adminSongsRef.child(firebaseId).get().await()
            val dropboxPath = songSnapshot.child("dropboxPath").getValue(String::class.java)
            
            // Delete from Firebase first
            adminSongsRef.child(firebaseId).removeValue().await()
            
            // Then try to delete from Dropbox if path exists
            // Note: We're keeping the file in Dropbox for now as other users might be using it
            // Consider implementing reference counting if needed
        }
    }

    // Update admin song metadata
    suspend fun updateAdminSong(songEntity: SongEntity) {
        if (!isCurrentUserAdmin()) {
            throw SecurityException("Only admins can update admin songs")
        }

        songEntity.firebaseId?.let { firebaseId ->
            val updates = hashMapOf(
                "title" to songEntity.title,
                "artist" to songEntity.artist,
                "album" to songEntity.album
            )
            adminSongsRef.child(firebaseId).updateChildren(updates.toMap()).await()
        }
    }

    fun cleanup() {
        coroutineScope.cancel()
    }
} 