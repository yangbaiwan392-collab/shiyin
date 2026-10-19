package com.yangy.shiyin.data.repo

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import com.yangy.shiyin.ui.theme.CustomSkin
import com.yangy.shiyin.ui.theme.parseColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 自定义皮肤的存取。
 *
 * 用户的皮肤包可能是：
 *   ① 一个 JSON 文件（只有颜色和圆角）
 *   ② 一个 ZIP（里面至少要有 skin.json，还可以带 background.jpg/png/webp）
 *
 * 导入后落到 app 私有目录 `files/skins/<id>/`，不写进媒体库、不需要读写存储权限。
 * 所有解析失败都抛异常，由调用方转成给用户看的提示；不会留下半成品目录。
 */
object SkinStore {

    private const val DIR = "skins"
    private const val MAX_TOTAL_BYTES = 12 * 1024 * 1024

    private fun root(context: Context) = File(context.filesDir, DIR)

    fun list(context: Context): List<CustomSkin> {
        val r = root(context)
        if (!r.isDirectory) return emptyList()
        return r.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { read(context, it.name) }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun find(context: Context, id: String): CustomSkin? = read(context, id)

    private fun read(context: Context, id: String): CustomSkin? {
        val dir = File(root(context), id)
        val json = File(dir, "skin.json").takeIf { it.isFile } ?: return null
        return runCatching { parse(dir, JSONObject(json.readText())) }.getOrNull()
    }

    /** 背景图文件（有就返回），给播放页/首页铺底层用 */
    fun backgroundFile(context: Context, id: String): File? {
        val dir = File(root(context), id)
        return dir.listFiles()?.firstOrNull {
            it.isFile && it.name.startsWith("background.", ignoreCase = true)
        }
    }

    suspend fun import(context: Context, uri: Uri): Result<CustomSkin> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("读不到这个文件")
            if (bytes.isEmpty()) error("文件是空的")
            if (bytes.size > MAX_TOTAL_BYTES) error("文件超过 12 MB")
            if (isZip(bytes)) importZip(context, bytes) else importJson(context, bytes)
        }
    }

    private fun isZip(b: ByteArray): Boolean =
        b.size >= 4 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte()

    private fun importJson(context: Context, bytes: ByteArray): CustomSkin {
        val json = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }
            .getOrElse { error("不是合法的 JSON") }
        val id = newId()
        val dir = File(root(context), id).apply { mkdirs() }
        File(dir, "skin.json").writeText(json.toString())
        return runCatching { parse(dir, json) }.getOrElse {
            dir.deleteRecursively()
            throw it
        }
    }

    private fun importZip(context: Context, bytes: ByteArray): CustomSkin {
        val id = newId()
        val dir = File(root(context), id).apply { mkdirs() }
        var skinJson: String? = null
        var background: Pair<String, ByteArray>? = null
        return runCatching {
            ZipInputStream(bytes.inputStream()).use { zin ->
                var total = 0
                while (true) {
                    val entry = zin.nextEntry ?: break
                    if (entry.isDirectory) continue
                    // 只认包根的 skin.json 和 background.*，忽略子目录和杂项，防止路径穿越
                    val leaf = entry.name.substringAfterLast('/').lowercase()
                    val wanted = leaf == "skin.json" || leaf.startsWith("background.")
                    if (!wanted) continue
                    val data = zin.readBytes()
                    total += data.size
                    if (total > MAX_TOTAL_BYTES) error("压缩包解压后超过 12 MB")
                    if (leaf == "skin.json") skinJson = String(data, Charsets.UTF_8)
                    else background = leaf to data
                }
            }
            val sj = skinJson ?: error("压缩包里没有 skin.json")
            File(dir, "skin.json").writeText(sj)
            background?.let { (name, data) -> File(dir, name).writeBytes(data) }
            parse(dir, JSONObject(sj))
        }.onFailure { dir.deleteRecursively() }.getOrThrow()
    }

    private fun parse(dir: File, json: JSONObject): CustomSkin {
        val name = json.optString("name").trim().ifEmpty { "自定义皮肤" }.take(24)
        fun need(key: String): Long =
            parseColor(json.optString(key)) ?: error("$key 缺失或格式不对（应写成 \"#RRGGBB\"）")
        val roundDp = json.optDouble("roundDp", 16.0).toFloat().coerceIn(0f, 32f)
        return CustomSkin(
            id = dir.name,
            name = name,
            accentLight = need("accentLight"),
            accentDark = need("accentDark"),
            surfaceLight = need("surfaceLight"),
            surfaceDark = need("surfaceDark"),
            roundDp = roundDp,
            hasBackground = dir.listFiles()?.any { it.name.startsWith("background.") } == true,
        )
    }

    fun delete(context: Context, id: String): Boolean =
        runCatching { File(root(context), id).deleteRecursively() }.getOrDefault(false)

    /**
     * 从任意一张图片生成皮肤：提取主色 → 推导亮/暗两套配色 → 图片本身存成背景。
     *
     * 这样用户不用手写 JSON，拿壁纸、专辑封面、甚至别的播放器的皮肤截图都能直接用。
     * 取色逻辑复用 [com.yangy.shiyin.ui.CoverColor]，纯本地计算。
     */
    suspend fun fromImage(context: Context, uri: Uri, name: String?): Result<CustomSkin> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("读不到这张图片")
                if (bytes.isEmpty()) error("图片是空的")
                if (bytes.size > 32 * 1024 * 1024) error("图片超过 32 MB")

                // 解码：先量尺寸再按需降采样，避免直接吃进一张超大图
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 1080 || bounds.outHeight / (sample * 2) >= 1920) {
                    sample *= 2
                }
                val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    ?: error("这张图片解不开（格式不支持？）")

                val seed = com.yangy.shiyin.ui.CoverColor.seedFrom(bitmap)
                    ?: run {
                        bitmap.recycle()
                        error("这张图里没有提取到可用的颜色，换一张试试")
                    }
                val palette = paletteFrom(seed)

                val id = newId()
                val dir = File(root(context), id).apply { mkdirs() }
                val skin = CustomSkin(
                    id = id,
                    name = (name?.trim()?.takeIf { it.isNotEmpty() } ?: "图片皮肤").take(24),
                    accentLight = palette.accentLight,
                    accentDark = palette.accentDark,
                    surfaceLight = palette.surfaceLight,
                    surfaceDark = palette.surfaceDark,
                    roundDp = 16f,
                    hasBackground = true,
                )
                File(dir, "skin.json").writeText(
                    """
                    {
                      "name": "${skin.name.replace("\"", "")}",
                      "accentLight": "#${hex(skin.accentLight)}",
                      "accentDark": "#${hex(skin.accentDark)}",
                      "surfaceLight": "#${hex(skin.surfaceLight)}",
                      "surfaceDark": "#${hex(skin.surfaceDark)}",
                      "roundDp": ${skin.roundDp}
                    }
                    """.trimIndent()
                )
                // 背景图单独压一遍（原图可能是 PNG 大图，没必要原样存）
                File(dir, "background.jpg").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it)
                }
                bitmap.recycle()
                parse(dir, JSONObject(File(dir, "skin.json").readText()))
            }
        }

    private class Palette(
        val accentLight: Long,
        val accentDark: Long,
        val surfaceLight: Long,
        val surfaceDark: Long,
    )

    /**
     * 由一个主色推导整套配色。
     * 思路和 Material 的动态取色一致：强调色保持色相、调明度；
     * 背景色用同一色相做极低饱和的浅色/深色，保证文字可读。
     */
    private fun paletteFrom(seed: androidx.compose.ui.graphics.Color): Palette {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
        fun build(hue: Float, saturation: Float, value: Float): Long =
            android.graphics.Color.HSVToColor(
                floatArrayOf(
                    hue,
                    saturation.coerceIn(0f, 1f),
                    value.coerceIn(0f, 1f),
                )
            ).toLong() and 0xFFFFFFFFL

        return Palette(
            // 亮色主题下的强调色：稍微压暗，白底上才看得清
            accentLight = build(hsv[0], hsv[1] * 0.95f, hsv[2] * 0.72f),
            // 暗色主题下的强调色：提亮
            accentDark = build(hsv[0], hsv[1] * 0.62f, (hsv[2] * 1.45f).coerceAtMost(1f)),
            // 浅色背景：同一色相、几乎不饱和
            surfaceLight = build(hsv[0], 0.07f, 0.975f),
            // 深色背景：同一色相、略饱和、很暗
            surfaceDark = build(hsv[0], 0.24f, 0.085f),
        )
    }

    private fun hex(argb: Long): String =
        String.format(java.util.Locale.US, "%06X", argb and 0xFFFFFFL)

    private fun newId(): String =
        "c" + System.currentTimeMillis().toString(36) + Integer.toHexString((0..0xFFFF).random())
}
