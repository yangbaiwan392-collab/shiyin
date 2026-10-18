package com.yangy.shiyin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yangy.shiyin.data.entity.Track

@Composable
fun PermissionGate(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(18.dp))
        Text("需要读取音频的权限", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "拾音完全离线运行，只读取你手机里的音乐文件。它没有网络权限，也不会有任何上传行为。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(26.dp))
        Button(onClick = onGrant) { Text("授予权限") }
    }
}

@Composable
fun SearchScreen(onBack: () -> Unit, vm: AppViewModel = viewModel()) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { vm.search(query) }.collectAsState()
    val playerState by vm.playerState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 46.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("歌名、艺术家、专辑、文件名") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "清除") }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) { Text("取消") }
        }
        val list = results.value
        if (query.isNotBlank() && list.isEmpty()) {
            EmptyState("没有匹配结果", "换个关键词试试")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(list) { track ->
                    TrackRow(
                        track = track,
                        isCurrent = playerState.current?.id == track.id,
                        height = 64,
                        onClick = { vm.play(list, list.indexOf(track)) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun PlaylistScreen(
    playlistId: Long,
    name: String,
    onBack: () -> Unit,
    vm: AppViewModel = viewModel(),
) {
    val tracks = vm.playlistTracks(playlistId).collectAsState()
    val playerState by vm.playerState.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val list = tracks.value

    SettingsScaffold(name, onBack) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${list.size} 首",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { menuOpen = true }) { Text("管理") }
                    Button(onClick = { if (list.isNotEmpty()) vm.play(list, 0) }, enabled = list.isNotEmpty()) {
                        Text("播放")
                    }
                }
            }
            items(list) { track ->
                TrackRow(
                    track = track,
                    isCurrent = playerState.current?.id == track.id,
                    height = 64,
                    onClick = { vm.play(list, list.indexOf(track)) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (menuOpen) {
        val all by vm.tracks.collectAsState()
        var pickerOpen by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { menuOpen = false },
            title = { Text(name) },
            text = {
                Column {
                    TextButton(onClick = { pickerOpen = true }) { Text("从曲库添加歌曲") }
                    TextButton(
                        onClick = {
                            vm.deletePlaylist(playlistId)
                            menuOpen = false
                            onBack()
                        },
                    ) { Text("删除歌单", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { menuOpen = false }) { Text("关闭") } },
        )
        if (pickerOpen) {
            PlaylistPicker(
                candidates = all,
                onDismiss = { pickerOpen = false },
                onPick = { ids ->
                    vm.addToPlaylist(playlistId, ids)
                    pickerOpen = false
                    menuOpen = false
                },
            )
        }
    }
}

@Composable
private fun PlaylistPicker(candidates: List<Track>, onDismiss: () -> Unit, onPick: (List<Long>) -> Unit) {
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择要加入的歌曲") },
        text = {
            LazyColumn(modifier = Modifier.height(380.dp)) {
                items(candidates) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = selected.contains(track.id),
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + track.id else selected - track.id
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                track.artistOrUnknown,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(selected.toList()) }, enabled = selected.isNotEmpty()) { Text("加入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
