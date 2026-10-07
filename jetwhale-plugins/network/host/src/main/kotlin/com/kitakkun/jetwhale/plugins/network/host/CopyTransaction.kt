package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.plugins.network.protocol.BodyEncoding
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.mediaType
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

internal fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

/**
 * The markers the adapters and agent-side redaction record as a TEXT body in place of a body they
 * did not capture. Only these shapes are matched, because a real body can be a single element such
 * as `<br>` or `<root/>`.
 */
private val UNCAPTURED_BODY_MARKER = Regex(
    listOf(
        "<streaming request body>",
        "<streaming response body>",
        "<websocket upgrade>",
        "<Content-Encoding: .+ body>",
        "<.+ body over the \\d+-byte maxImageBytes limit>",
        "<body withheld: .+>",
        "<$MEDIA_TYPE_TOKEN/$MEDIA_TYPE_TOKEN(?:; .+)?>",
    ).joinToString("|"),
)

/** An RFC 9110 token, the shape of a media type's type and subtype. */
private const val MEDIA_TYPE_TOKEN = "[!#\$%&'*+.^_`|~0-9A-Za-z-]+"

/** [body] as text to copy, or null when it is absent, a binary (Base64) capture or an uncaptured-body marker. */
internal fun copyableBody(body: String?, encoding: BodyEncoding): String? = body?.takeUnless { encoding == BodyEncoding.BASE64 || it.matches(UNCAPTURED_BODY_MARKER) }

/**
 * Rebuilds the captured request as a shell-pasteable `curl` command.
 *
 * Content-Length is dropped (curl derives it from the body). A truncated capture can't be
 * replayed faithfully, so it's flagged with a leading comment instead of silently emitting
 * a partial body; an uncaptured-body marker and a binary (Base64) capture are omitted entirely and
 * flagged the same way.
 */
internal fun buildCurlCommand(request: CapturedHttpRequest): String {
    val binary = request.bodyEncoding == BodyEncoding.BASE64
    val body = copyableBody(request.body, request.bodyEncoding)
    // --globoff: curl expands [] and {} in URLs itself, even inside shell quotes.
    val lines = mutableListOf("curl --globoff")
    // -X GET must be explicit when a body is present, or --data-raw switches the method to POST.
    if (!request.method.equals("GET", ignoreCase = true) || body != null) {
        lines += "-X ${request.method.uppercase()}"
    }
    lines += "'${request.url.escapeSingleQuotes()}'"
    request.headers.forEach { (name, values) ->
        if (name.equals("Content-Length", ignoreCase = true)) return@forEach
        values.forEach { value ->
            lines += "-H '${"$name: $value".escapeSingleQuotes()}'"
        }
    }
    body?.let {
        // --data-raw, not --data: a body starting with @ must not be read as a file reference.
        lines += "--data-raw '${it.escapeSingleQuotes()}'"
    }
    val command = lines.joinToString(" \\\n  ")
    val note = when {
        binary -> "# NOTE: request body was captured as binary (${request.headers.mediaType() ?: "unknown media type"}); the command omits it\n"
        body == null && request.body != null -> "# NOTE: request body was not captured (${request.body}); the command omits it\n"
        request.bodyTruncated -> "# NOTE: request body was truncated at capture time\n"
        else -> ""
    }
    return note + command
}

private fun String.escapeSingleQuotes(): String = replace("'", "'\\''")
