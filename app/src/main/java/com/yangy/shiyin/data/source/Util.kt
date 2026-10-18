package com.yangy.shiyin.data.source

/** 稳定的 64 位 FNV-1a 哈希，用作曲库主键，避免 String.hashCode 的 32 位碰撞 */
fun String.stableId(): Long {
    var hash = -0x34095f354a8faa5bL
    for (ch in this) {
        hash = hash xor ch.code.toLong()
        hash *= 0x100000001b3L
    }
    return hash
}

object PathUtil {

    /**
     * 归一化目录路径。
     * MediaStore 的 RELATIVE_PATH 本身就是"所在目录"（形如 Music/蓝调/），
     * 不能再剥掉最后一段，否则所有子分类会被压平到上一层。
     */
    fun normalizeFolder(volumeName: String, relativePath: String): String {
        val cleaned = relativePath.trim('/', '\\')
        return if (volumeName.isBlank() || cleaned.isBlank()) {
            cleaned.ifBlank { volumeName.ifBlank { "内部存储" } }
        } else {
            "$volumeName/$cleaned"
        }
    }

    fun fileNameOf(displayName: String): String = displayName.substringAfterLast('/')

    fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase()
}
