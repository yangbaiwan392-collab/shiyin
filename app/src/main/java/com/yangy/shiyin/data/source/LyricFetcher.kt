package com.yangy.shiyin.data.source

import android.content.Context
import com.yangy.shiyin.data.entity.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 联网歌词抓取。
 *
 * 只有用户在设置里显式打开「联网找歌词」后才会被调用；默认路径完全不联网。
 *
 * 数据源：GD 音乐台的公开接口（https://music-api.gdstudio.xyz/api.php），
 * 它聚合了多个音乐平台。这里用 netease 源 —— 实测英文/蓝调曲目
 * 「搜得到 + 有歌词」的命中率最高（10/10）。
 *
 * 取到歌词后落成本地 .lrc 缓存，之后跟手动放的 lrc 走完全相同的本地路径，
 * 不再发起任何网络请求。
 */
object LyricFetcher {

    private const val BASE = "https://music-api.gdstudio.xyz/api.php"
    private const val SOURCE = "netease"
    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 8000

    /**
     * 缓存文件名用 track.id 的稳定哈希。
     * Java String.hashCode() 的算法是规范固定的，跨进程/跨版本一致，
     * 所以同一个曲目每次都能命中同一个缓存文件。
     */
    fun cacheFile(context: Context, trackId: String): File {
        // 取 hashCode 的无符号 32 位表示，固定 8 位十六进制，
        // 避免负数在文件名里出现 '-'，也避免 Int.toString(16) 的符号问题
        val h = trackId.hashCode().toLong() and 0xFFFFFFFFL
        val name = h.toString(16).padStart(8, '0') + ".lrc"
        return File(File(context.filesDir, "lyrics"), name)
    }

    /**
     * 联网取歌词。命中返回 LRC 原文（并写入缓存），否则返回 null。
     * 调用方负责保证「用户已开启联网歌词」。
     */
    suspend fun fetch(context: Context, track: Track): String? = withContext(Dispatchers.IO) {
        // ① 先看缓存：同一首歌不重复请求
        val cache = cacheFile(context, track.id.toString())
        if (cache.exists()) {
            runCatching { cache.readText() }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        }

        // ② 组搜索词：有艺术家就带上，命中率明显更高
        val query = buildString {
            if (track.artist.isNotBlank()) append(track.artist).append(' ')
            append(if (track.title.isNotBlank()) track.title else track.fileName.substringBeforeLast('.'))
        }.trim()
        if (query.isEmpty()) return@withContext null

        // ③ 搜索，拿 lyric_id
        val searchUrl = "$BASE?types=search&source=$SOURCE&name=${enc(query)}&count=5&pages=1"
        val searchBody = httpGet(searchUrl) ?: return@withContext null
        val lyricId = runCatching {
            val arr = JSONArray(searchBody)
            if (arr.length() == 0) return@runCatching null
            // 优先挑歌名跟本地标题最接近的一条，避免搜到翻唱/伴奏
            val want = normalize(track.title)
            var best: JSONObject? = null
            var bestScore = -1
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = normalize(o.optString("name"))
                val score = when {
                    name == want -> 100
                    name.startsWith(want) || want.startsWith(name) -> 80
                    name.contains(want) || want.contains(name) -> 60
                    else -> 10
                }
                if (score > bestScore) { bestScore = score; best = o }
            }
            best?.optString("lyric_id")?.takeIf { it.isNotBlank() && it != "null" }
        }.getOrNull() ?: return@withContext null

        // ④ 取歌词
        val lyricUrl = "$BASE?types=lyric&source=$SOURCE&id=${enc(lyricId)}"
        val lyricBody = httpGet(lyricUrl) ?: return@withContext null
        val lyric = runCatching {
            JSONObject(lyricBody).optString("lyric")
        }.getOrNull()?.takeIf { it.isNotBlank() && it != "null" } ?: return@withContext null

        // ⑤ 落盘缓存（失败也不影响本次显示）
        runCatching {
            cache.parentFile?.mkdirs()
            cache.writeText(lyric)
        }
        lyric
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** 规范化歌名：去掉常见后缀与标点，方便比较「晴天」和「晴天 (Live)」 */
    private fun normalize(raw: String): String {
        var s = raw.lowercase()
        for (suffix in listOf("(live)", "(remastered)", "(remaster)", "[live]", "(demo)", "(伴奏)", "(翻唱)")) {
            s = s.replace(suffix, "")
        }
        return s.replace(Regex("[\\s\\-_.()\\[\\]（）【】'\",，。！!？?]"), "")
    }

    private fun httpGet(url: String): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", "Shiyin/0.2 (local player; lyrics only)")
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) return@runCatching null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
