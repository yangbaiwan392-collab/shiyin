package com.yangy.shiyin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yangy.shiyin.data.entity.Track
import com.yangy.shiyin.data.source.LrcParser
import com.yangy.shiyin.data.source.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 歌词面板。
 * 只读取本地来源：同目录 .lrc 文件，或文件名同名 .lrc。不联网抓取。
 */
@Composable
fun LyricsPanel(
    track: Track?,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var offsetMs by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(track?.uri) {
        lines = emptyList()
        if (track == null) return@LaunchedEffect
        loading = true
        lines = withContext(Dispatchers.IO) { loadLyrics(context, track) }
        loading = false
    }

    val active = remember(positionMs, lines, offsetMs) {
        LrcParser.indexAt(lines, positionMs, offsetMs)
    }

    LaunchedEffect(active) {
        if (active >= 0 && lines.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(active.coerceAtMost(lines.lastIndex)) }
        }
    }

    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (lines.isEmpty()) {
            Spacer(Modifier.height(80.dp))
            Text(
                text = if (loading) "正在查找歌词…" else "没有歌词文件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!loading) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "把同名的 .lrc 放在音频旁边即可显示",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 90.dp, horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(lines) { index, line ->
                    val isCurrent = index == active
                    Text(
                        text = line.text.ifBlank { "♪" },
                        fontSize = if (isCurrent) 21.sp else 16.sp,
                        lineHeight = if (isCurrent) 29.sp else 23.sp,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 7.dp),
                    )
                }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Text(
                    text = if (offsetMs == 0) "歌词同步 0.0s" else "歌词同步 ${offsetMs / 1000.0}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = offsetMs / 1000f,
                    onValueChange = { offsetMs = (it * 1000).toInt() },
                    valueRange = -10f..10f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun loadLyrics(context: android.content.Context, track: Track): List<LyricLine> {
    val candidates = ArrayList<String>()
    track.lrcUri?.let { candidates += it }
    // 通过 MediaStore 的 DATA 列定位同目录 .lrc
    runCatching {
        val uri = android.net.Uri.parse(track.uri)
        context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.Audio.Media.DATA), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    val path = cursor.getString(0)
                    val file = File(path)
                    val sibling = File(file.parentFile, file.nameWithoutExtension + ".lrc")
                    if (sibling.exists()) candidates += sibling.absolutePath
                }
            }
    }
    for (candidate in candidates) {
        val text = runCatching {
            val file = File(candidate)
            if (file.exists()) file.readText() else null
        }.getOrNull() ?: continue
        val parsed = LrcParser.parse(text)
        if (parsed.isNotEmpty()) return parsed
    }
    return emptyList()
}
