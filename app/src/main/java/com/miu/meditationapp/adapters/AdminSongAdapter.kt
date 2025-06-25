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
    private val onSongClick: (SongEntity) -> Unit,
    private val onMoreOptionsClick: (SongEntity, View) -> Unit
) : RecyclerView.Adapter<AdminSongAdapter.SongViewHolder>() {

    var songs: List<SongEntity> = emptyList()
        private set

    private val loadingStates = mutableMapOf<Int, Boolean>()

    inner class SongViewHolder(val binding: ItemAdminSongBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(song: SongEntity) {
            binding.apply {
                textTitle.text = song.title
                textDuration.text = song.duration
                textArtist.text = song.artist
                textPlayCount.text = "${song.playCount} plays"

                // Set loading state
                loadingOverlay.visibility = if (loadingStates[song.id.toInt()] == true) View.VISIBLE else View.GONE

                // Click listeners
                root.setOnClickListener {
                    if (loadingStates[song.id.toInt()] != true) {
                        setLoading(song.id.toInt(), true)
                        onSongClick(song)
                    }
                }
                buttonMore.setOnClickListener { onMoreOptionsClick(song, buttonMore) }
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

    fun setLoading(songId: Int, isLoading: Boolean) {
        loadingStates[songId] = isLoading
        val position = songs.indexOfFirst { it.id.toInt() == songId }
        if (position != -1) {
            notifyItemChanged(position)
        }
    }

    fun clearLoadingStates() {
        loadingStates.clear()
        notifyDataSetChanged()
    }
} 