package com.yangy.shiyin.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yangy.shiyin.data.dao.FolderDao
import com.yangy.shiyin.data.dao.PlaylistDao
import com.yangy.shiyin.data.dao.TrackDao
import com.yangy.shiyin.data.entity.ExcludedPath
import com.yangy.shiyin.data.entity.MusicFolder
import com.yangy.shiyin.data.entity.Playlist
import com.yangy.shiyin.data.entity.PlaylistEntry
import com.yangy.shiyin.data.entity.Track

@Database(
    entities = [
        Track::class,
        Playlist::class,
        PlaylistEntry::class,
        MusicFolder::class,
        ExcludedPath::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ShiyinDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun folderDao(): FolderDao

    companion object {
        @Volatile
        private var instance: ShiyinDatabase? = null

        fun get(context: Context): ShiyinDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShiyinDatabase::class.java,
                    "shiyin.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
