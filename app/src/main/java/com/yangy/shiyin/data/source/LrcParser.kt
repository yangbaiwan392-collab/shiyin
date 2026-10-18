package com.yangy.shiyin.data.source

/** 同步歌词，单位毫秒 */
data class LyricLine(val timeMs: Long, val text: String)

object LrcParser {

    private val TAG = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?](.*)""")

    fun parse(raw: String): List<LyricLine> {
        val lines = ArrayList<LyricLine>()
        for (source in raw.lineSequence()) {
            val trimmed = source.trim()
            if (trimmed.isEmpty() || !trimmed.startsWith('[')) continue
            val tags = TAG.findAll(trimmed).toList()
            if (tags.isEmpty()) continue
            val text = tags.last().groupValues[4].trim()
            for (tag in tags) {
                val minute = tag.groupValues[1].toLongOrNull() ?: continue
                val second = tag.groupValues[2].toLongOrNull() ?: continue
                val fraction = tag.groupValues[3]
                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100L
                    2 -> fraction.toLong() * 10L
                    else -> fraction.take(3).toLong()
                }
                lines += LyricLine((minute * 60 + second) * 1000 + millis, text)
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /** 返回当前播放位置对应的行号，-1 表示还没有歌词 */
    fun indexAt(lines: List<LyricLine>, positionMs: Long, offsetMs: Int): Int {
        if (lines.isEmpty()) return -1
        val target = positionMs + offsetMs
        var low = 0
        var high = lines.lastIndex
        var answer = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= target) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer
    }
}
