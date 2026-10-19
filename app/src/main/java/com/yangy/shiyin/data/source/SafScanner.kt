package com.yangy.shiyin.data.source

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.yangy.shiyin.data.entity.Track
import com.yangy.shiyin.data.model.FormatSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 遍历用户通过 SAF 授权的目录树，补充 MediaStore 没索引到的文件（例如 SD 卡子目录）。
 * 已经在 MediaStore 里的条目按文件名+大小去重。
 *
 * 顺带把同目录的 .lrc 配给曲目：遍历时本来就把目录里所有条目读了一遍，
 * 之前只留音频、把 .lrc 丢掉了 —— 于是歌词永远加载不出来。这里改成
 * 一个目录先读全、建好「基名 → lrc」映射、再逐条生成 Track。
 */
class SafScanner(private val context: Context) {

    private class Entry(val uri: Uri, val name: String, val size: Long, val mime: String)

    suspend fun scan(treeUri: String, minSeconds: Int, known: Set<String>, token: Long): List<Track> =
        withContext(Dispatchers.IO) {
            val root = Uri.parse(treeUri)
            val out = ArrayList<Track>()
            walk(root, "", PathUtil.fileNameOf(DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))), minSeconds, known, token, out)
            out
        }

    private fun walk(
        dirUri: Uri,
        relative: String,
        folderLabel: String,
        minSeconds: Int,
        known: Set<String>,
        token: Long,
        out: MutableList<Track>,
    ) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            dirUri,
            DocumentsContract.getDocumentId(dirUri),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )

        // 一个目录读两遍的代价比「流式读到哪算哪」大，所以整目录读进内存。
        // 目录条目数量级是几十到几百，内存无压力。
        val subdirs = ArrayList<Entry>()
        val audios = ArrayList<Entry>()
        val lrcByBase = HashMap<String, Uri>()

        context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

            while (cursor.moveToNext()) {
                val childId = cursor.getString(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val mime = cursor.getString(mimeCol) ?: ""
                val childUri = DocumentsContract.buildDocumentUriUsingTree(dirUri, childId)
                val size = if (cursor.isNull(sizeCol)) 0L else cursor.getLong(sizeCol)

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!name.startsWith('.')) subdirs += Entry(childUri, name, size, mime)
                    continue
                }

                val extension = PathUtil.extensionOf(name)
                // 歌词文件：同一目录里按「去掉扩展名的小写基名」建索引
                if (extension == "lrc" || extension == "LRC") {
                    lrcByBase[name.substringBeforeLast('.').lowercase()] = childUri
                    continue
                }
                if (!FormatSupport.ALL.contains(extension)) continue
                audios += Entry(childUri, name, size, mime)
            }
        }

        for (audio in audios) {
            if (known.contains("${audio.name}:${audio.size}")) continue
            val extension = PathUtil.extensionOf(audio.name)
            val lrc = lrcByBase[audio.name.substringBeforeLast('.').lowercase()]
            out += Track(
                id = audio.uri.toString().stableId(),
                uri = audio.uri.toString(),
                displayName = audio.name,
                title = audio.name.substringBeforeLast('.'),
                artist = "",
                album = "",
                albumArtist = "",
                genre = "",
                folderPath = folderLabel + relative,
                fileName = audio.name,
                extension = extension,
                durationMs = 0,
                sizeBytes = audio.size,
                bitrate = 0,
                sampleRate = 0,
                channels = 0,
                bitsPerSample = 0,
                codec = audio.mime.substringAfterLast('/').uppercase(),
                engine = FormatSupport.engineFor(extension).name,
                year = 0,
                trackNumber = 0,
                discNumber = 0,
                dateAddedSec = System.currentTimeMillis() / 1000,
                dateModifiedSec = 0,
                hasEmbeddedArt = false,
                lrcUri = lrc?.toString(),
                scanToken = token,
            )
        }

        for (dir in subdirs) {
            walk(dir.uri, "$relative/${dir.name}", folderLabel, minSeconds, known, token, out)
        }
    }
}
