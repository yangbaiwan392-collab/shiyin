package com.yangy.shiyin.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color

/**
 * 从封面提取主色。
 * 做法：缩采样后按色相分桶统计，取"饱和且不过暗过亮"的最大桶的平均色，
 * 再校正到可用作前景强调色的明度区间。纯本地计算，不联网。
 */
object CoverColor {

    private const val BUCKETS = 12

    fun seedFrom(bitmap: Bitmap): Color? {
        val scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        if (scaled !== bitmap) scaled.recycle()

        val sums = DoubleArray(BUCKETS * 3)
        val counts = IntArray(BUCKETS)
        val hsv = FloatArray(3)

        for (pixel in pixels) {
            val alpha = pixel ushr 24 and 0xFF
            if (alpha < 200) continue
            android.graphics.Color.colorToHSV(pixel, hsv)
            val hue = hsv[0].coerceIn(0f, 359.99f)
            val saturation = hsv[1]
            val value = hsv[2]
            // 跳过接近黑白的像素，它们给不出有意义的强调色
            if (saturation < 0.18f || value < 0.15f || value > 0.99f) continue
            val bucket = (hue / 360f * BUCKETS).toInt().coerceIn(0, BUCKETS - 1)
            sums[bucket * 3] += android.graphics.Color.red(pixel).toDouble()
            sums[bucket * 3 + 1] += android.graphics.Color.green(pixel).toDouble()
            sums[bucket * 3 + 2] += android.graphics.Color.blue(pixel).toDouble()
            counts[bucket]++
        }

        var best = -1
        var bestScore = 0
        for (bucket in 0 until BUCKETS) {
            // 加权：像素多且色相更暖/更饱和的桶优先
            val score = counts[bucket]
            if (score > bestScore) {
                bestScore = score
                best = bucket
            }
        }
        if (best < 0 || bestScore < pixels.size / 40) return null

        val argb = android.graphics.Color.rgb(
            (sums[best * 3] / bestScore).toInt().coerceIn(0, 255),
            (sums[best * 3 + 1] / bestScore).toInt().coerceIn(0, 255),
            (sums[best * 3 + 2] / bestScore).toInt().coerceIn(0, 255),
        )
        return adjustForUi(argb)
    }

    /** 把取到的颜色拉进可读的明度/饱和度区间，避免过暗或 washed out */
    private fun adjustForUi(argb: Int): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        val saturation = hsv[1].coerceIn(0.42f, 0.86f)
        val value = hsv[2].coerceIn(0.45f, 0.82f)
        return Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], saturation, value)))
    }
}
