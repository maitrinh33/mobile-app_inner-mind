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
import androidx.appcompat.widget.SearchView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.miu.meditationapp.R
import com.miu.meditationapp.databinding.FragmentMusicBinding
import com.miu.meditationapp.databases.SongEntity
import com.miu.meditationapp.services.MusicServiceRefactored
import com.miu.meditationapp.viewmodels.MusicViewModel
import com.miu.meditationapp.adapters.ModernSongAdapter
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

class MusicFragment : Fragment() {
    private var _binding: FragmentMusicBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MusicViewModel
    private lateinit var adapter: ModernSongAdapter
    private lateinit var prefs: SharedPreferences
    private var hasMigrated = false
    private lateinit var auth: FirebaseAuth
    
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
        
        setupRecyclerView()
        setupSearchView()
        observeViewModel()
        setupClickListeners()
        
        return binding.root
    }

    private fun setupClickListeners() {
        // Set up click listeners for both add song buttons
        binding.fabAddSong.setOnClickListener {
            launchSongPicker()
        }

        // Find the MaterialButton in the empty state layout and set its click listener
        binding.emptyStateLayout.findViewById<MaterialButton>(R.id.btnEmptyStateAddSong)?.setOnClickListener {
            launchSongPicker()
        }
    }

    private fun setupRecyclerView() {
        adapter = ModernSongAdapter(
            onSongClick = { song -> onItemClick(song) },
            onSongLongClick = { song -> showSongOptionsDialog(song); true },
            onFavoriteClick = { song, isFavorite -> 
                viewModel.toggleFavorite(song.id, isFavorite)
            },
            onEditClick = { song -> showEditSongDialog(song) },
            onDeleteClick = { song -> showDeleteConfirmationDialog(song) }
        )
        
        binding.recyclerViewSongs.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@MusicFragment.adapter
            
            // Add swipe-to-delete functionality
            val swipeHandler = object : ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            ) {
                override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
                
                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val position = viewHolder.bindingAdapterPosition // Use bindingAdapterPosition instead of deprecated adapterPosition
                    val song = this@MusicFragment.adapter.songs[position]
                    when (direction) {
                        ItemTouchHelper.LEFT -> showDeleteConfirmationDialog(song)
                        ItemTouchHelper.RIGHT -> showEditSongDialog(song)
                    }
                }
            }
            ItemTouchHelper(swipeHandler).attachToRecyclerView(this)
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
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.songs.collectLatest { songs ->
                adapter.updateSongs(songs)
                updateEmptyState(songs.isEmpty())
                
                // Migrate existing songs to internal storage (only once)
                if (songs.isNotEmpty() && !hasMigrated) {
                    migrateExistingSongs(songs)
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.songCount.collectLatest { count ->
                updateSongCount(count)
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.apply {
            if (isEmpty) {
                emptyStateLayout.visibility = View.VISIBLE
                recyclerViewSongs.visibility = View.GONE
            } else {
                emptyStateLayout.visibility = View.GONE
                recyclerViewSongs.visibility = View.VISIBLE
            }
        }
    }

    private fun updateSongCount(count: Int) {
        binding.textSongCount.text = "$count songs"
    }

    override fun onResume() {
        super.onResume()
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
        
        // Copy file to internal storage to avoid permission issues
        val internalUri = copyFileToInternalStorage(uri)
        if (internalUri == null) {
            Toast.makeText(context, "Could not access this file", Toast.LENGTH_SHORT).show()
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
        startPlayingSong(song.title, song.uri, song.duration)
        viewModel.incrementPlayCount(song.id)
    }

    private fun showSongOptionsDialog(song: SongEntity) {
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

    override fun onDestroyView() {
        super.onDestroyView()
        // Clear the fragment instance when the view is destroyed
        if (fragmentInstance == this) {
            fragmentInstance = null
        }
        _binding = null
    }
} 