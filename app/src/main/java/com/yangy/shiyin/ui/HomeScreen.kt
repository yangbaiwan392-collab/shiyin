package com.yangy.shiyin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yangy.shiyin.data.entity.Track
import com.yangy.shiyin.data.repo.FolderNode
import com.yangy.shiyin.data.repo.ListDensity
import com.yangy.shiyin.data.repo.findNode
import com.yangy.shiyin.data.repo.ScanState
import com.yangy.shiyin.play.PlayUiState

private val TABS = listOf("歌曲", "专辑", "艺术家", "文件夹", "歌单")

@Composable
fun HomeScreen(
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenPlaylist: (Long, String) -> Unit,
    vm: AppViewModel = viewModel(),
) {
    val tracks by vm.tracks.collectAsState()
    val settings by vm.settings.collectAsState()
    val playerState by vm.playerState.collectAsState()
    val scanState by vm.scanState.collectAsState()
    val albums by vm.albums.collectAsState()
    val artists by vm.artists.collectAsState()
    val folderGroups by vm.folderGroups.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val recent by vm.recent.collectAsState()
    val mostPlayed by vm.mostPlayed.collectAsState()
    val totalMs by vm.totalDurationMs.collectAsState()
    val tree by vm.folderTree.collectAsState()
    val folderScopePath by vm.folderScope.collectAsState()

    var tab by remember { mutableStateOf(0) }
    var collection by remember { mutableStateOf("全部") }
    var folderStack by remember { mutableStateOf(listOf<String>()) }

    val rowHeight = when (settings.listDensity) {
        ListDensity.COMPACT -> 56
        ListDensity.STANDARD -> 66
        ListDensity.RELAXED -> 78
    }

    Scaffold(
        // 透明底：让自定义皮肤的 background 图能透出来。
        // 没背景图时，外层 MainActivity 的 Box 已经铺了主题色，所以不会变黑。
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            Column(
                modifier = Modifier.statusBarsPadding().fillMaxWidth(),
            ) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("拾音", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onOpenSearch) { Icon(Icons.Rounded.Search, "搜索") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "设置") }
                }
                Spacer(Modifier.height(8.dp))
                TabRow(
                    selectedTabIndex = tab,
                    // 透明：让皮肤背景透到标签栏，否则这里会是一条白底
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ) {
                    TABS.forEachIndexed { index, title ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = {
                                Text(
                                    title,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible,
                                )
                            },
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding().fillMaxWidth()) {
                MiniPlayer(state = playerState, onOpen = onOpenPlayer, vm = vm)
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> {
                    val inScope: List<Track> = if (folderScopePath == null) {
                        tracks
                    } else {
                        tracks.filter {
                            it.folderPath == folderScopePath ||
                                it.folderPath.startsWith("$folderScopePath/")
                        }
                    }
                    val scopedIds = inScope.map { it.id }.toSet()
                    val pool: List<Track> = when (collection) {
                        "喜欢" -> favorites.filter { scopedIds.contains(it.id) }
                        "最近加入" -> recent.filter { scopedIds.contains(it.id) }
                        "最常播放" -> mostPlayed.filter { scopedIds.contains(it.id) }
                        else -> inScope
                    }
                    if (inScope.isEmpty()) {
                        EmptyState(
                            title = "曲库是空的",
                            body = "点下面的按钮扫描本地音乐，或在设置里添加自定义文件夹",
                            action = {
                                androidx.compose.material3.Button(onClick = { vm.scan() }) { Text("立即扫描") }
                            },
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                val scope = folderScopePath
                                if (scope != null) {
                                    val leaf = scope.split('/').lastOrNull().orEmpty()
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Spacer(Modifier.width(10.dp))
                                            Icon(
                                                Icons.Rounded.Folder,
                                                null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                leaf,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                            TextButton(
                                                onClick = {
                                                    folderStack = emptyList()
                                                    vm.setFolderScope(null)
                                                },
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                                            ) { Text("清除筛选", fontSize = 11.sp) }
                                        }
                                    }
                                }
                            }
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                ) {
                                    item {
                                        CollectionChip("全部", collection) { collection = it }
                                    }
                                    item {
                                        CollectionChip("喜欢", collection) { collection = it }
                                    }
                                    item {
                                        CollectionChip("最近加入", collection) { collection = it }
                                    }
                                    item {
                                        CollectionChip("最常播放", collection) { collection = it }
                                    }
                                }
                            }
                            item {
                                LibraryHeader(
                                    count = pool.size,
                                    totalText = formatLongDuration(totalMs),
                                    onPlayAll = { vm.play(pool, 0) },
                                    onShuffle = {
                                        vm.play(pool.shuffled(), 0)
                                    },
                                )
                            }
                            itemsIndexed(pool) { index, track ->
                                TrackRow(
                                    track = track,
                                    isCurrent = playerState.current?.id == track.id,
                                    height = rowHeight,
                                    onClick = { vm.play(pool, index) },
                                )
                            }
                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
                1 -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(albums) { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.play(group.tracks, 0) }
                                    .padding(horizontal = 18.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Cover(group.coverTrack, 56, corner = 8)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(group.key.album, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${group.key.artist.ifBlank { "未知艺术家" }} · ${group.tracks.size} 首",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
                2 -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(artists.entries.toList(), key = { it.key }) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.play(entry.value, 0) }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Cover(entry.value.firstOrNull(), 44, corner = 22)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.key, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${entry.value.size} 首",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
                3 -> {
                    val current = folderStack.lastOrNull()
                    val nodes: List<FolderNode> = if (current == null) {
                        tree
                    } else {
                        findNode(tree, current)?.children ?: emptyList()
                    }
                    val direct: List<Track> = if (current == null) {
                        emptyList()
                    } else {
                        findNode(tree, current)?.directTracks ?: emptyList()
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (current != null) {
                            item {
                                val node = findNode(tree, current)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(onClick = {
                                        val popped = folderStack.dropLast(1)
                                        folderStack = popped
                                        vm.setFolderScope(popped.lastOrNull())
                                    }) { Text("← 返回") }
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            node?.displayName ?: current,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${node?.allTracks?.size ?: 0} 首 · ${formatBytes(node?.totalBytes ?: 0L)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (node != null && node.allTracks.isNotEmpty()) {
                                        IconButton(onClick = { vm.play(node.allTracks, 0) }) {
                                            Icon(Icons.Rounded.PlayArrow, "播放此文件夹")
                                        }
                                    }
                                }
                            }
                        }
                        items(nodes, key = { it.path }) { node ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        folderStack = folderStack + node.path
                                        vm.setFolderScope(node.path)
                                    }
                                    .padding(start = 20.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        node.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${node.allTracks.size} 首 · ${formatBytes(node.totalBytes)}" +
                                            (if (node.children.isNotEmpty()) " · ${node.children.size} 个子目录" else ""),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { vm.play(node.allTracks, 0); vm.setFolderScope(node.path) }) {
                                    Icon(Icons.Rounded.PlayArrow, "播放 ${node.displayName}")
                                }
                            }
                        }
                        if (direct.isNotEmpty()) {
                            item { SectionHeader("此目录内的文件") }
                            itemsIndexed(direct) { index, track ->
                                TrackRow(
                                    track = track,
                                    isCurrent = playerState.current?.id == track.id,
                                    height = rowHeight,
                                    onClick = { vm.play(direct, index) },
                                )
                            }
                        }
                        if (nodes.isEmpty() && direct.isEmpty()) {
                            item {
                                Text(
                                    "这个目录下没有可播放的文件",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(20.dp),
                                )
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
                4 -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenPlaylist(playlist.id, playlist.name) }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("♪", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(14.dp))
                                Text(playlist.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            }
                        }
                        item {
                            TextButton(
                                onClick = { vm.createPlaylist("新建歌单 ${playlists.size + 1}") },
                                modifier = Modifier.padding(horizontal = 14.dp),
                            ) { Text("+ 新建歌单") }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionChip(label: String, selected: String, onClick: (String) -> Unit) {
    FilterChip(selected = label == selected, onClick = { onClick(label) }, label = { Text(label, fontSize = 13.sp) })
}

@Composable
fun LibraryHeader(count: Int, totalText: String, onPlayAll: () -> Unit, onShuffle: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            "$count 首 · 共 $totalText",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.Button(onClick = onPlayAll) { Text("播放全部") }
            androidx.compose.material3.OutlinedButton(onClick = onShuffle) { Text("随机播放") }
        }
    }
}

@Composable
fun OfflineBadge(modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(
            "离线",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun MiniPlayer(state: PlayUiState, onOpen: () -> Unit, vm: AppViewModel) {
    val track = state.current ?: return
    val duration = if (state.durationMs > 0) state.durationMs else track.durationMs
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column {
            if (duration > 0) {
                LinearProgressIndicator(
                    progress = { (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(track, 44, corner = 8)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        track.artistOrUnknown,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { vm.player.previous() }) {
                    Icon(Icons.Rounded.SkipPrevious, "上一首", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { vm.player.togglePlay() }) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                    )
                }
                IconButton(onClick = { vm.player.next() }) {
                    Icon(Icons.Rounded.SkipNext, "下一首", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}


