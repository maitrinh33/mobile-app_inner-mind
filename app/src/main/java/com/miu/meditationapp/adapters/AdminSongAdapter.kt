package com.miu.meditationapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.miu.meditationapp.databases.SongEntity
import com.miu.meditationapp.databinding.ItemAdminSongBinding
import java.text.SimpleDateFormat
import java.util.*

class AdminSongAdapter(
    private val currentUserId: String,
    private val onSongClick: (SongEntity) -> Unit,
    private val onMoreOptionsClick: (SongEntity, View) -> Unit
) : RecyclerView.Adapter<AdminSongAdapter.SongViewHolder>() {

    var songs: List<SongEntity> = emptyList()
        private set

    private var loadingSongId: Int? = null
    private var currentPlayingSongId: Int? = null
    private var readySongIds: Set<Int> = emptySet()

    inner class SongViewHolder(val binding: ItemAdminSongBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(song: SongEntity) {
            binding.apply {
                textTitle.text = song.title
                textDuration.text = formatDuration(song.duration)
                textArtist.text = song.artist
                textPlayCount.text = "${song.playCount} plays"

                // Set loading state: only show if this is the loading song and not the currently playing song
                loadingOverlay.visibility = if (song.id.toInt() == loadingSongId && song.id.toInt() != currentPlayingSongId) View.VISIBLE else View.GONE

                // Show ready indicator if song is ready for instant playback
                readyIndicator.visibility = if (readySongIds.contains(song.id)) View.VISIBLE else View.GONE

                // Click listeners
                root.setOnClickListener {
                    android.util.Log.d("AdminSongClick", "Clicked admin song: ${song.id}, loadingSongId=$loadingSongId")
                    setOnlyLoading(song.id.toInt())
                    onSongClick(song)
                }
                moreOptionsButton.setOnClickListener { onMoreOptionsClick(song, moreOptionsButton) }

                // Logic to determine which menu to show will be in the fragment/activity
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemAdminSongBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(songs[position])
    }

    override fun getItemCount() = songs.size

    fun updateSongs(newSongs: List<SongEntity>) {
        songs = newSongs
        notifyDataSetChanged()
    }

    fun setOnlyLoading(songId: Int) {
        loadingSongId = songId
        notifyDataSetChanged()
    }

    fun stopLoading() {
        if (loadingSongId != null) {
            loadingSongId = null
            notifyDataSetChanged()
        }
    }

    fun clearLoadingStates() {
        loadingSongId = null
        notifyDataSetChanged()
    }

    fun setCurrentPlayingSongId(songId: Int?) {
        currentPlayingSongId = songId
        notifyDataSetChanged()
    }

    fun updateReadySongIds(readyIds: Set<Int>) {
        readySongIds = readyIds
        notifyDataSetChanged()
    }

    private fun formatDuration(duration: String): String {
        return try {
            val millis = duration.toLong()
            val minutes = (millis / 1000) / 60
            val seconds = (millis / 1000) % 60
            String.format("%d:%02d", minutes, seconds)
        } catch (e: NumberFormatException) {
            "N/A"
        }
    }
} 