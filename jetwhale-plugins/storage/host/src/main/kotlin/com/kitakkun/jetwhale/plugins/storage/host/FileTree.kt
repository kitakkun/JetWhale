package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.FileRootInfo

/** A file root, or a file or directory below one: [path] holds the segments under the root. */
internal data class FileLocation(val rootName: String, val path: List<String>) {
    val name: String get() = path.lastOrNull() ?: rootName

    fun child(name: String): FileLocation = FileLocation(rootName, path + name)

    /** True when [other] is this location or lies below it. */
    operator fun contains(other: FileLocation): Boolean = other.rootName == rootName && other.path.size >= path.size && other.path.subList(0, path.size) == path
}

/**
 * One visible line of the file tree.
 *
 * @property entry What the agent reported for this file or directory; null for a root, which is
 *   never listed as an entry of anything.
 * @property expanded Whether the directory's content shows below it; always false for a file.
 */
internal data class FileTreeRow(
    val location: FileLocation,
    val depth: Int,
    val entry: FileEntry?,
    val expanded: Boolean,
) {
    val isDirectory: Boolean get() = entry?.isDirectory ?: true
}

/**
 * The rows the tree shows: every root, and below each expanded directory whose content has been
 * loaded, that content — depth first, in the order the agent listed it.
 */
internal fun flattenFileTree(
    roots: List<FileRootInfo>,
    children: Map<FileLocation, List<FileEntry>>,
    expanded: Set<FileLocation>,
): List<FileTreeRow> = buildList {
    fun addSubtree(location: FileLocation, depth: Int, entry: FileEntry?) {
        val isExpanded = location in expanded && entry?.isDirectory != false
        add(FileTreeRow(location = location, depth = depth, entry = entry, expanded = isExpanded))
        if (!isExpanded) return
        children[location].orEmpty().forEach { child -> addSubtree(location.child(child.name), depth + 1, child) }
    }
    roots.forEach { root -> addSubtree(FileLocation(root.name, emptyList()), depth = 0, entry = null) }
}
