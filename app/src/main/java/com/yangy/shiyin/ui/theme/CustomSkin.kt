package com.yangy.shiyin.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 用户自定义皮肤。
 *
 * 内置的 8 套皮肤是枚举（[Skin]），写在代码里；这一份是**可以导入**的，
 * 存在 app 私有目录，用户自己写 JSON 或者打包成 ZIP 放进来。
 *
 * 颜色用 Long 存 ARGB，避免 Compose Color 不能被序列化的问题。
 * 背景图是可选的——有就铺在播放页和首页底层，没有就用纯色。
 */
data class CustomSkin(
    val id: String,
    val name: String,
    val accentLight: Long,
    val accentDark: Long,
    val surfaceLight: Long,
    val surfaceDark: Long,
    val roundDp: Float,
    /** 包里是否带了 background.* 图片 */
    val hasBackground: Boolean = false,
) {
    fun accent(dark: Boolean): Color = Color(if (dark) accentDark else accentLight)
    fun surface(dark: Boolean): Color = Color(if (dark) surfaceDark else surfaceLight)
}

/** 把 `#RGB` / `#RRGGBB` / `#AARRGGBB` 解析成 ARGB Long；解析不了返回 null */
fun parseColor(raw: String?): Long? {
    val s = raw?.trim()?.removePrefix("#") ?: return null
    return when (s.length) {
        3 -> runCatching {
            val r = s[0].digitToInt(16) * 17
            val g = s[1].digitToInt(16) * 17
            val b = s[2].digitToInt(16) * 17
            (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
        }.getOrNull()
        6 -> runCatching { (0xFFL shl 24) or s.toLong(16) }.getOrNull()
        8 -> runCatching { s.toLong(16) }.getOrNull()
        else -> null
    }
}
