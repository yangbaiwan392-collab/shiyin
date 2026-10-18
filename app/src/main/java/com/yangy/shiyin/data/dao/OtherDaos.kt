package com.yangy.shiyin.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.yangy.shiyin.data.entity.ExcludedPath
import com.yangy.shiyin.data.entity.MusicFolder
import com.yangy.shiyin.data.entity.Playlist
import com.yangy.shiyin.data.entity.PlaylistEntry
import com.yangy.shiyin.data.entity.Track
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Insert
    suspend fun insert(playlist: Playlist): Long

    @Query("SELECT * FROM playlists ORDER BY sortIndex, name COLLATE NOCASE")
    fun observePlaylists(): Flow<List<Playlist>>

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("SELECT MAX(sortIndex) FROM playlists")
    suspend fun maxSort(): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addEntries(entries: List<PlaylistEntry>)

    @Query("DELETE FROM playlist_entries WHERE id = :id")
    suspend fun removeEntry(id: Long)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun clear(playlistId: Long)

    @Query("SELECT trackId FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position")
    suspend fun trackIds(playlistId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM playlist_entries WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun countIn(playlistId: Long, trackId: Long): Int

    @Transaction
    @Query("SELECT tracks.* FROM tracks INNER JOIN playlist_entries ON tracks.id = playlist_entries.trackId WHERE playlist_entries.playlistId = :playlistId ORDER BY playlist_entries.position")
    fun observeTracks(playlistId: Long): Flow<List<Track>>

    @Transaction
    @Query("SELECT tracks.* FROM tracks INNER JOIN playlist_entries ON tracks.id = playlist_entries.trackId WHERE playlist_entries.playlistId = :playlistId ORDER BY playlist_entries.position")
    suspend fun tracksNow(playlistId: Long): List<Track>
}

@Dao
interface FolderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: MusicFolder)

    @Query("DELETE FROM music_folders WHERE treeUri = :treeUri")
    suspend fun delete(treeUri: String)

    @Query("SELECT * FROM music_folders ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<MusicFolder>>

    @Query("SELECT * FROM music_folders WHERE enabled = 1")
    suspend fun enabledNow(): List<MusicFolder>

    @Query("UPDATE music_folders SET enabled = :value WHERE treeUri = :treeUri")
    suspend fun setEnabled(treeUri: String, value: Boolean)

    @Query("UPDATE music_folders SET lastScanAt = :at WHERE treeUri = :treeUri")
    suspend fun touch(treeUri: String, at: Long)

    @Query("UPDATE music_folders SET minSeconds = :seconds WHERE treeUri = :treeUri")
    suspend fun setMinSeconds(treeUri: String, seconds: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addExclusion(path: ExcludedPath)

    @Query("DELETE FROM excluded_paths WHERE pathPrefix = :pathPrefix")
    suspend fun removeExclusion(pathPrefix: String)

    @Query("SELECT * FROM excluded_paths ORDER BY pathPrefix")
    fun observeExclusions(): Flow<List<ExcludedPath>>

    @Query("SELECT pathPrefix FROM excluded_paths")
    suspend fun exclusionsNow(): List<String>
}
