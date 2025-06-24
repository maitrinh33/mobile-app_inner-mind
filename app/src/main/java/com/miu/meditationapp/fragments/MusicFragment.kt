package com.miu.meditationapp.fragments

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.appcompat.widget.SearchView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.miu.meditationapp.R
import com.miu.meditationapp.databinding.FragmentMusicBinding
import com.miu.meditationapp.databinding.EmptyStateMusicBinding
import com.miu.meditationapp.databases.SongEntity
import com.miu.meditationapp.services.MusicServiceRefactored
import com.miu.meditationapp.viewmodels.MusicViewModel
import com.miu.meditationapp.adapters.ModernSongAdapter
import com.miu.meditationapp.adapters.AdminSongAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.ViewModelProvider
import android.util.Log
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import android.content.ServiceConnection
import android.content.ComponentName
import android.os.IBinder
import kotlinx.coroutines.*

class MusicFragment : Fragment() {
    private var _binding: FragmentMusicBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MusicViewModel
    private lateinit var userSongAdapter: ModernSongAdapter
    private lateinit var adminSongAdapter: AdminSongAdapter
    private lateinit var prefs: SharedPreferences
    private var hasMigrated = false
    private lateinit var auth: FirebaseAuth
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { addSongFromUri(it) }
    }

    // Make this function public and add @JvmStatic annotation to make it accessible from XML
    companion object {
        private var fragmentInstance: MusicFragment? = null

        @JvmStatic
        fun onAddSongClick(view: View) {
            // Get the fragment instance from our stored reference
            fragmentInstance?.launchSongPicker()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MusicServiceRefactored.MusicBinder
            viewModel.setMusicService(binder?.getService())
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            viewModel.setMusicService(null)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMusicBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        
        // Store the fragment instance
        fragmentInstance = this
        
        // Check if user is logged in
        if (auth.currentUser == null) {
            Toast.makeText(context, "Please sign in to access your music library", Toast.LENGTH_SHORT).show()
            return binding.root
        }
        
        viewModel = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)).get(
            MusicViewModel::class.java)
        prefs = requireContext().getSharedPreferences("music_favorites", 0)
        
        // Verify database access
        viewModel.verifyDatabaseAccess()
        
        setupViews()
        observeViewModel()
        setupClickListeners()
        
        return binding.root
    }

    private fun setupClickListeners() {
        // Set up click listeners for both add song buttons
        binding.fabAddSong.setOnClickListener {
            launchSongPicker()
        }

        // Set click listener for empty state add song button
        binding.emptyStateLayout.btnEmptyStateAddSong.setOnClickListener {
            launchSongPicker()
        }
    }

    private fun setupViews() {
        setupRecyclerView()
        setupSearchView()
        
        // Show admin upload button if user is admin
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isAdmin.collect { isAdmin ->
                binding.fabAdminUpload.visibility = if (isAdmin) View.VISIBLE else View.GONE
            }
        }
        
        // Admin upload button click listener
        binding.fabAdminUpload.setOnClickListener {
            launchAdminSongPicker()
        }

        // Regular add song FAB click listener
        binding.fabAddSong.setOnClickListener {
            launchSongPicker()
        }
    }

    private fun setupRecyclerView() {
        // Setup user songs adapter (list view)
        userSongAdapter = ModernSongAdapter(
            onSongClick = { song -> onItemClick(song) },
            onMoreOptionsClick = { song -> showUserSongOptionsDialog(song) }
        )
        
        // Setup admin songs adapter (grid view)
        adminSongAdapter = AdminSongAdapter(
            onSongClick = { song -> onItemClick(song) },
            onMoreOptionsClick = { song -> showAdminSongOptionsDialog(song) }
        )
        
        // Setup user songs RecyclerView (list)
        binding.recyclerViewUserSongs.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = userSongAdapter
            
            // Add swipe-to-delete functionality
            val swipeHandler = object : ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            ) {
                override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
                
                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val position = viewHolder.bindingAdapterPosition
                    val song = userSongAdapter.songs[position]
                    when (direction) {
                        ItemTouchHelper.LEFT -> showDeleteConfirmationDialog(song)
                        ItemTouchHelper.RIGHT -> showEditSongDialog(song)
                    }
                }
            }
            ItemTouchHelper(swipeHandler).attachToRecyclerView(this)
        }
        
        // Setup admin songs RecyclerView (grid)
        binding.recyclerViewAdminSongs.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = adminSongAdapter
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.setSearchQuery(query ?: "")
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText ?: "")
                return true
            }
        })
    }

    private fun observeViewModel() {
        // Observe database access errors
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.databaseError.collectLatest { error ->
                error?.let {
                    Log.e("MusicFragment", "Database error", it)
                    Toast.makeText(context, "Error loading songs: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.songs.collectLatest { songs ->
                val userSongs = songs.filter { !it.isAdminSong }
                val adminSongs = songs.filter { it.isAdminSong }
                
                userSongAdapter.updateSongs(userSongs)
                adminSongAdapter.updateSongs(adminSongs)
                
                // Update visibility based on whether there are songs
                binding.apply {
                    userSongsSection.visibility = if (userSongs.isNotEmpty()) View.VISIBLE else View.GONE
                    adminSongsSection.visibility = if (adminSongs.isNotEmpty()) View.VISIBLE else View.GONE
                    emptyStateLayout.root.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
                }
                
                // Prefetch URLs for admin songs
                if (adminSongs.isNotEmpty()) {
                    viewModel.prefetchAdminSongUrls(adminSongs)
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.songCount.collectLatest { count ->
                updateSongCount(count)
            }
        }

        // Observe current playing song
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentPlayingSong.collectLatest { currentSong ->
                currentSong?.let { song ->
                    if (song.isAdminSong) {
                        // Clear loading state when song starts playing
                        adminSongAdapter.setLoading(song.id.toInt(), false)
                    }
                }
            }
        }

        // Observe playback errors
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.playbackError.collectLatest { error ->
                error?.let { (songId, message) ->
                    // Clear loading state on error
                    adminSongAdapter.setLoading(songId, false)
                    if (!message.contains("Cancelled")) { // Don't show error for cancelled songs
                        Toast.makeText(context, "Playback error: $message", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.apply {
            if (isEmpty) {
                emptyStateLayout.root.visibility = View.VISIBLE
                recyclerViewUserSongs.visibility = View.GONE
            } else {
                emptyStateLayout.root.visibility = View.GONE
                recyclerViewUserSongs.visibility = View.VISIBLE
            }
        }
    }

    private fun updateSongCount(count: Int) {
        binding.textSongCount.text = "$count songs"
    }

    override fun onResume() {
        super.onResume()
        // Clear any stale loading states
        adminSongAdapter.clearLoadingStates()
    }

    override fun onPause() {
        super.onPause()
    }

    private fun addSongFromUri(uri: Uri) {
        val context = requireContext()
        var title = "Unknown"
        var duration = "--:--"
        var artist = "Unknown Artist"
        var album = "Unknown Album"
        var fileSize = 0L
        
        // Check if user is logged in
        if (auth.currentUser == null) {
            Toast.makeText(context, "Please sign in to add songs", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get file metadata
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) title = cursor.getString(nameIndex)
                if (sizeIndex >= 0) fileSize = cursor.getLong(sizeIndex)
            }
        }
        
        // Extract audio metadata
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            val durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            duration = formatDuration(durationMs)
            
            // Try to get artist and album info
            artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
            
            // If title is still "Unknown", try to get it from metadata
            if (title == "Unknown") {
                title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "Unknown"
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not read song metadata", Toast.LENGTH_SHORT).show()
        } finally {
            mmr.release()
        }

        // Handle admin song upload
        if (viewModel.isAdmin.value) {
            try {
                // Show upload progress dialog
                val progressDialog = MaterialAlertDialogBuilder(context)
                    .setTitle("Uploading Admin Song")
                    .setMessage("Starting upload...")
                    .setCancelable(false)
                    .create()
                progressDialog.show()

                // Read the input stream before passing it to the upload function
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    progressDialog.dismiss()
                    Toast.makeText(context, "Could not read song file", Toast.LENGTH_SHORT).show()
                    return
                }

                val fileName = "admin_song_${System.currentTimeMillis()}.mp3"
                viewModel.uploadAdminSong(
                    inputStream = inputStream,
                    fileName = fileName,
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                    fileSize = fileSize,
                    onProgress = { progress ->
                        progressDialog.setMessage("Uploading: $progress%")
                    },
                    onSuccess = { song ->
                        progressDialog.dismiss()
                        Snackbar.make(binding.root, "Admin song added: ${song.title}", Snackbar.LENGTH_SHORT).show()
                    },
                    onError = { e ->
                        progressDialog.dismiss()
                        Toast.makeText(context, "Failed to upload admin song: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("MusicFragment", "Failed to upload admin song", e)
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read song file: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("MusicFragment", "Failed to read song file", e)
            }
            return
        }

        // Regular user song upload
        val internalUri = copyFileToInternalStorage(uri)
        if (internalUri == null) {
            Toast.makeText(context, "Could not access this file", Toast.LENGTH_SHORT).show()
            return
        }
        
        val song = SongEntity(
            title = title,
            duration = duration,
            uri = internalUri.toString(),
            artist = artist,
            album = album,
            fileSize = fileSize,
            userId = auth.currentUser!!.uid
        )
        
        viewModel.addSong(song)
        
        // Start playing the song immediately
        startPlayingSong(title, internalUri.toString(), duration)
        
        // Show success message
        Snackbar.make(binding.root, "Added: $title", Snackbar.LENGTH_SHORT).show()
    }
    
    private fun copyFileToInternalStorage(uri: Uri): Uri? {
        return try {
            // For content URIs, we need to take persistent permission
            if (uri.scheme == "content") {
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Continue anyway, might work without persistent permission
                }
            }
            
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream == null) {
                return null
            }
            
            val fileName = "song_${System.currentTimeMillis()}.mp3"
            val file = File(requireContext().filesDir, fileName)
            
            inputStream.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // Ensure the file is readable
            file.setReadable(true, false)
            
            Uri.fromFile(file)
        } catch (e: Exception) {
            // Return the original URI if copying fails
            uri
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun startPlayingSong(title: String, uri: String, duration: String) {
        // Use a consistent song ID based on the URI to avoid mismatches
        val songId = uri.hashCode().toString()
        
        val intent = Intent(requireContext(), MusicServiceRefactored::class.java).apply {
            action = MusicServiceRefactored.ACTION_PLAY_SONG
            putExtra("songId", songId)
            putExtra("title", title)
            putExtra("uri", uri)
            putExtra("duration", duration)
        }
        requireContext().startService(intent)
    }
    
    private fun testFileAccessibility(uriString: String) {
        try {
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                "file" -> {
                    // Just check if the file exists and is readable
                    val file = File(uri.path ?: "")
                    if (!file.exists() || !file.canRead()) {
                        Log.e("MusicFragment", "File does not exist or is not readable: $uriString")
                    }
                }
                "content" -> {
                    requireContext().contentResolver.openInputStream(uri)?.close()
                }
            }
        } catch (e: Exception) {
            Log.e("MusicFragment", "Error testing file accessibility: ${e.message}")
        }
    }
    
    private fun isMusicServiceRunning(): Boolean {
        // Instead of using deprecated getRunningServices, we'll check if our service is bound
        val intent = Intent(requireContext(), MusicServiceRefactored::class.java)
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                // Service is running
                requireContext().unbindService(this)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // Service has crashed or been killed
            }
        }

        return try {
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            true
        } catch (e: Exception) {
            Log.e("MusicFragment", "Error checking service state: ${e.message}")
            false
        }
    }

    private fun onItemClick(song: SongEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (song.isAdminSong) {
                    // Clear any existing loading states first
                    adminSongAdapter.clearLoadingStates()
                    // Set loading state for clicked song
                    adminSongAdapter.setLoading(song.id.toInt(), true)
                }
                viewModel.playSong(song)
            } catch (e: Exception) {
                if (song.isAdminSong) {
                    adminSongAdapter.setLoading(song.id.toInt(), false)
                }
                Toast.makeText(context, "Error playing song: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUserSongOptionsDialog(song: SongEntity) {
        val options = arrayOf("Edit", "Delete", "Add to Favorites", "Share")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(song.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditSongDialog(song)
                    1 -> showDeleteConfirmationDialog(song)
                    2 -> viewModel.toggleFavorite(song.id, !song.isFavorite)
                    3 -> shareSong(song)
                }
            }
            .show()
    }

    private fun showAdminSongOptionsDialog(song: SongEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            val isAdmin = viewModel.isAdmin.value
            val options = if (isAdmin) {
                arrayOf("Edit", "Delete", "Add to Favorites", "Share")
            } else {
                arrayOf("Add to Favorites", "Share")
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(song.title)
                .setItems(options) { _, which ->
                    when {
                        isAdmin && which == 0 -> showEditSongDialog(song)
                        isAdmin && which == 1 -> showDeleteConfirmationDialog(song)
                        isAdmin && which == 2 -> viewModel.toggleFavorite(song.id.toInt(), !song.isFavorite)
                        isAdmin && which == 3 -> shareSong(song)
                        !isAdmin && which == 0 -> viewModel.toggleFavorite(song.id.toInt(), !song.isFavorite)
                        !isAdmin && which == 1 -> shareSong(song)
                    }
                }
                .show()
        }
    }

    private fun showEditSongDialog(song: SongEntity) {
        val editText = android.widget.EditText(requireContext()).apply {
            setText(song.title)
            selectAll()
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Song Title")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = editText.text.toString().trim()
                if (newTitle.isNotEmpty() && newTitle != song.title) {
                    viewModel.updateSongTitle(song.id, newTitle)
                    Snackbar.make(binding.root, "Title updated", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(song: SongEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Song")
            .setMessage("Are you sure you want to delete '${song.title}'? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteSong(song)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSong(song: SongEntity) {
        viewModel.deleteSong(song)
        Snackbar.make(binding.root, "Deleted: ${song.title}", Snackbar.LENGTH_LONG)
            .setAction("Undo") {
                // Add the song back (you could implement undo functionality here)
                viewModel.addSong(song)
            }
            .show()
    }

    private fun shareSong(song: SongEntity) {
        try {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(song.uri))
                putExtra(Intent.EXTRA_TEXT, "Check out this song: ${song.title}")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Song"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not share song", Toast.LENGTH_SHORT).show()
        }
    }

    private fun migrateExistingSongs(songs: List<SongEntity>) {
        songs.forEach { song ->
            val uri = song.uri
            if (uri.startsWith("content://")) {
                try {
                    val originalUri = Uri.parse(uri)
                    val internalUri = copyFileToInternalStorage(originalUri)
                    if (internalUri != null) {
                        viewModel.updateSong(song.copy(uri = internalUri.toString()))
                    }
                } catch (e: Exception) {
                    Log.e("MusicFragment", "Failed to migrate song: ${e.message}")
                }
            }
        }
        hasMigrated = true
    }

    // Helper function to launch the song picker
    private fun launchSongPicker() {
        if (auth.currentUser == null) {
            Toast.makeText(context, "Please sign in to add songs", Toast.LENGTH_SHORT).show()
            return
        }
        pickAudioLauncher.launch("audio/*")
    }

    private fun launchAdminSongPicker() {
        if (!viewModel.isAdmin.value) {
            Toast.makeText(context, "Only admins can upload songs", Toast.LENGTH_SHORT).show()
            return
        }
        pickAudioLauncher.launch("audio/*")
    }

    override fun onStart() {
        super.onStart()
        // Bind to MusicService
        Intent(requireContext(), MusicServiceRefactored::class.java).also { intent ->
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        // Unbind from MusicService
        requireContext().unbindService(serviceConnection)
        viewModel.setMusicService(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        coroutineScope.cancel()
        _binding = null
    }
} 