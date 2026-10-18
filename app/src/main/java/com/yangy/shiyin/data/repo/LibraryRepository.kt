package com.yangy.shiyin.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.yangy.shiyin.data.dao.FolderDao
import com.yangy.shiyin.data.dao.PlaylistDao
import com.yangy.shiyin.data.dao.TrackDao
import com.yangy.shiyin.data.entity.MusicFolder
import com.yangy.shiyin.data.entity.Playlist
import com.yangy.shiyin.data.entity.PlaylistEntry
import com.yangy.shiyin.data.entity.Track
import com.yangy.shiyin.data.source.MediaStoreScanner
import com.yangy.shiyin.data.source.MetadataEnricher
import com.yangy.shiyin.data.source.PathUtil
import com.yangy.shiyin.data.source.SafScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScanState {
    data object Idle : ScanState
    data class Running(val stage: String, val done: Int, val total: Int) : ScanState
    data class Done(val trackCount: Int, val skippedShort: Int, val elapsedMs: Long) : ScanState
    data class Failed(val message: String) : ScanState
}

/** 专辑分组键 */
data class AlbumKey(val artist: String, val album: String)
data class AlbumGroup(val key: AlbumKey, val tracks: List<Track>) {
    val coverTrack: Track get() = tracks.minByOrNull { it.trackNumber } ?: tracks.first()
}

