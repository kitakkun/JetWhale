package com.kitakkun.jetwhale.plugins.storage.agent

/**
 * A directory the host may browse, along with everything below it.
 *
 * @param name Labels the root in the host, and identifies it in requests; keep it unique.
 * @param path The directory's absolute path on the device.
 */
class FileRoot(
    val name: String,
    val path: String,
) {
    companion object
}

/**
 * The absolute path of [segments] below this root. The segments come from the host, so each must
 * be one plain name: nothing may climb out of the root.
 *
 * @throws IllegalArgumentException when a segment is empty, `.`, `..`, or holds a path separator.
 */
internal fun FileRoot.resolve(segments: List<String>): String {
    segments.forEach { segment ->
        require(segment.isNotEmpty() && segment != "." && segment != ".." && '/' !in segment && '\\' !in segment) {
            "'$segment' is not a plain file name"
        }
    }
    return (listOf(path.trimEnd('/')) + segments).joinToString("/")
}

/**
 * The app's own directories on this platform: on Android its data, files and cache directories; on
 * iOS and macOS its home, Documents, Caches and temporary directories; on the JVM the working and
 * temporary directories. None on the web, which has no file system to browse.
 */
expect fun FileRoot.Companion.platformDefaults(): List<FileRoot>
