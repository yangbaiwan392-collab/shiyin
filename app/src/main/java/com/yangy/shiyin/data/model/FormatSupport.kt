package com.yangy.shiyin.data.model

/**
 * 播放引擎路由。
 *
 * 当前只有 MediaCodec / Media3 这一条原生通路 —— libVLC 那层（WMA/APE/TTA/WavPack/DSD
 * 等冷门格式的转码）已经移除，APK 从 63.9 MB 瘦到 ~20 MB（其中 42 MB 是 libvlc.so）。
 * 想要那些格式就自己把 libVLC 加回来，路由逻辑本来就在这。
 */
object FormatSupport {

    /** Media3 / MediaCodec 可直接解码的扩展名 */
    private val NATIVE = setOf(
        "mp3", "flac", "m4a", "m4b", "mp4", "aac", "ogg", "oga", "opus",
        "wav", "wave", "aif", "aiff", "amr", "mid", "midi", "xmf", "mxmf", "rtttl", "rtx", "ota", "wtv",
    )

    /** 扫描时认定为音乐的扩展名全集 */
    val ALL: Set<String> get() = NATIVE

    fun engineFor(extension: String): Engine = Engine.NATIVE

    fun isSupported(extension: String): Boolean = NATIVE.contains(extension.lowercase())

    enum class Engine(val label: String) {
        NATIVE("MediaCodec 硬解"),
    }
}
