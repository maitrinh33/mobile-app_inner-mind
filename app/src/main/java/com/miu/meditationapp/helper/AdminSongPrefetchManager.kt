package com.miu.meditationapp.helper

import android.content.Context
import com.miu.meditationapp.databases.SongEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AdminSongPrefetchManager(
    private val context: Context,
    private val cacheHelper: AdminSongCacheHelper
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var prefetchJob: Job? = null

    // StateFlow to notify which songs are ready (cached)
    private val _readySongIds = MutableStateFlow<Set<Int>>(emptySet())
    val readySongIds: StateFlow<Set<Int>> = _readySongIds

    fun prefetchNextSongs(songs: List<SongEntity>, startIndex: Int, count: Int = 2) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            val toPrefetch = songs.drop(startIndex + 1).take(count)
            toPrefetch.forEach { song ->
                if (!cacheHelper.isSongCached(song)) {
                    try {
                        cacheHelper.cacheSong(song)
                        markSongReady(song.id)
                        // Optionally notify UI that song is ready
                    } catch (e: Exception) {
                        // Log or handle error
                    }
                } else {
                    markSongReady(song.id)
                }
            }
        }
    }

    private fun markSongReady(songId: Int) {
        _readySongIds.value = _readySongIds.value + songId
    }

    fun stopPrefetching() {
        prefetchJob?.cancel()
    }

    fun cleanup() {
        scope.cancel()
    }

    // Optionally, allow manual refresh of ready state (e.g., on app start)
    fun refreshReadySongs(songs: List<SongEntity>) {
        val ready = songs.filter { cacheHelper.isSongCached(it) }.map { it.id }.toSet()
        _readySongIds.value = ready
    }
} 