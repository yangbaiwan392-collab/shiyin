package com.yangy.shiyin.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 一套皮肤 = 明暗两套种子色 + 表面基调 + 圆角与描边风格。
 * 全部离线内置，不下载任何资源。
 */
enum class Skin(
    val id: String,
    val label: String,
    val hint: String,
    val lightSeed: Color,
    val darkSeed: Color,
    val lightSurface: Color,
    val darkSurface: Color,
    val roundDp: Float,
) {
    MOSS("moss", "墨绿", "松针与青苔", Color(0xFF1B6B5A), Color(0xFF6FD3B6), Color(0xFFF4F6F1), Color(0xFF0E1714), 18f),
    MIDNIGHT("midnight", "暗夜", "纯黑高对比", Color(0xFF2B3A67), Color(0xFF8FA6E8), Color(0xFFF3F4F8), Color(0xFF090B10), 14f),
    SAND("sand", "暖沙", "纸感暖调", Color(0xFF9A6B3F), Color(0xFFE0B383), Color(0xFFFBF5EA), Color(0xFF171310), 22f),
    INDIGO("indigo", "靛蓝", "青花冷调", Color(0xFF2F5D8A), Color(0xFF86BCE8), Color(0xFFF1F5F9), Color(0xFF0B1118), 16f),
    PEACH("peach", "桃夭", "柔和粉橘", Color(0xFFB5566A), Color(0xFFFFAFBE), Color(0xFFFDF3F3), Color(0xFF180F11), 26f),
    GRAPHITE("graphite", "石墨", "中性灰阶", Color(0xFF4A4F52), Color(0xFFBFC7CB), Color(0xFFF5F6F6), Color(0xFF101213), 10f),
    MIST("mist", "晨雾", "青灰通透", Color(0xFF3E7C74), Color(0xFF9AD8CE), Color(0xFFF1F7F6), Color(0xFF0C1413), 20f),
    PLUM("plum", "紫棠", "沉郁果色", Color(0xFF6A3D7D), Color(0xFFD6ADEF), Color(0xFFF8F3FA), Color(0xFF140F17), 24f),

    ;

    companion object {
        fun fromId(id: String): Skin = entries.firstOrNull { it.id == id } ?: MOSS
    }
}
