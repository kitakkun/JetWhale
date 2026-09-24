package com.kitakkun.jetwhale.plugins.storage.host

/** The first bytes of a file as read from the agent, with the file's whole size. */
internal class LoadedFile(
    val location: FileLocation,
    val bytes: ByteArray,
    val totalSizeBytes: Long,
) {
    val isTruncated: Boolean get() = bytes.size < totalSizeBytes
}

/** The ways the file pane can render a file's bytes. */
internal enum class PreviewFormat(val label: String) {
    Preferences("Preferences"),
    Image("Image"),
    Text("Text"),
    Hex("Hex"),
}

/**
 * The formats [file] can be shown in, the most specific first: a Preferences DataStore file is
 * decoded, an image is drawn, anything that reads as text is text, and every file has a hex dump.
 */
internal fun previewFormatsOf(file: LoadedFile): List<PreviewFormat> = buildList {
    // A truncated DataStore file cannot be decoded: its last entry would be cut in half.
    if (file.location.name.endsWith(PREFERENCES_DATASTORE_SUFFIX) && !file.isTruncated) add(PreviewFormat.Preferences)
    if (IMAGE_SIGNATURES.any(file.bytes::startsWith)) add(PreviewFormat.Image)
    if (decodeTextOrNull(file.bytes) != null) add(PreviewFormat.Text)
    add(PreviewFormat.Hex)
}

/** Sixteen bytes a line: offset, hex, and the printable ASCII of the same bytes. */
internal fun hexDump(bytes: ByteArray): String = bytes.asList().chunked(HEX_DUMP_WIDTH).withIndex().joinToString("\n") { (line, chunk) ->
    val offset = (line * HEX_DUMP_WIDTH).toString(16).padStart(8, '0')
    val hex = chunk.joinToString(" ") { it.toUByte().toString(16).padStart(2, '0') }.padEnd(HEX_DUMP_WIDTH * 3 - 1)
    val ascii = chunk.joinToString("") { byte -> if (byte in 0x20..0x7E) byte.toInt().toChar().toString() else "." }
    "$offset  $hex  $ascii"
}

private const val HEX_DUMP_WIDTH = 16

private val IMAGE_SIGNATURES: List<ByteArray> = listOf(
    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), // PNG
    byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()), // JPEG
    "GIF8".encodeToByteArray(),
    "RIFF".encodeToByteArray(), // WebP, whose "WEBP" tag follows the RIFF size
    "BM".encodeToByteArray(),
)

private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

/**
 * [bytes] as text, or null when they are not UTF-8 or hold control characters other than
 * whitespace. A read cut short may end in the middle of a character, so up to three malformed
 * trailing bytes are dropped rather than failing the whole file.
 */
internal fun decodeTextOrNull(bytes: ByteArray): String? {
    val decoded = (0..minOf(3, bytes.size)).firstNotNullOfOrNull { dropped ->
        try {
            bytes.decodeToString(0, bytes.size - dropped, throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            null
        }
    } ?: return null
    return decoded.takeIf { text -> text.none { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' } }
}
