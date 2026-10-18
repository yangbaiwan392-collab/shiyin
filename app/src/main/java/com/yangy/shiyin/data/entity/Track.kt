package com.yangy.shiyin.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [
        Index("artist"), Index("album"), Index("folderPath"),
        Index("title"), Index("genre"), Index("isFavorite"),
    ],
)
data class Track(
    @PrimaryKey val id: Long,
    val uri: String,
    val displayName: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    /** 归一化后的目录路径，用于文件夹视图 */
    val folderPath: String,
    val fileName: String,
    val extension: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val bitrate: Int,
    val sampleRate: Int,
    val channels: Int,
    @ColumnInfo(name = "bitsPerSample") val bitsPerSample: Int,
    val codec: String,
    /** 使用哪个解码引擎，见 FormatSupport.Engine */
    val engine: String,
    val year: Int,
    val trackNumber: Int,
    val discNumber: Int,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    /** 是否嵌入了封面图 */
    val hasEmbeddedArt: Boolean,
    /** 同目录是否存在 .lrc 歌词文件 */
    val lrcUri: String?,
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(defaultValue = "0") val playCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val lastPlayedAt: Long = 0,
    /** 断点续播位置 */
    @ColumnInfo(defaultValue = "0") val resumePositionMs: Long = 0,
    /** 上次扫描时该文件的存在标记，用于清理失效条目 */
    @ColumnInfo(name = "scanToken", defaultValue = "0") val scanToken: Long = 0,
) {
    val isLossless: Boolean
        get() = codec.uppercase() in LOSSLESS_CODECS || extension.lowercase() in LOSSLESS_EXT

    val artistOrUnknown: String get() = artist.ifBlank { "未知艺术家" }
    val albumOrUnknown: String get() = album.ifBlank { "未知专辑" }

    companion object {
        private val LOSSLESS_CODECS = setOf("FLAC", "ALAC", "PCM", "WAV", "TTA", "WV", "APE", "DSD", "DST", "TRUEHD", "WMA LOSSLESS", "WMA PRO")
        private val LOSSLESS_EXT = setOf("flac", "ape", "tta", "wv", "dsf", "dff", "alac", "wav", "aiff", "aif")
    }
}
