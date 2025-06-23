package com.miu.meditationapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.miu.meditationapp.databases.SongEntity
import com.miu.meditationapp.databinding.ItemSongBinding
import java.text.SimpleDateFormat
import java.util.*

class ModernSongAdapter(
    private val onSongClick: (SongEntity) -> Unit,
    private val onSongLongClick: (SongEntity) -> Boolean,
    private val onFavoriteClick: (SongEntity, Boolean) -> Unit,
    private val onEditClick: (SongEntity) -> Unit,
    private val onDeleteClick: (SongEntity) -> Unit
) : RecyclerView.Adapter<ModernSongAdapter.SongViewHolder>() {

    var songs: List<SongEntity> = emptyList()
        private set

    inner class SongViewHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(song: SongEntity) {
            binding.apply {
                textTitle.text = song.title
                textDuration.text = song.duration
                textArtist.text = song.artist
                textAlbum.text = song.album
                
                // Format date added
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                textDateAdded.text = dateFormat.format(song.dateAdded)
                
                // Play count
                textPlayCount.text = "${song.playCount} plays"
                
                // Favorite button
                buttonFavorite.setImageResource(
                    if (song.isFavorite) android.R.drawable.btn_star_big_on
                    else android.R.drawable.btn_star_big_off
                )
                
                // Click listeners
                root.setOnClickListener { onSongClick(song) }
                root.setOnLongClickListener { onSongLongClick(song) }
                buttonFavorite.setOnClickListener { onFavoriteClick(song, !song.isFavorite) }
                buttonEdit.setOnClickListener { onEditClick(song) }
                buttonDelete.setOnClickListener { onDeleteClick(song) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(
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

    fun removeSong(song: SongEntity) {
        val position = songs.indexOf(song)
        if (position != -1) {
            val newList = songs.toMutableList()
            newList.removeAt(position)
            songs = newList
            notifyItemRemoved(position)
        }
    }
} 