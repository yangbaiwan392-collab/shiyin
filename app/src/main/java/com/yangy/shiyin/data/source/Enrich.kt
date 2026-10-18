package com.yangy.shiyin.data.source

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import com.yangy.shiyin.data.entity.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 扫描后在后台补齐：编解码器、采样率、位深、声道、是否内嵌封面 */
class MetadataEnricher(private val context: Context) {

    data class Detail(
        val codec: String,
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val bitrate: Int,
        val hasArt: Boolean,
    )

    suspend fun read(uri: String): Detail? = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        val fromExtractor = readExtractor(parsed)
        val fromRetriever = readRetriever(parsed)
        if (fromExtractor == null && fromRetriever == null) return@withContext null
        Detail(
            codec = fromExtractor?.codec?.ifBlank { null }
                ?: fromRetriever?.codec
                ?: "",
            sampleRate = pick(fromExtractor?.sampleRate, fromRetriever?.sampleRate),
            channels = fromExtractor?.channels ?: 0,
            bitsPerSample = pick(fromExtractor?.bitsPerSample, fromRetriever?.bitsPerSample),
            bitrate = pick(fromRetriever?.bitrate, fromExtractor?.bitrate),
            hasArt = fromRetriever?.hasArt ?: false,
        )
    }

    private fun pick(primary: Int?, secondary: Int?): Int =
        if (primary != null && primary > 0) primary else (secondary ?: 0)

    private fun readExtractor(uri: Uri): Detail? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            var chosen = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    chosen = i
                    break
                }
            }
            if (chosen < 0) return null
            val format = extractor.getTrackFormat(chosen)
            Detail(
                codec = codecLabel(format.getString(MediaFormat.KEY_MIME).orEmpty()),
                sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                } else {
                    0
                },
                channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else {
                    0
                },
                bitsPerSample = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    format.containsKey(KEY_BITS_PER_SAMPLE)
                ) {
                    format.getInteger(KEY_BITS_PER_SAMPLE)
                } else {
                    0
                },
                bitrate = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    format.getInteger(MediaFormat.KEY_BIT_RATE)
                } else {
                    0
                },
                hasArt = false,
            )
        } catch (error: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun readRetriever(uri: Uri): Detail? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            Detail(
                codec = codecLabel(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()),
                sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0,
                channels = 0,
                bitsPerSample = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() ?: 0,
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0,
                hasArt = retriever.embeddedPicture != null,
            )
        } catch (error: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun codecLabel(mime: String): String {
        val raw = mime.removePrefix("audio/").removePrefix("video/").uppercase()
        return when {
            raw.contains("FLAC") -> "FLAC"
            raw.contains("MPEG") || raw.contains("MP3") -> "MP3"
            raw.contains("AAC") || raw.contains("MP4A") -> "AAC"
            raw.contains("ALAC") -> "ALAC"
            raw.contains("OPUS") -> "Opus"
            raw.contains("VORBIS") -> "Vorbis"
            raw.contains("PCM") || raw.contains("WAV") -> "PCM"
            raw.contains("WMA") -> "WMA"
            raw.contains("APE") -> "APE"
            raw.contains("DSD") -> "DSD"
            raw.isNotBlank() -> raw
            else -> ""
        }
    }

    private companion object {
        const val KEY_BITS_PER_SAMPLE = "bits-per-sample"
    }
}

/** 读取内嵌封面的原始字节，用于注入媒体会话（通知栏与系统播放控件需要） */
suspend fun embeddedArtBytes(context: Context, uri: String): ByteArray? = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, Uri.parse(uri))
        retriever.embeddedPicture
    } catch (error: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}
/** 封面提取，带内存缓存，图像全部来自文件本身 */
object CoverArt {

    private const val MAX_BYTES = 24 * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(context: Context, track: Track): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(track.uri)?.let { return@withContext it }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(track.uri))
            val bytes = retriever.embeddedPicture ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, 768)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.also { cache.put(track.uri, it) }
        } catch (error: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun sampleSizeFor(width: Int, height: Int, target: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= target || height / (sample * 2) >= target) {
            sample *= 2
        }
        return sample
    }
}
