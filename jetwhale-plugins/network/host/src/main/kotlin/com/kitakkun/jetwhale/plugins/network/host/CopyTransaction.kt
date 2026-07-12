package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

internal fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

/**
 * Adapters record bodies they can't read (streaming/binary) as a single `<...>` marker such as
 * `<streaming request body>` or `<application/json>`. Real XML/HTML bodies contain nested tags
 * with more than one `<`/`>`, so they don't match.
 */
private val PLACEHOLDER_BODY = Regex("^<[^<>]+>$")

/**
 * Rebuilds the captured request as a shell-pasteable `curl` command.
 *
 * Content-Length is dropped (curl derives it from the body). A truncated capture can't be
 * replayed faithfully, so it's flagged with a leading comment instead of silently emitting
 * a partial body; a placeholder body is omitted entirely and flagged the same way.
 */
internal fun buildCurlCommand(request: CapturedHttpRequest): String {
    val body = request.body?.takeUnless { it.matches(PLACEHOLDER_BODY) }
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
        body == null && request.body != null -> "# NOTE: request body was not captured (${request.body}); the command omits it\n"
        request.bodyTruncated -> "# NOTE: request body was truncated at capture time\n"
        else -> ""
    }
    return note + command
}

private fun String.escapeSingleQuotes(): String = replace("'", "'\\''")
