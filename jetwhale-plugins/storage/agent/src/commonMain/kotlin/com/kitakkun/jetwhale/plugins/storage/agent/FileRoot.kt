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
 * The absolute path of [segments] below this root. The segments come from the host, so nothing
 * may climb out of the root: each must be one plain name, and the path it leads to must still be
 * inside the root once symbolic links are resolved.
 *
 * @throws IllegalArgumentException when a segment is empty, `.`, `..`, or holds a path separator,
 *   or when a symbolic link leads outside the root.
 */
internal fun FileRoot.resolve(segments: List<String>): String {
    segments.forEach { segment ->
        require(segment.isNotEmpty() && segment != "." && segment != ".." && '/' !in segment && '\\' !in segment) {
            "'$segment' is not a plain file name"
        }
    }
    val base = path.trimEnd('/')
    if (segments.isEmpty()) return base.ifEmpty { "/" }
    val resolved = "$base/${segments.joinToString("/")}"
    require(resolvesInside(resolved, root = path)) {
        "'${segments.joinToString("/")}' leads outside the root through a symbolic link"
    }
    return resolved
}

/** Where an upload is collected, and the file it replaces once complete. */
internal class UploadPaths(val staging: String, val target: String)

/**
 * The paths an upload named [uploadId] writes to for the file at [segments]: the target, and a
 * staging file beside it, so the final move stays on one file system. Both go through [resolve].
 *
 * @throws IllegalArgumentException for the root itself, an [uploadId] that is not letters, digits
 *   and `-`, or a path [resolve] refuses.
 */
internal fun FileRoot.uploadPaths(segments: List<String>, uploadId: String): UploadPaths {
    require(segments.isNotEmpty()) { "a file root cannot be written over, only a file inside it" }
    require(UPLOAD_ID.matches(uploadId)) { "'$uploadId' is not an upload id; use letters, digits and '-'" }
    val staging = segments.dropLast(1) + ".${segments.last()}.jetwhale-upload-$uploadId"
    return UploadPaths(staging = resolve(staging), target = resolve(segments))
}

private val UPLOAD_ID = Regex("[A-Za-z0-9-]{1,64}")

/**
 * The app's own directories on this platform: on Android its data, files and cache directories; on
 * iOS and macOS its home, Documents, Caches and temporary directories; on the JVM the working and
 * temporary directories. None on the web, which has no file system to browse.
 */
expect fun FileRoot.Companion.platformDefaults(): List<FileRoot>
