package com.miu.meditationapp.databases

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val title: String,
    val duration: String,
    val uri: String,
    val isFavorite: Boolean = false,
    val dateAdded: Date = Date(),
    val lastPlayed: Date? = null,
    val playCount: Int = 0,
    val fileSize: Long = 0L,
    val artist: String = "Unknown Artist",
    val album: String = "Unknown Album"
) 