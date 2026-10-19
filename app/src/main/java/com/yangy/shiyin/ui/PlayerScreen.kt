package com.yangy.shiyin.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yangy.shiyin.data.model.PlayMode
import com.yangy.shiyin.data.repo.PlayerLayout
import com.yangy.shiyin.play.PlayUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onOpenSound: () -> Unit,
    vm: AppViewModel = viewModel(),
) {
    val state by vm.playerState.collectAsState()
    val settings by vm.settings.collectAsState()
    var showQueue by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var lyricsOn by remember { mutableStateOf(settings.playerLayout == PlayerLayout.LYRICS) }

    val track = state.current
    val duration = if (state.durationMs > 0) state.durationMs else track?.durationMs ?: 0

    Box(modifier = Modifier.fillMaxSize()) {
    CoverBackdrop(track)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowDownward, contentDescription = "收起")
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "正在播放",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showDetail = true }) {
                Icon(Icons.Rounded.MusicNote, contentDescription = "文件信息")
            }
        }

        Spacer(Modifier.height(8.dp))

        if (settings.playerLayout == PlayerLayout.MINIMAL) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = track?.title ?: "未在播放",
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track?.artistOrUnknown ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
        } else if (lyricsOn) {
            LyricsPanel(
                track = track,
                positionMs = state.positionMs,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        } else {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Cover(
                    track = track,
                    size = 320,
                    corner = if (settings.playerLayout == PlayerLayout.COVER) 26 else 12,
                    modifier = Modifier.size(320.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = track?.title ?: "未在播放",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${track?.artistOrUnknown ?: "—"} · ${track?.albumOrUnknown ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (track?.isLossless == true) {
                    Spacer(Modifier.width(8.dp))
                    LosslessBadge()
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        ProgressRow(
            positionMs = state.positionMs,
            durationMs = duration,
            onSeek = { vm.player.seekTo(it) },
        )

        ControlRow(
            state = state,
            settingsSpeed = settings.speed,
            onToggle = { vm.player.togglePlay() },
            onNext = { vm.player.next() },
            onPrevious = { vm.player.previous() },
            onMode = { vm.player.cycleMode() },
            onQueue = { showQueue = true },
            onFavorite = { track?.let { vm.toggleFavorite(it) } },
            onSleep = { showSleep = true },
            onSound = onOpenSound,
            onToggleLyrics = { lyricsOn = !lyricsOn },
            favorite = track?.isFavorite == true,
        )
    }
    }

    if (showQueue) {
        QueueSheet(onDismiss = { showQueue = false }, vm = vm, state = state)
    }
    if (showDetail) {
        FileDetailDialog(track = track, state = state, settings = settings, onDismiss = { showDetail = false })
    }
    if (showSleep) {
        SleepDialog(current = settings.sleepMinutes, onDismiss = { showSleep = false }, vm = vm)
    }
}

@Composable
private fun ProgressRow(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val progress = if (durationMs > 0) {
        (if (dragging) dragValue else positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = progress,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                if (durationMs > 0) onSeek((dragValue * durationMs).toLong())
                dragging = false
            },
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatDuration(if (dragging) (dragValue * durationMs).toLong() else positionMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "-${formatDuration(durationMs - positionMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ControlRow(
    state: PlayUiState,
    settingsSpeed: Float,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onMode: () -> Unit,
    onQueue: () -> Unit,
    onFavorite: () -> Unit,
    onSleep: () -> Unit,
    onSound: () -> Unit,
    onToggleLyrics: () -> Unit,
    favorite: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMode, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = when (state.mode) {
                    PlayMode.REPEAT_ONE -> Icons.Rounded.RepeatOne
                    PlayMode.SHUFFLE -> Icons.Rounded.Shuffle
                    else -> Icons.Rounded.Repeat
                },
                contentDescription = state.mode.label,
                tint = if (state.mode == PlayMode.SEQUENTIAL) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(34.dp))
        }
        Surface(
            onClick = onToggle,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(74.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "下一首", modifier = Modifier.size(34.dp))
        }
        IconButton(onClick = onFavorite, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "喜欢",
                tint = if (favorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(onClick = onToggleLyrics, label = { Text("歌词", fontSize = 12.sp) })
        Spacer(Modifier.width(8.dp))
        AssistChip(
            onClick = onSound,
            label = { Text("音质", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Rounded.GraphicEq, null, modifier = Modifier.size(16.dp)) },
        )
        Spacer(Modifier.width(8.dp))
        AssistChip(
            onClick = onSleep,
            label = { Text(if (settingsSpeed != 1f) "${settingsSpeed}x · 定时" else "定时", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Rounded.Timelapse, null, modifier = Modifier.size(16.dp)) },
        )
        Spacer(Modifier.width(8.dp))
        AssistChip(onClick = onQueue, label = { Text("队列", fontSize = 12.sp) })
    }
    Spacer(Modifier.height(18.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(onDismiss: () -> Unit, vm: AppViewModel, state: PlayUiState) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("播放队列 ${state.queue.size}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
        }
        LazyColumn(modifier = Modifier.height(460.dp)) {
            items(state.queue, key = { "${it.id}-${it.uri}" }) { item ->
                val index = state.queue.indexOf(item)
                TrackRow(
                    track = item,
                    isCurrent = index == state.index,
                    height = 62,
                    onClick = { vm.player.seekToIndex(index) },
                    trailing = {
                        Row {
                            IconButton(onClick = { vm.player.moveItem(index, (index - 1).coerceAtLeast(0)) }) {
                                Icon(Icons.Rounded.ArrowDownward, "上移")
                            }
                            IconButton(onClick = { vm.player.removeAt(index) }) {
                                Text("删", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun FileDetailDialog(
    track: com.yangy.shiyin.data.entity.Track?,
    state: PlayUiState,
    settings: com.yangy.shiyin.data.repo.AppSettings,
    onDismiss: () -> Unit,
) {
    if (track == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val rows = buildList {
        add("标题" to track.title)
        add("艺术家" to track.artistOrUnknown)
        add("专辑" to track.albumOrUnknown)
        add("格式" to "${track.extension.uppercase()} · ${track.codec.ifBlank { "未识别" }}")
        add("采样率" to if (track.sampleRate > 0) "${track.sampleRate} Hz" else "未识别")
        add("位深" to if (track.bitsPerSample > 0) "${track.bitsPerSample} bit" else "未识别")
        add("声道" to when (track.channels) {
            1 -> "单声道"
            2 -> "立体声"
            0 -> "未识别"
            else -> "${track.channels} 声道"
        })
        add("码率" to if (track.bitrate > 0) formatBitrate(track.bitrate / 1000) else "未识别")
        add("时长" to formatDuration(track.durationMs))
        add("体积" to formatBytes(track.sizeBytes))
        add("解码引擎" to "MediaCodec 硬解")
        add("输出通路" to settings.outputPath.label)
        add("均衡器" to if (settings.eqOn && settings.outputPath.supportsEffects) "开启（${settings.eqPresetName}）" else "关闭")
        add("变速" to "${state.speed}x")
        add("位置" to track.folderPath)
        add("文件名" to track.fileName)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文件信息") },
        text = {
            LazyColumn(modifier = Modifier.height(420.dp)) {
                items(rows) { (label, value) ->
                    Column(modifier = Modifier.padding(vertical = 5.dp)) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun SleepDialog(current: Int, onDismiss: () -> Unit, vm: AppViewModel) {
    val options = listOf(0, 15, 30, 45, 60, 90, 120)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("睡眠定时") },
        text = {
            Column {
                options.forEach { minutes ->
                    TextButton(
                        onClick = {
                            vm.update { it.copy(sleepMinutes = minutes) }
                            vm.player.startSleepTimer(minutes, vm.settings.value.sleepFadeOut)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (minutes == 0) "关闭定时" else "$minutes 分钟后停止",
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                            color = if (current == minutes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (current == minutes) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
