package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

internal fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

/**
 * Rebuilds the captured request as a shell-pasteable `curl` command.
 *
 * Content-Length is dropped (curl derives it from the body). A truncated capture can't be
 * replayed faithfully, so it's flagged with a leading comment instead of silently emitting
 * a partial body.
 */
internal fun buildCurlCommand(request: CapturedHttpRequest): String {
    // --globoff: curl expands [] and {} in URLs itself, even inside shell quotes.
    val lines = mutableListOf("curl --globoff")
    // -X GET must be explicit when a body is present, or --data-raw switches the method to POST.
    if (!request.method.equals("GET", ignoreCase = true) || request.body != null) {
        lines += "-X ${request.method.uppercase()}"
    }
    lines += "'${request.url.escapeSingleQuotes()}'"
    request.headers.forEach { (name, values) ->
        if (name.equals("Content-Length", ignoreCase = true)) return@forEach
        values.forEach { value ->
            lines += "-H '${"$name: $value".escapeSingleQuotes()}'"
        }
    }
    request.body?.let { body ->
        // --data-raw, not --data: a body starting with @ must not be read as a file reference.
        lines += "--data-raw '${body.escapeSingleQuotes()}'"
    }
    val command = lines.joinToString(" \\\n  ")
    return if (request.bodyTruncated) "# NOTE: request body was truncated at capture time\n$command" else command
}

private fun String.escapeSingleQuotes(): String = replace("'", "'\\''")
