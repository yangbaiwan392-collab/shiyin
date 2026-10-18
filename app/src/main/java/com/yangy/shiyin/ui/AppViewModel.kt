package com.yangy.shiyin.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yangy.shiyin.ShiyinApp
import com.yangy.shiyin.data.entity.Playlist
import com.yangy.shiyin.data.entity.Track
import com.yangy.shiyin.data.model.PlayMode
import com.yangy.shiyin.data.repo.AlbumGroup
import com.yangy.shiyin.data.repo.FolderNode
import com.yangy.shiyin.data.repo.FolderTree
import com.yangy.shiyin.data.repo.AppSettings
import com.yangy.shiyin.data.repo.ScanState
import com.yangy.shiyin.play.PlayUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val shiyin = application as ShiyinApp
    private val library get() = shiyin.library

    val player get() = shiyin.player

    val settings: StateFlow<AppSettings> = shiyin.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val playerState: StateFlow<PlayUiState> = shiyin.player.state

    val tracks: StateFlow<List<Track>> = library.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trackCount: StateFlow<Int> = library.count
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val favorites: StateFlow<List<Track>> = library.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent: StateFlow<List<Track>> = library.recent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mostPlayed: StateFlow<List<Track>> = library.mostPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<Playlist>> = library.playlistList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders = library.folderList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scanState: StateFlow<ScanState> = library.scanState
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScanState.Idle)

    val totalDurationMs: StateFlow<Long> = library.totalDuration
        .map { it ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val albums: StateFlow<List<AlbumGroup>> = tracks
        .map { library.groupAlbums(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val artists: StateFlow<Map<String, List<Track>>> = tracks
        .map { library.groupArtists(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val folderGroups: StateFlow<Map<String, List<Track>>> = tracks
        .map { library.groupFolders(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val folderTree: StateFlow<List<FolderNode>> = tracks
        .map { FolderTree.build(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 主界面只显示某个文件夹内的歌曲；null 表示不筛选 */
    private val _folderScope = MutableStateFlow<String?>(null)
    val folderScope: StateFlow<String?> = _folderScope.asStateFlow()

    fun setFolderScope(path: String?) {
        _folderScope.value = path
    }

    /** 进入某个文件夹：下钻到它的子目录，同时把主界面筛选到该目录 */
    fun focusFolder(node: FolderNode) {
        _folderScope.value = node.path
    }

    val genres: StateFlow<List<String>> = library.genres
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        shiyin.player.connect()
    }

    fun search(query: String): StateFlow<List<Track>> =
        library.search(query).stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), emptyList())

    fun playlistTracks(playlistId: Long): StateFlow<List<Track>> =
        library.playlistTracks(playlistId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), emptyList())

    fun scan() {
        viewModelScope.launch {
            library.scan(viewModelScope, enrich = true)
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { shiyin.settings.update(transform) }
    }

    fun play(tracks: List<Track>, index: Int = 0) = shiyin.player.play(tracks, index)

    fun toggleFavorite(track: Track) {
        viewModelScope.launch { library.toggleFavorite(track.id, track.isFavorite) }
    }

    fun createPlaylist(name: String, then: (Long) -> Unit = {}) {
        viewModelScope.launch { then(library.createPlaylist(name)) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { library.deletePlaylist(id) }
    }

    fun renamePlaylist(id: Long, name: String) {
        viewModelScope.launch { library.renamePlaylist(id, name) }
    }

    fun addToPlaylist(id: Long, trackIds: List<Long>) {
        viewModelScope.launch { library.addToPlaylist(id, trackIds) }
    }

    fun addFolder(treeUri: android.net.Uri) {
        viewModelScope.launch {
            library.persistTree(treeUri)
            scan()
        }
    }

    fun removeFolder(treeUri: String) {
        viewModelScope.launch { library.removeTree(treeUri) }
    }

    fun setFolderEnabled(treeUri: String, enabled: Boolean) {
        viewModelScope.launch { library.setFolderEnabled(treeUri, enabled) }
    }

    fun overrideMode(mode: PlayMode) = shiyin.player.applyMode(mode)

    override fun onCleared() {
        super.onCleared()
    }
}
