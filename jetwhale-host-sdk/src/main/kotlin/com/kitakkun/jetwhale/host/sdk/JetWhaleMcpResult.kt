package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi

/** One block of an MCP tool result. */
@ExperimentalJetWhaleApi
public sealed interface JetWhaleMcpContent {
    /** Plain text or JSON, shown to the AI agent as-is. */
    public class Text(public val text: String) : JetWhaleMcpContent

    /** Raw image bytes; the host base64-encodes them for the MCP protocol. */
    public class Image(
        public val data: ByteArray,
        public val mimeType: String,
    ) : JetWhaleMcpContent
}

/**
 * What a [JetWhaleMcpCommand] returns: one or more content blocks. Most commands answer with a
 * single block, for which [text] and [image] are the shorthands.
 */
@ExperimentalJetWhaleApi
public class JetWhaleMcpResult(
    public val content: List<JetWhaleMcpContent>,
) {
    public companion object {
        public fun text(text: String): JetWhaleMcpResult = JetWhaleMcpResult(listOf(JetWhaleMcpContent.Text(text)))

        public fun image(data: ByteArray, mimeType: String): JetWhaleMcpResult = JetWhaleMcpResult(listOf(JetWhaleMcpContent.Image(data, mimeType)))
    }
}
