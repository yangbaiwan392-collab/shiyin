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
 */
class SafScanner(private val context: Context) {

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
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (name.startsWith('.')) continue
                    walk(childUri, "$relative/$name", folderLabel, minSeconds, known, token, out)
                    continue
                }
                val extension = PathUtil.extensionOf(name)
                if (!FormatSupport.ALL.contains(extension)) continue
                if (known.contains("$name:${cursor.getLong(sizeCol)}")) continue
                out += Track(
                    id = childUri.toString().stableId(),
                    uri = childUri.toString(),
                    displayName = name,
                    title = name.substringBeforeLast('.'),
                    artist = "",
                    album = "",
                    albumArtist = "",
                    genre = "",
                    folderPath = folderLabel + relative,
                    fileName = name,
                    extension = extension,
                    durationMs = 0,
                    sizeBytes = cursor.getLong(sizeCol),
                    bitrate = 0,
                    sampleRate = 0,
                    channels = 0,
                    bitsPerSample = 0,
                    codec = mime.substringAfterLast('/').uppercase(),
                    engine = FormatSupport.engineFor(extension).name,
                    year = 0,
                    trackNumber = 0,
                    discNumber = 0,
                    dateAddedSec = System.currentTimeMillis() / 1000,
                    dateModifiedSec = 0,
                    hasEmbeddedArt = false,
                    lrcUri = null,
                    scanToken = token,
                )
            }
        }
    }
}
