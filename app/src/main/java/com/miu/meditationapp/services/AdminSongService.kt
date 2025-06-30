package com.miu.meditationapp.services

import android.content.Context
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

@Singleton
class AdminSongService @Inject constructor(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val storage: FirebaseStorage
) {
    private val adminSongsRef = database.getReference("admin_songs").apply {
        // Enable persistence for offline access
        keepSynced(true)
    }
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // REMOVED: database.setPersistenceEnabled(true)
    }

    // Check if current user is admin
    suspend fun isCurrentUserAdmin(): Boolean {
        val currentUser = auth.currentUser ?: return false
        val userRef = database.getReference("users").child(currentUser.uid).child("isAdmin")
        return try {
            val snapshot = userRef.get().await()
            snapshot.exists() && snapshot.getValue(Boolean::class.java) == true
        } catch (e: Exception) {
            false
        }
    }

    // Verify database rules
    suspend fun verifyDatabaseRules() {
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
        uri: Uri,
        fileName: String,
        title: String,
        artist: String,
        album: String,
        duration: String,
        fileSize: Long,
        onSuccess: (SongEntity) -> Unit,
        onError: (Exception) -> Unit
    ) {
        coroutineScope.launch {
            try {
                Log.d("AdminSongService", "Starting admin song upload: $fileName")
                if (!isCurrentUserAdmin()) {
                    throw SecurityException("Only admins can upload admin songs")
                }

                // Open InputStream right before upload
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Could not open input stream from URI")

                // Upload to Dropbox first
                val dropboxPath = "/admin_songs/$fileName"
                Log.d("AdminSongService", "Uploading to Dropbox path: $dropboxPath")

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
            // val dropboxPath = songSnapshot.child("dropboxPath").getValue(String::class.java) // Unused
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

    fun getAdminSongs(): Flow<List<SongEntity>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val songs = snapshot.children.mapNotNull { child ->
                    val map = child.value as? Map<*, *> ?: return@mapNotNull null
                    SongEntity(
                        title = map["title"] as? String ?: "",
                        artist = map["artist"] as? String ?: "",
                        album = map["album"] as? String ?: "",
                        duration = map["duration"] as? String ?: "",
                        uri = map["uri"] as? String ?: "",
                        fileSize = (map["fileSize"] as? Long) ?: 0L,
                        isAdminSong = true,
                        firebaseId = child.key,
                        userId = "admin",
                        dateAdded = Date((map["dateAdded"] as? Long) ?: 0L)
                    )
                }
                trySend(songs)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        adminSongsRef.addValueEventListener(listener)
        awaitClose { adminSongsRef.removeEventListener(listener) }
    }
} 