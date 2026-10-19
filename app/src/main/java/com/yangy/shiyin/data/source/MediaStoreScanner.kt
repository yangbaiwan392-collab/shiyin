package com.yangy.shiyin.data.source

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.yangy.shiyin.data.entity.Track
import com.yangy.shiyin.data.model.FormatSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通过 MediaStore 读取系统已索引的音频。
 * 只查必要列，一次遍历完成，速度快；编解码细节交给 MetadataEnricher 后台补齐。
 */
class MediaStoreScanner(private val context: Context) {

    data class Result(val tracks: List<Track>, val skippedShort: Int, val skippedUnsupported: Int)

    suspend fun scan(minSeconds: Int, exclusions: List<String>, token: Long): Result = withContext(Dispatchers.IO) {
        val tracks = ArrayList<Track>(512)
        var skippedShort = 0
        var skippedUnsupported = 0

        // 先给整库的 .lrc 建一次索引：MediaStore 的音频表里没有歌词文件（它不是音频），
        // 所以必须单独查一次 Files 表，按「目录 + 基名」配给曲目。
        // 查不到（权限不足/分区限制）就退化成空表，不影响扫描。
        val lrcByFolderBase = indexLyrics()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.GENRE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.BITRATE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.BOOKMARK,
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idx = HashMap<String, Int>()
            for (column in projection) {
                idx[column] = cursor.getColumnIndexOrThrow(column)
            }
            val idCol = idx.getValue(MediaStore.Audio.Media._ID)
            val nameCol = idx.getValue(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleCol = idx.getValue(MediaStore.Audio.Media.TITLE)
            val artistCol = idx.getValue(MediaStore.Audio.Media.ARTIST)
            val albumCol = idx.getValue(MediaStore.Audio.Media.ALBUM)
            val albumArtistCol = idx.getValue(MediaStore.Audio.Media.ALBUM_ARTIST)
            val genreCol = idx.getValue(MediaStore.Audio.Media.GENRE)
            val durationCol = idx.getValue(MediaStore.Audio.Media.DURATION)
            val sizeCol = idx.getValue(MediaStore.Audio.Media.SIZE)
            val bitrateCol = idx.getValue(MediaStore.Audio.Media.BITRATE)
            val trackCol = idx.getValue(MediaStore.Audio.Media.TRACK)
            val yearCol = idx.getValue(MediaStore.Audio.Media.YEAR)
            val addedCol = idx.getValue(MediaStore.Audio.Media.DATE_ADDED)
            val modifiedCol = idx.getValue(MediaStore.Audio.Media.DATE_MODIFIED)
            val relativeCol = idx.getValue(MediaStore.Audio.Media.RELATIVE_PATH)
            val bookmarkCol = idx.getValue(MediaStore.Audio.Media.BOOKMARK)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: ""
                val extension = PathUtil.extensionOf(name)
                if (!FormatSupport.ALL.contains(extension)) {
                    skippedUnsupported++
                    continue
                }
                val durationMs = if (cursor.isNull(durationCol)) 0L else cursor.getLong(durationCol)
                if (durationMs in 1..(minSeconds * 1000L - 1)) {
                    skippedShort++
                    continue
                }
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                if (exclusions.any { uri.startsWith(it) || name.startsWith(it) }) {
                    continue
                }
                val relative = cursor.getString(relativeCol) ?: ""
                val folder = PathUtil.normalizeFolder("内部存储", relative)
                val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')
                // 同目录同基名的 .lrc（索引键：目录 + 小写基名）
                val lrc = lrcByFolderBase[relative.trimEnd('/').lowercase() + "|" + name.substringBeforeLast('.').lowercase()]
                tracks += Track(
                    id = uri.stableId(),
                    uri = uri,
                    displayName = name,
                    title = title,
                    artist = cleanMediaValue(cursor.getString(artistCol)),
                    album = cleanMediaValue(cursor.getString(albumCol)),
                    albumArtist = cleanMediaValue(cursor.getString(albumArtistCol)),
                    genre = cleanMediaValue(cursor.getString(genreCol)),
                    folderPath = folder,
                    fileName = name,
                    extension = extension,
                    durationMs = durationMs,
                    sizeBytes = if (cursor.isNull(sizeCol)) 0L else cursor.getLong(sizeCol),
                    bitrate = if (cursor.isNull(bitrateCol)) 0 else cursor.getInt(bitrateCol),
                    sampleRate = 0,
                    channels = 0,
                    bitsPerSample = 0,
                    codec = "",
                    engine = FormatSupport.engineFor(extension).name,
                    year = if (cursor.isNull(yearCol)) 0 else cursor.getInt(yearCol),
                    trackNumber = if (cursor.isNull(trackCol)) 0 else cursor.getInt(trackCol) % 1000,
                    discNumber = if (cursor.isNull(trackCol)) 0 else cursor.getInt(trackCol) / 1000,
                    dateAddedSec = if (cursor.isNull(addedCol)) 0L else cursor.getLong(addedCol),
                    dateModifiedSec = if (cursor.isNull(modifiedCol)) 0L else cursor.getLong(modifiedCol),
                    hasEmbeddedArt = false,
                    lrcUri = lrc,
                    resumePositionMs = if (cursor.isNull(bookmarkCol)) 0L else cursor.getLong(bookmarkCol),
                    scanToken = token,
                )
            }
        }
        Result(tracks, skippedShort, skippedUnsupported)
    }

    private fun cleanMediaValue(raw: String?): String {
        val value = raw?.trim().orEmpty()
        return if (value.isEmpty() || value == MediaStore.UNKNOWN_STRING) "" else value
    }

    /**
     * 给整库的 .lrc 建索引：`目录|小写基名` → 内容 URI。
     *
     * 为什么单独查一次：MediaStore 的音频表里没有歌词文件（它不是音频），
     * 只有 Files 表按文件名索引所有文件。Android 10 以下没有 RELATIVE_PATH，
     * 那时返回空表，由 Lyrics 面板里的 DATA 列兜底。
     *
     * 权限不足、查询抛异常都吞掉返回空——歌词找不到不该让整个扫描失败。
     */
    private fun indexLyrics(): Map<String, String> {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return emptyMap()
        val out = HashMap<String, String>()
        val filesUri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
        )
        runCatching {
            context.contentResolver.query(
                filesUri,
                projection,
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("%.lrc"),
                null,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val relCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    val rel = cursor.getString(relCol) ?: ""
                    val id = cursor.getLong(idCol)
                    out[rel.trimEnd('/').lowercase() + "|" + name.substringBeforeLast('.').lowercase()] =
                        ContentUris.withAppendedId(filesUri, id).toString()
                }
            }
        }
        return out
    }
}
