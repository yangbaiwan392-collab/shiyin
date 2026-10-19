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
 * 联网补封面。
 *
 * 和歌词一样：只读取本地内嵌封面失败、且用户开了「联网补全」时才调用。
 * 数据源同为 GD 音乐台公开接口：
 *   search → 拿 pic_id → types=pic 拿图片 URL → 下载 → 缓存成本地文件
 *
 * 缓存之后 CoverArt 直接读本地文件，跟内嵌封面走完全相同的路径，不再联网。
 */
object CoverFetcher {

    private const val BASE = "https://music-api.gdstudio.xyz/api.php"
    private const val SOURCE = "netease"
    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 10000

    fun cacheFile(context: Context, trackId: String): File {
        val h = trackId.hashCode().toLong() and 0xFFFFFFFFL
        val name = h.toString(16).padStart(8, '0') + ".img"
        return File(File(context.filesDir, "covers"), name)
    }

    /** 命中返回图片原始字节（并写入缓存），否则 null。 */
    suspend fun fetch(context: Context, track: Track): ByteArray? = withContext(Dispatchers.IO) {
        // ① 本地缓存优先：同一首歌不重复下载
        val cache = cacheFile(context, track.id.toString())
        if (cache.exists()) {
            runCatching { cache.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        }

        // ② 搜索，挑歌名最接近的一条，拿 pic_id
        val query = buildString {
            if (track.artist.isNotBlank()) append(track.artist).append(' ')
            append(if (track.title.isNotBlank()) track.title else track.fileName.substringBeforeLast('.'))
        }.trim()
        if (query.isEmpty()) return@withContext null

        val searchBody = httpGetText("$BASE?types=search&source=$SOURCE&name=${enc(query)}&count=5&pages=1")
            ?: return@withContext null

        val picId = runCatching {
            val arr = JSONArray(searchBody)
            if (arr.length() == 0) return@runCatching null
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
            best?.optString("pic_id")?.takeIf { it.isNotBlank() && it != "null" }
        }.getOrNull() ?: return@withContext null

        // ③ 拿真实图片 URL
        val picBody = httpGetText("$BASE?types=pic&source=$SOURCE&id=${enc(picId)}") ?: return@withContext null
        val picUrl = runCatching { JSONObject(picBody).optString("url") }
            .getOrNull()?.takeIf { it.startsWith("http") } ?: return@withContext null

        // ④ 下载图片
        val bytes = httpGetBytes(picUrl) ?: return@withContext null
        if (bytes.isEmpty()) return@withContext null

        // ⑤ 落盘缓存
        runCatching {
            cache.parentFile?.mkdirs()
            cache.writeBytes(bytes)
        }
        bytes
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun normalize(raw: String): String {
        var s = raw.lowercase()
        for (suffix in listOf("(live)", "(remastered)", "(remaster)", "[live]", "(demo)", "(伴奏)", "(翻唱)")) {
            s = s.replace(suffix, "")
        }
        return s.replace(Regex("[\\s\\-_.()\\[\\]（）【】'\",，。！!？?]"), "")
    }

    private fun httpGetText(url: String): String? = runCatching {
        val conn = open(url)
        try {
            if (conn.responseCode !in 200..299) return@runCatching null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun httpGetBytes(url: String): ByteArray? = runCatching {
        val conn = open(url)
        try {
            if (conn.responseCode !in 200..299) return@runCatching null
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", "Shiyin/0.2 (local player; cover only)")
            instanceFollowRedirects = true
        }
}