class LibraryRepository(
    private val context: Context,
    private val tracks: TrackDao,
    private val playlists: PlaylistDao,
    private val folders: FolderDao,
    private val settings: SettingsRepository,
) {
    private val mediaStore = MediaStoreScanner(context)
    private val saf = SafScanner(context)
    private val enricher = MetadataEnricher(context)

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    val all: Flow<List<Track>> = tracks.observeAll()
    val count: Flow<Int> = tracks.observeCount()
    val favorites: Flow<List<Track>> = tracks.observeFavorites()
    val recent: Flow<List<Track>> = tracks.observeRecent()
    val mostPlayed: Flow<List<Track>> = tracks.observeMostPlayed()
    val neverPlayed: Flow<List<Track>> = tracks.observeNeverPlayed()
    val genres: Flow<List<String>> = tracks.observeGenres()
    val folderList: Flow<List<MusicFolder>> = folders.observeAll()
    val playlistList: Flow<List<Playlist>> = playlists.observePlaylists()
    val totalDuration: Flow<Long?> = tracks.observeTotalDuration()

    fun search(query: String): Flow<List<Track>> =
        if (query.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else tracks.observeSearch(query.trim())

    fun playlistTracks(playlistId: Long): Flow<List<Track>> = playlists.observeTracks(playlistId)

    suspend fun scan(scope: CoroutineScope, enrich: Boolean) {
        if (_scanState.value is ScanState.Running) return
        val started = System.currentTimeMillis()
        val token = started
        _scanState.value = ScanState.Running("读取系统媒体库", 0, 0)
        try {
            val appSettings = settings.current()
            val exclusions = folders.exclusionsNow()
            val result = mediaStore.scan(appSettings.scanMinSeconds, exclusions, token)
            val collected = result.tracks.toMutableList()

            _scanState.value = ScanState.Running("扫描自定义文件夹", 0, collected.size)
            for (folder in folders.enabledNow()) {
                if (folder.type != FOLDER_TYPE_SAF) continue
                if (!hasTreePermission(folder.treeUri)) continue
                val known = collected.map { "${it.fileName}:${it.sizeBytes}" }.toSet()
                collected += saf.scan(folder.treeUri, folder.minSeconds, known, token)
                folders.touch(folder.treeUri, System.currentTimeMillis())
            }

            _scanState.value = ScanState.Running("写入曲库", 0, collected.size)
            tracks.upsertAll(collected)
            tracks.deleteStale(token)

            _scanState.value = ScanState.Running("补齐音频参数", 0, collected.size)
            if (enrich) enrichInBackground(scope, collected)

            _scanState.value = ScanState.Done(
                trackCount = collected.size,
                skippedShort = result.skippedShort,
                elapsedMs = System.currentTimeMillis() - started,
            )
        } catch (error: Exception) {
            _scanState.value = ScanState.Failed(error.message ?: "扫描失败")
        }
    }

    private fun enrichInBackground(scope: CoroutineScope, collected: List<Track>) {
        scope.launch(Dispatchers.IO) {
            val pending = collected.filter { it.sampleRate == 0 || !it.hasEmbeddedArt }
            var done = 0
            for (track in pending) {
                val detail = enricher.read(track.uri) ?: continue
                done++
                _scanState.value = ScanState.Running("补齐音频参数", done, pending.size)
                tracks.upsertAll(
                    listOf(
                        track.copy(
                            codec = detail.codec.ifBlank { track.codec },
                            sampleRate = detail.sampleRate,
                            channels = detail.channels,
                            bitsPerSample = detail.bitsPerSample,
                            bitrate = if (detail.bitrate > 0) detail.bitrate else track.bitrate,
                            hasEmbeddedArt = detail.hasArt,
                        ),
                    ),
                )
            }
        }
    }

    private fun hasTreePermission(treeUri: String): Boolean {
        val uri = Uri.parse(treeUri)
        val flags = context.contentResolver.getPersistedUriPermissions()
            .filter { it.uri == uri && it.isReadPermission }
        return flags.isNotEmpty()
    }

    suspend fun persistTree(treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val label = treeLabel(treeUri)
        folders.upsert(
            MusicFolder(
                treeUri = treeUri.toString(),
                label = label,
                type = FOLDER_TYPE_SAF,
            ),
        )
    }

    suspend fun removeTree(treeUri: String) {
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        folders.delete(treeUri)
    }

    suspend fun setFolderEnabled(treeUri: String, enabled: Boolean) = folders.setEnabled(treeUri, enabled)

    suspend fun setFolderMinSeconds(treeUri: String, seconds: Int) = folders.setMinSeconds(treeUri, seconds)

    suspend fun toggleFavorite(id: Long, current: Boolean) = tracks.setFavorite(id, !current)

    suspend fun markPlayed(id: Long) = tracks.markPlayed(id, System.currentTimeMillis())

    suspend fun saveResume(id: Long, positionMs: Long) = tracks.setResumePosition(id, positionMs)

    suspend fun trackById(id: Long): Track? = tracks.byId(id)

    suspend fun uriOf(id: Long): String? = tracks.uriOf(id)

    suspend fun createPlaylist(name: String): Long {
        val sort = (playlists.maxSort() ?: 0) + 1
        return playlists.insert(
            Playlist(name = name, sortIndex = sort, createdAt = System.currentTimeMillis()),
        )
    }

    suspend fun renamePlaylist(id: Long, name: String) = playlists.rename(id, name)

    suspend fun deletePlaylist(id: Long) = playlists.deletePlaylist(id)

    suspend fun addToPlaylist(playlistId: Long, trackIds: List<Long>) = withContext(Dispatchers.IO) {
        val existing = playlists.trackIds(playlistId).toSet()
        var position = (existing.size)
        val entries = trackIds.filter { !existing.contains(it) }.map {
            PlaylistEntry(playlistId = playlistId, trackId = it, position = position++)
        }
        if (entries.isNotEmpty()) playlists.addEntries(entries)
    }

    suspend fun removeFromPlaylist(playlistId: Long, trackId: Long) =
        playlists.removeTrack(playlistId, trackId)

    suspend fun playlistTrackIds(playlistId: Long): List<Long> = playlists.trackIds(playlistId)

    suspend fun inPlaylistCount(playlistId: Long, trackId: Long): Int =
        playlists.countIn(playlistId, trackId)

    /** 取目录树显示名：先查 Document 的 DISPLAY_NAME，失败则退回 documentId 末段 */
    private fun treeLabel(treeUri: Uri): String {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val fallback = docId.substringAfterLast(':').substringAfterLast('/').ifBlank { "外部文件夹" }
        val probe = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        return runCatching {
            context.contentResolver.query(
                probe,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun groupAlbums(list: List<Track>): List<AlbumGroup> =
        list.groupBy { AlbumKey(it.albumArtistOrEmpty(), it.albumOrUnknown) }
            .map { (key, value) -> AlbumGroup(key, value.sortedBy { it.trackNumber }) }
            .sortedBy { it.key.album.lowercase() }

    fun groupArtists(list: List<Track>): Map<String, List<Track>> =
        list.groupBy { it.artistOrUnknown }.toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it })

    fun groupFolders(list: List<Track>): Map<String, List<Track>> =
        list.groupBy { it.folderPath.ifBlank { "未知位置" } }
            .toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it })

    private fun Track.albumArtistOrEmpty(): String = albumArtist.ifBlank { artist }

    companion object {
        const val FOLDER_TYPE_SAF = "SAF"
        const val FOLDER_TYPE_MEDIA_STORE = "MEDIA_STORE"
    }
}
