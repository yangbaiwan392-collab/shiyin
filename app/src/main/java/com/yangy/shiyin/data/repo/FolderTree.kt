package com.yangy.shiyin.data.repo

import com.yangy.shiyin.data.entity.Track

/**
 * 目录树节点。
 * path 为从卷根开始的完整目录；label 可能已把只含单一子目录的链条合并显示。
 */
data class FolderNode(
    val path: String,
    val label: String,
    val directTracks: List<Track>,
    val children: List<FolderNode>,
) {
    val allTracks: List<Track>
        get() = if (children.isEmpty()) directTracks else directTracks + children.flatMap { it.allTracks }

    val totalBytes: Long get() = allTracks.sumOf { it.sizeBytes }

    val displayName: String get() = label.substringAfterLast('/')
}

object FolderTree {

    private class Node(var label: String, var path: String) {
        val kids = LinkedHashMap<String, Node>()
        val tracks = ArrayList<Track>()
    }

    fun build(tracks: List<Track>): List<FolderNode> {
        if (tracks.isEmpty()) return emptyList()
        val roots = LinkedHashMap<String, Node>()

        for (track in tracks) {
            val segments = track.folderPath.split('/').map { it.trim() }.filter { it.isNotEmpty() }
            var level = roots
            var accumulated = ""
            var chain = ArrayList<Node>()
            for (segment in segments) {
                accumulated = if (accumulated.isEmpty()) segment else "$accumulated/$segment"
                val node = level.getOrPut(segment) { Node(segment, accumulated) }
                chain.add(node)
                level = node.kids
            }
            if (chain.isEmpty()) {
                // 没有目录信息，归到未分类
                val other = level.getOrPut("未分类") { Node("未分类", "未分类") }
                other.tracks.add(track)
            } else {
                chain.last().tracks.add(track)
            }
        }

        return roots.values.map { fold(it) }
    }

    /** 递归折叠：把"只有一个子目录且自身没有直属文件"的链条合并成一段显示 */
    private fun fold(node: Node): FolderNode {
        var label = node.label
        var path = node.path
        var current = node
        while (current.tracks.isEmpty() && current.kids.size == 1) {
            val only = current.kids.values.first()
            label = "$label/${only.label}"
            path = only.path
            current = only
        }
        return FolderNode(
            path = path,
            label = label,
            directTracks = current.tracks.toList(),
            children = current.kids.values.map { fold(it) }.sortedBy { it.displayName.lowercase() },
        )
    }
}

/** 在树里按完整路径找到某个节点 */
fun findNode(nodes: List<FolderNode>, path: String): FolderNode? {
    for (node in nodes) {
        if (node.path == path) return node
        val deeper = findNode(node.children, path)
        if (deeper != null) return deeper
    }
    return null
}
