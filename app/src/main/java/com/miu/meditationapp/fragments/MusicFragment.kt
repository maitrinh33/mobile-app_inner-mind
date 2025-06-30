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
import androidx.fragment.app.viewModels
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
import com.miu.meditationapp.data.repositories.MusicRepository
import com.miu.meditationapp.helper.DialogHelper
import com.miu.meditationapp.helper.MenuHelper
import com.miu.meditationapp.helper.FilePickerHelper
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
import dagger.hilt.android.AndroidEntryPoint
import com.miu.meditationapp.helper.MusicStateManager
import com.miu.meditationapp.helper.UploadManager
import com.miu.meditationapp.helper.RecyclerViewManager
import com.miu.meditationapp.helper.PlaybackManager
import com.miu.meditationapp.helper.DialogManager
import javax.inject.Inject
import kotlinx.coroutines.flow.combine

@AndroidEntryPoint
class MusicFragment : Fragment() {
    private var _binding: FragmentMusicBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MusicViewModel by viewModels()
    private lateinit var userSongAdapter: ModernSongAdapter
    private lateinit var adminSongAdapter: AdminSongAdapter
    private lateinit var prefs: SharedPreferences
    private var hasMigrated = false
    @Inject
    lateinit var auth: FirebaseAuth
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var stateManager: MusicStateManager
    private lateinit var uploadManager: UploadManager
    private lateinit var recyclerViewManager: RecyclerViewManager
    private lateinit var playbackManager: PlaybackManager
    private lateinit var dialogManager: DialogManager

    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            FilePickerHelper.persistUriPermission(requireContext(), it)
            viewLifecycleOwner.lifecycleScope.launch {
                handleSelectedAudioFile(it)
            }
        }
    }

    companion object {
        private var fragmentInstance: MusicFragment? = null

        @JvmStatic
        fun onAddSongClick(view: View) {
            fragmentInstance?.launchSongPicker()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MusicServiceRefactored.MusicBinder
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMusicBinding.inflate(inflater, container, false)
        initializeManagers()
        checkAuthAndSetup()
        return binding.root
    }

    private fun initializeManagers() {
        stateManager = MusicStateManager(binding)
        uploadManager = UploadManager(requireContext(), viewModel, auth)
        recyclerViewManager = RecyclerViewManager(requireContext(), binding, viewModel)
        playbackManager = PlaybackManager(
            requireContext(),
            viewModel,
            viewLifecycleOwner.lifecycleScope,
            recyclerViewManager,
            uploadManager
        )
        dialogManager = DialogManager(
            requireContext(),
            viewModel,
            viewLifecycleOwner.lifecycleScope,
            binding.root,
            auth
        )
        fragmentInstance = this
    }

    private fun checkAuthAndSetup() {
        if (auth.currentUser == null) {
            dialogManager.showError("Please sign in to access your music library")
            return
        }

        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        setupClickListeners()
        setupRecyclerViews()
        setupSearchView()
        setupAdminVisibility()
    }

    private fun setupClickListeners() {
        binding.apply {
            fabAddSong.setOnClickListener { launchSongPicker() }
            emptyStateLayout.btnEmptyStateAddSong.setOnClickListener { launchSongPicker() }
            fabAdminUpload.setOnClickListener { launchAdminSongPicker() }
        }
    }

    private fun setupRecyclerViews() {
        recyclerViewManager.setupRecyclerViews(
            onSongClick = playbackManager::handleSongClick,
            onMoreOptionsClick = dialogManager::showSongOptionsMenu,
            onSwipeToDelete = dialogManager::showDeleteConfirmationDialog,
            onSwipeToEdit = dialogManager::showEditSongDialog
        )
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                return true
            }
        })
    }

    private fun setupAdminVisibility() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isAdmin.collect { isAdmin ->
                stateManager.updateAdminButtonVisibility(isAdmin)
            }
        }
    }

    private fun observeViewModel() {
        observeAdminState()
        observeSongState()
        observeErrors()
    }

    private fun observeAdminState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isAdmin.collect { isAdmin ->
                stateManager.updateAdminButtonVisibility(isAdmin)
            }
        }
    }

    private fun observeSongState() {
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                viewModel.songs,
                viewModel.adminSongs
            ) { userSongs, adminSongs ->
                Pair(userSongs.filter { !it.isAdminSong }, adminSongs)
            }.collectLatest { (userSongs, adminSongs) ->
                recyclerViewManager.updateAdapters(userSongs, adminSongs)
                stateManager.updateSongListsVisibility(userSongs + adminSongs)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.songCount.collectLatest { count ->
                stateManager.updateSongCount(count)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentPlayingSong.collectLatest { song ->
                song?.takeIf { it.isAdminSong }?.let {
                    recyclerViewManager.clearLoadingStates()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.playbackState.collectLatest { state ->
                recyclerViewManager.setCurrentPlayingAdminSong(state.songId)
                if (state.isPlaying) {
                    recyclerViewManager.stopLoading()
                }
            }
        }
    }

    private fun observeErrors() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.databaseError.collectLatest { error ->
                error?.let {
                    dialogManager.showError("Error loading songs: ${it.message}")
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.playbackError.collectLatest { error ->
                error?.let { (songId, message) ->
                    recyclerViewManager.clearLoadingStates()
                    if (!message.contains("Cancelled")) {
                        dialogManager.showError("Playback error: $message")
                    }
                }
            }
        }
    }

    private suspend fun handleSelectedAudioFile(uri: Uri) {
        if (auth.currentUser == null) {
            dialogManager.showError("You must be logged in to add a song.")
            return
        }
        
        if (auth.currentUser == null) {
            dialogManager.showError("Please sign in to add songs")
            return
        }
        playbackManager.handleSelectedAudioFile(uri, viewModel.isAdmin.value) { message ->
            dialogManager.showError(message)
        }
    }

    private fun launchSongPicker() {
        FilePickerHelper.launchSongPicker(requireContext(), auth, pickAudioLauncher)
    }

    private fun launchAdminSongPicker() {
        FilePickerHelper.launchAdminSongPicker(requireContext(), viewModel.isAdmin.value, pickAudioLauncher)
    }

    override fun onStart() {
        super.onStart()
        Intent(requireContext(), MusicServiceRefactored::class.java).also { intent ->
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        requireContext().unbindService(serviceConnection)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        coroutineScope.cancel()
        _binding = null
    }
} 