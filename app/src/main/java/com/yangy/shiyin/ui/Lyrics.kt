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
import com.yangy.shiyin.app
import com.yangy.shiyin.data.entity.Track
import com.yangy.shiyin.data.source.LrcParser
import com.yangy.shiyin.data.source.LyricFetcher
import com.yangy.shiyin.data.source.LyricLine
import com.yangy.shiyin.data.source.NetworkState
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
    var loadingOnline by remember { mutableStateOf(false) }
    var offsetMs by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current

    LaunchedEffect(track?.uri) {
        lines = emptyList()
        if (track == null) return@LaunchedEffect
        loading = true
        loadingOnline = false

        // ① 本地优先：同目录 .lrc（扫描期已配好）或 DATA 列兜底
        var result = withContext(Dispatchers.IO) { loadLyrics(context, track) }

        // ② 本地没有，且用户显式开了「联网补全」才联网
        if (result.isEmpty()) {
            val settings = withContext(Dispatchers.IO) {
                runCatching { context.app.settings.current() }.getOrNull()
            }
            val canFetch = settings != null &&
                settings.lyricOnline &&
                NetworkState.canFetch(context)
            if (canFetch) {
                loadingOnline = true
                val fetched = withContext(Dispatchers.IO) { LyricFetcher.fetch(context, track) }
                if (fetched != null) result = LrcParser.parse(fetched)
                loadingOnline = false
            }
        }

        lines = result
        loading = false
    }

    val active = remember(positionMs, lines, offsetMs) {
        LrcParser.indexAt(lines, positionMs, offsetMs)
    }

    LaunchedEffect(active) {
        if (active >= 0 && lines.isNotEmpty()) {
            val target = active.coerceAtMost(lines.lastIndex)
            // 把当前行滚到列表垂直中线附近（不是贴顶），这样视觉上是「往上走」
            // 而不是「跳到顶部」。scrollOffset 为负 = 让该行往下落。
            val layout = listState.layoutInfo
            val viewport = layout.viewportEndOffset - layout.viewportStartOffset
            if (viewport > 0) {
                val lineHeight = with(density) { 30.dp.roundToPx() }
                val offset = -(viewport / 2 - lineHeight / 2)
                runCatching { listState.animateScrollToItem(target, offset) }
            } else {
                runCatching { listState.animateScrollToItem(target) }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (lines.isEmpty()) {
            Spacer(Modifier.height(80.dp))
            Text(
                text = when {
                    loadingOnline -> "正在联网查找歌词…"
                    loading -> "正在查找歌词…"
                    else -> "没有歌词"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!loading && !loadingOnline) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "把同名的 .lrc 放在音频旁边即可显示；\n或在设置里打开「联网找歌词」",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
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
    // ① 扫描期配好的（SAF 是 content://，MediaStore 是 content://）
    track.lrcUri?.let { candidates += it }
    // ② 老系统 / 扫描没配上的兜底：DATA 列拿绝对路径，找同目录 .lrc
    runCatching {
        val uri = android.net.Uri.parse(track.uri)
        context.contentResolver.query(
            uri,
            arrayOf(android.provider.MediaStore.Audio.Media.DATA),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                val file = File(cursor.getString(0))
                val sibling = File(file.parentFile, file.nameWithoutExtension + ".lrc")
                if (sibling.exists()) candidates += sibling.absolutePath
            }
        }
    }

    for (candidate in candidates) {
        val bytes = readLrcBytes(context, candidate) ?: continue
        val text = decodeLyricBytes(bytes) ?: continue
        val parsed = LrcParser.parse(text)
        if (parsed.isNotEmpty()) return parsed
    }
    return emptyList()
}

/** 按 URI 类型读字节：content:// 走 ContentResolver（SAF 只能这样读），路径走 File。 */
private fun readLrcBytes(context: android.content.Context, candidate: String): ByteArray? = runCatching {
    val uri = android.net.Uri.parse(candidate)
    when {
        uri.scheme == "content" -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        uri.scheme == "file" -> File(uri.path ?: return@runCatching null).takeIf { it.exists() }?.readBytes()
        else -> File(candidate).takeIf { it.exists() }?.readBytes()
    }
}.getOrNull()

/**
 * 歌词文件编码探测。
 *
 * 中文歌词大量是 GBK 编码（尤其从老播放器/网盘扒下来的），
 * 之前一律按 UTF-8 读，结果全是乱码——这是「歌词显示不出来」的第二大原因。
 *
 * 顺序：BOM > UTF-8 严格解码 > GBK。
 */
private fun decodeLyricBytes(bytes: ByteArray): String? {
    if (bytes.isEmpty()) return null
    // BOM
    if (bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
    ) return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
        return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
        return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
    }
    // 严格 UTF-8：能解就是 UTF-8，解不了才回退 GBK
    runCatching {
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString()
    }
    return runCatching { String(bytes, charset("GBK")) }.getOrNull()
}
