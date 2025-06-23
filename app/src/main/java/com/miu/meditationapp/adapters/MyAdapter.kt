package com.miu.meditationapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.miu.meditationapp.databinding.ActivityVideosListBinding
import com.miu.meditationapp.models.Videos

class MyAdapter(
    var videosList: ArrayList<Videos>,
    val itemClickListener: ItemClickListener
) : RecyclerView.Adapter<MyAdapter.ListViewHolder>() {

    interface ItemClickListener {
        fun onItemClick(position: Int)
    }

    inner class ListViewHolder(private val binding: ActivityVideosListBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                itemClickListener.onItemClick(adapterPosition)
            }
        }

        fun bind(video: Videos) {
            binding.imageRecyclerView.setImageResource(video.image)
            binding.textRecyclerView1.text = video.title
            binding.textRecyclerView3.text = "Duration: ${video.duration}"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
        val binding = ActivityVideosListBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
        holder.bind(videosList[position])
    }

    override fun getItemCount(): Int {
        return videosList.size
    }
} 