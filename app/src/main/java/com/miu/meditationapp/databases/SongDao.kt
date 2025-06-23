package com.miu.meditationapp.databases

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: SongEntity): Long

    @Query("SELECT * FROM songs WHERE userId = :userId ORDER BY dateAdded DESC")
    fun getAllSongs(userId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE userId = :userId AND isFavorite = 1 ORDER BY dateAdded DESC")
    fun getFavoriteSongs(userId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE userId = :userId AND (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%') ORDER BY dateAdded DESC")
    fun searchSongs(userId: String, query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE userId = :userId ORDER BY playCount DESC LIMIT 10")
    fun getMostPlayedSongs(userId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE userId = :userId ORDER BY lastPlayed DESC LIMIT 10")
    fun getRecentlyPlayedSongs(userId: String): Flow<List<SongEntity>>

    @Update
    suspend fun update(song: SongEntity)

    @Query("UPDATE songs SET title = :newTitle WHERE id = :songId AND userId = :userId")
    suspend fun updateTitle(songId: Int, userId: String, newTitle: String)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId AND userId = :userId")
    suspend fun updateFavorite(songId: Int, userId: String, isFavorite: Boolean)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayed = :lastPlayed WHERE id = :songId AND userId = :userId")
    suspend fun incrementPlayCount(songId: Int, userId: String, lastPlayed: java.util.Date)

    @Delete
    suspend fun delete(song: SongEntity)

    @Query("DELETE FROM songs WHERE id = :songId AND userId = :userId")
    suspend fun deleteById(songId: Int, userId: String)

    @Query("DELETE FROM songs WHERE uri = :uri AND userId = :userId")
    suspend fun deleteByUri(uri: String, userId: String)

    @Query("SELECT COUNT(*) FROM songs WHERE userId = :userId")
    suspend fun getSongCount(userId: String): Int

    @Query("SELECT COUNT(*) FROM songs WHERE userId = :userId AND isFavorite = 1")
    suspend fun getFavoriteCount(userId: String): Int

    @Query("SELECT * FROM songs WHERE id = :songId AND userId = :userId")
    suspend fun getSongById(songId: Int, userId: String): SongEntity?

    @Query("SELECT * FROM songs WHERE uri = :uri AND userId = :userId")
    suspend fun getSongByUri(uri: String, userId: String): SongEntity?
} 