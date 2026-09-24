package com.kitakkun.jetwhale.plugins.storage.host

/** What a file holds, as far as its name and first bytes tell. */
internal enum class FileKind(val label: String, val isImage: Boolean) {
    PreferencesDataStore("Preferences DataStore", isImage = false),
    Sqlite("SQLite database", isImage = false),
    Png("PNG image", isImage = true),
    Jpeg("JPEG image", isImage = true),
    Gif("GIF image", isImage = true),
    WebP("WebP image", isImage = true),
    Bmp("BMP image", isImage = true),
    Pdf("PDF document", isImage = false),
    Zip("ZIP archive (also JAR, APK)", isImage = false),
    Gzip("gzip-compressed data", isImage = false),
    Json("JSON", isImage = false),
    Xml("XML", isImage = false),
    Text("Plain text", isImage = false),
}

/** The kind of a file named [name] that starts with [bytes], or null when nothing identifies it. */
internal fun fileKindOf(name: String, bytes: ByteArray): FileKind? = when {
    // A Preferences DataStore file is protobuf, which has no signature: only its name tells.
    name.endsWith(PREFERENCES_DATASTORE_SUFFIX) -> FileKind.PreferencesDataStore

    bytes.startsWith("SQLite format 3\u0000".encodeToByteArray()) -> FileKind.Sqlite

    bytes.startsWith(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)) -> FileKind.Png

    bytes.startsWith(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())) -> FileKind.Jpeg

    bytes.startsWith("GIF8".encodeToByteArray()) -> FileKind.Gif

    // RIFF is a container: the format's own tag follows the four-byte chunk size.
    bytes.startsWith("RIFF".encodeToByteArray()) && bytes.size >= 12 && bytes.decodeToString(8, 12) == "WEBP" -> FileKind.WebP

    bytes.startsWith("%PDF-".encodeToByteArray()) -> FileKind.Pdf

    // A local file header, the end record an empty archive consists of, or a spanned archive's marker.
    ZIP_SIGNATURES.any(bytes::startsWith) -> FileKind.Zip

    bytes.startsWith(byteArrayOf(0x1F, 0x8B.toByte())) -> FileKind.Gzip

    else -> textKindOf(bytes) ?: FileKind.Bmp.takeIf { bytes.startsWith("BM".encodeToByteArray()) }
}

// Checked before BMP, whose two-letter signature a text file could start with too.
private fun textKindOf(bytes: ByteArray): FileKind? {
    val text = decodeTextOrNull(bytes) ?: return null
    return when (text.trimStart().firstOrNull()) {
        null -> null
        '{', '[' -> FileKind.Json
        '<' -> FileKind.Xml
        else -> FileKind.Text
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private val ZIP_SIGNATURES: List<ByteArray> = listOf(
    byteArrayOf(0x50, 0x4B, 0x03, 0x04),
    byteArrayOf(0x50, 0x4B, 0x05, 0x06),
    byteArrayOf(0x50, 0x4B, 0x07, 0x08),
)
