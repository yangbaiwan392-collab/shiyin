package com.yangy.shiyin.data.model

/** 播放顺序模式 */
enum class PlayMode {
    SEQUENTIAL, REPEAT_ALL, REPEAT_ONE, SHUFFLE;

    fun next(): PlayMode = when (this) {
        SEQUENTIAL -> REPEAT_ALL
        REPEAT_ALL -> REPEAT_ONE
        REPEAT_ONE -> SHUFFLE
        SHUFFLE -> SEQUENTIAL
    }

    val label: String
        get() = when (this) {
            SEQUENTIAL -> "顺序播放"
            REPEAT_ALL -> "列表循环"
            REPEAT_ONE -> "单曲循环"
            SHUFFLE -> "随机播放"
        }
}

/** 解码通路：决定音质与功能之间的取舍 */
enum class OutputPath(val label: String, val detail: String) {
    /** 硬件 offload，绕过系统混音器，省电且最接近源码，但不支持均衡器 */
    DIRECT("源码直通", "硬件解码 · 绕过混音器 · 均衡器与频谱不可用"),

    /** 软件解码后经处理链输出，支持均衡器/限幅/音量增强，可能被系统重采样 */
    PROCESSED("处理输出", "软件解码 · 支持均衡器与限幅 · 可能被系统重采样");

    val supportsEffects: Boolean get() = this == PROCESSED
}

/** 输出位深 */
enum class BitDepth(val label: String) {
    S16("16-bit PCM"),
    S24("24-bit PCM"),
    F32("32-bit float");
}

/** 输出采样率，0 表示跟随源文件 */
enum class SampleRate(val hz: Int, val label: String) {
    AUTO(0, "跟随源文件"),
    R44100(44100, "44.1 kHz"),
    R48000(48000, "48 kHz"),
    R88200(88200, "88.2 kHz"),
    R96000(96000, "96 kHz"),
    R192000(192000, "192 kHz");
}
