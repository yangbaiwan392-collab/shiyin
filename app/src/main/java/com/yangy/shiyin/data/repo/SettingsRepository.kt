package com.yangy.shiyin.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yangy.shiyin.data.model.BitDepth
import com.yangy.shiyin.data.model.OutputPath
import com.yangy.shiyin.data.model.SampleRate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class DarkMode { FOLLOW_SYSTEM, LIGHT, DARK }

enum class PlayerLayout(val label: String) {
    COVER("大封面"), LYRICS("歌词优先"), MINIMAL("极简");
}

enum class ListDensity(val label: String) {
    COMPACT("紧凑"), STANDARD("标准"), RELAXED("宽松");
}

/** 全部用户设置，纯本地，不落网络 */
data class AppSettings(
    val skinId: String = "moss",
    val darkMode: DarkMode = DarkMode.FOLLOW_SYSTEM,
    val dynamicFromCover: Boolean = false,
    val customAccent: Int? = null,
    val playerLayout: PlayerLayout = PlayerLayout.COVER,
    val listDensity: ListDensity = ListDensity.STANDARD,
    val outputPath: OutputPath = OutputPath.PROCESSED,
    val sampleRate: SampleRate = SampleRate.AUTO,
    val bitDepth: BitDepth = BitDepth.S16,
    val gapless: Boolean = true,
    val crossfadeSec: Int = 0,
    val fadeInMs: Int = 0,
    val speed: Float = 1.0f,
    val volumeBoostPercent: Int = 0,
    val limiterOn: Boolean = true,
    val preampDb: Float = 0f,
    val eqOn: Boolean = false,
    val eqGains: FloatArray = FloatArray(10) { 0f },
    val eqPresetName: String = "平坦",
    val skipSilence: Boolean = false,
    val monoMix: Boolean = false,
    val visualizerOn: Boolean = false,
    val sleepMinutes: Int = 0,
    val sleepFadeOut: Boolean = true,
    val resumePlayback: Boolean = true,
    val scanMinSeconds: Int = 20,
    val skipNotifications: Boolean = true,
    /** 是否允许联网补全歌词与封面（默认关；开启后也只在 WiFi 下联网） */
    val lyricOnline: Boolean = false,
) {
    val ampingLinear: Float get() = 1f + volumeBoostPercent / 100f
    val effectsAllowed: Boolean get() = outputPath.supportsEffects

    override fun equals(other: Any?): Boolean {
        if (this !== other) return false
        if (other !is AppSettings) return false
        return skinId == other.skinId && darkMode == other.darkMode &&
            dynamicFromCover == other.dynamicFromCover && customAccent == other.customAccent &&
            outputPath == other.outputPath && sampleRate == other.sampleRate &&
            bitDepth == other.bitDepth && eqOn == other.eqOn && eqGains.contentEquals(other.eqGains) &&
            speed == other.speed && volumeBoostPercent == other.volumeBoostPercent &&
            limiterOn == other.limiterOn && preampDb == other.preampDb &&
            gapless == other.gapless && crossfadeSec == other.crossfadeSec &&
            monoMix == other.monoMix && visualizerOn == other.visualizerOn
    }

    override fun hashCode(): Int =
        skinId.hashCode() * 31 + eqGains.contentHashCode()
}

