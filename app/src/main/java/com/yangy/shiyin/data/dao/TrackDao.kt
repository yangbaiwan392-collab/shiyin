package com.yangy.shiyin.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yangy.shiyin.data.entity.Track
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<Track>)

    @Update
    suspend fun update(track: Track)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM tracks WHERE scanToken <> :keepToken")
    suspend fun deleteStale(keepToken: Long)

    @Query("SELECT * FROM tracks WHERE scanToken <> :keepToken")
    suspend fun findStale(keepToken: Long): List<Track>

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun byId(id: Long): Track?

    @Query("SELECT * FROM tracks WHERE id IN (:ids) ORDER BY title COLLATE NOCASE")
    fun observeByIds(ids: List<Long>): Flow<List<Track>>

    @Query(
        """
        SELECT * FROM tracks
        WHERE title LIKE '%' || :q || '%' COLLATE NOCASE
           OR artist LIKE '%' || :q || '%' COLLATE NOCASE
           OR album LIKE '%' || :q || '%' COLLATE NOCASE
           OR fileName LIKE '%' || :q || '%' COLLATE NOCASE
        ORDER BY title COLLATE NOCASE
        """
    )
    fun observeSearch(q: String): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY title COLLATE NOCASE")
    fun observeFavorites(): Flow<List<Track>>

    @Query("SELECT COUNT(*) FROM tracks")
    fun observeCount(): Flow<Int>
    @Query("SELECT SUM(durationMs) FROM tracks")
    fun observeTotalDuration(): Flow<Long?>

    @Query("SELECT * FROM tracks ORDER BY dateAddedSec DESC LIMIT 200")
    fun observeRecent(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE playCount > 0 ORDER BY playCount DESC LIMIT 200")
    fun observeMostPlayed(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE playCount = 0 ORDER BY dateAddedSec DESC LIMIT 200")
    fun observeNeverPlayed(): Flow<List<Track>>

    @Query("SELECT DISTINCT genre FROM tracks WHERE genre <> '' ORDER BY genre COLLATE NOCASE")
    fun observeGenres(): Flow<List<String>>

    @Query("UPDATE tracks SET isFavorite = :value WHERE id = :id")
    suspend fun setFavorite(id: Long, value: Boolean)

    @Query("UPDATE tracks SET resumePositionMs = :positionMs WHERE id = :id")
    suspend fun setResumePosition(id: Long, positionMs: Long)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :now WHERE id = :id")
    suspend fun markPlayed(id: Long, now: Long)

    @Query("SELECT uri FROM tracks WHERE id = :id")
    suspend fun uriOf(id: Long): String?
}
