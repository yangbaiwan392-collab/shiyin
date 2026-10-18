package com.yangy.shiyin.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "0") val sortIndex: Int = 0,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0,
)

@Entity(
    tableName = "playlist_entries",
    indices = [Index("playlistId"), Index(value = ["playlistId", "position"], unique = true)],
)
data class PlaylistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
)

/**
 * 音乐来源目录。
 * type=MEDIA_STORE 表示依赖系统媒体库；type=SAF 表示用户通过文件夹选择器持久授权的目录。
 */
@Entity(tableName = "music_folders")
data class MusicFolder(
    @PrimaryKey val treeUri: String,
    val label: String,
    val type: String,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0") val lastScanAt: Long = 0,
    /** 短于该秒数的文件视为提示音，跳过 */
    @ColumnInfo(defaultValue = "20") val minSeconds: Int = 20,
)

/** 被排除的目录前缀，扫描时跳过 */
@Entity(tableName = "excluded_paths")
data class ExcludedPath(
    @PrimaryKey val pathPrefix: String,
)
