package com.yangy.shiyin

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.yangy.shiyin.data.ShiyinDatabase
import com.yangy.shiyin.data.repo.LibraryRepository
import com.yangy.shiyin.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus

class ShiyinApp : Application() {

    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + dispatchers) }

    private val dispatchers = kotlinx.coroutines.Dispatchers.Default

    val preferences: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create { preferencesDataStoreFile("shiyin_prefs") }
    }

    val settings: SettingsRepository by lazy { SettingsRepository(preferences) }

    val library: LibraryRepository by lazy {
        val db = ShiyinDatabase.get(this)
        LibraryRepository(this, db.trackDao(), db.playlistDao(), db.folderDao(), settings)
    }

    val player: com.yangy.shiyin.play.PlayerController by lazy {
        com.yangy.shiyin.play.PlayerController(this)
    }

    companion object {
        lateinit var instance: ShiyinApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}

val Context.app: ShiyinApp get() = applicationContext as ShiyinApp