class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun current(): AppSettings = store.data.first().toSettings()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val current = prefs.toSettings()
            val next = transform(current)
            with(prefs) {
                this[K_SKIN] = next.skinId
                this[K_DARK] = next.darkMode.name
                this[K_DYNAMIC] = next.dynamicFromCover
                val accent = next.customAccent
                if (accent != null) this[K_ACCENT] = accent else remove(K_ACCENT)
                this[K_LAYOUT] = next.playerLayout.name
                this[K_DENSITY] = next.listDensity.name
                this[K_PATH] = next.outputPath.name
                this[K_RATE] = next.sampleRate.name
                this[K_DEPTH] = next.bitDepth.name
                this[K_GAPLESS] = next.gapless
                this[K_XFADE] = next.crossfadeSec
                this[K_FADEIN] = next.fadeInMs
                this[K_SPEED] = next.speed
                this[K_BOOST] = next.volumeBoostPercent
                this[K_LIMITER] = next.limiterOn
                this[K_PREAMP] = next.preampDb
                this[K_EQ_ON] = next.eqOn
                this[K_EQ] = next.eqGains.joinToString(",")
                this[K_EQ_PRESET] = next.eqPresetName
                this[K_SKIP_SILENCE] = next.skipSilence
                this[K_MONO] = next.monoMix
                this[K_VIS] = next.visualizerOn
                this[K_SLEEP] = next.sleepMinutes
                this[K_SLEEP_FADE] = next.sleepFadeOut
                this[K_RESUME] = next.resumePlayback
                this[K_MINSEC] = next.scanMinSeconds
                this[K_SKIP_NOTIF] = next.skipNotifications
                this[K_LYRIC_ONLINE] = next.lyricOnline
            }
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val gains = (this[K_EQ] ?: "")
            .split(',')
            .mapNotNull { it.toFloatOrNull() }
            .let { list -> FloatArray(10) { i -> list.getOrElse(i) { 0f } } }
        return AppSettings(
            skinId = this[K_SKIN] ?: "moss",
            darkMode = enumOrDefault(this[K_DARK], DarkMode.FOLLOW_SYSTEM),
            dynamicFromCover = this[K_DYNAMIC] ?: false,
            customAccent = this[K_ACCENT],
            playerLayout = enumOrDefault(this[K_LAYOUT], PlayerLayout.COVER),
            listDensity = enumOrDefault(this[K_DENSITY], ListDensity.STANDARD),
            outputPath = enumOrDefault(this[K_PATH], OutputPath.PROCESSED),
            sampleRate = enumOrDefault(this[K_RATE], SampleRate.AUTO),
            bitDepth = enumOrDefault(this[K_DEPTH], BitDepth.S16),
            gapless = this[K_GAPLESS] ?: true,
            crossfadeSec = this[K_XFADE] ?: 0,
            fadeInMs = this[K_FADEIN] ?: 0,
            speed = this[K_SPEED] ?: 1f,
            volumeBoostPercent = this[K_BOOST] ?: 0,
            limiterOn = this[K_LIMITER] ?: true,
            preampDb = this[K_PREAMP] ?: 0f,
            eqOn = this[K_EQ_ON] ?: false,
            eqGains = gains,
            eqPresetName = this[K_EQ_PRESET] ?: "平坦",
            skipSilence = this[K_SKIP_SILENCE] ?: false,
            monoMix = this[K_MONO] ?: false,
            visualizerOn = this[K_VIS] ?: false,
            sleepMinutes = this[K_SLEEP] ?: 0,
            sleepFadeOut = this[K_SLEEP_FADE] ?: true,
            resumePlayback = this[K_RESUME] ?: true,
            scanMinSeconds = this[K_MINSEC] ?: 20,
            skipNotifications = this[K_SKIP_NOTIF] ?: true,
            lyricOnline = this[K_LYRIC_ONLINE] ?: false,
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, fallback: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private companion object {
        val K_SKIN = stringPreferencesKey("skin")
        val K_DARK = stringPreferencesKey("dark_mode")
        val K_DYNAMIC = booleanPreferencesKey("dynamic")
        val K_ACCENT = intPreferencesKey("accent")
        val K_LAYOUT = stringPreferencesKey("layout")
        val K_DENSITY = stringPreferencesKey("density")
        val K_PATH = stringPreferencesKey("output_path")
        val K_RATE = stringPreferencesKey("sample_rate")
        val K_DEPTH = stringPreferencesKey("bit_depth")
        val K_GAPLESS = booleanPreferencesKey("gapless")
        val K_XFADE = intPreferencesKey("crossfade")
        val K_FADEIN = intPreferencesKey("fade_in")
        val K_SPEED = floatPreferencesKey("speed")
        val K_BOOST = intPreferencesKey("boost")
        val K_LIMITER = booleanPreferencesKey("limiter")
        val K_PREAMP = floatPreferencesKey("preamp")
        val K_EQ_ON = booleanPreferencesKey("eq_on")
        val K_EQ = stringPreferencesKey("eq_gains")
        val K_EQ_PRESET = stringPreferencesKey("eq_preset")
        val K_SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val K_MONO = booleanPreferencesKey("mono")
        val K_VIS = booleanPreferencesKey("visualizer")
        val K_SLEEP = intPreferencesKey("sleep_minutes")
        val K_SLEEP_FADE = booleanPreferencesKey("sleep_fade")
        val K_RESUME = booleanPreferencesKey("resume")
        val K_MINSEC = intPreferencesKey("scan_min_seconds")
        val K_SKIP_NOTIF = booleanPreferencesKey("skip_notif")
        val K_LYRIC_ONLINE = booleanPreferencesKey("lyric_online")
    }
}
