package com.kitakkun.jetwhale.host.sdk

import kotlinx.serialization.json.JsonObject

/**
 * One block of a tool result, in the order the AI agent reads them.
 *
 * JetWhale owns this hierarchy instead of handing plugins the MCP library's own content types, so a
 * plugin keeps compiling and loading when the host updates that library.
 */
@ExperimentalJetWhaleApi
public sealed interface JetWhaleMcpContent {
    /** Text the agent reads verbatim: prose, or a JSON document that is already serialized. */
    public data class Text(val text: String) : JetWhaleMcpContent

    /**
     * An image the agent can look at.
     *
     * @param base64Data The image bytes, Base64-encoded, with no `data:` URI prefix.
     * @param mimeType   The image's MIME type, e.g. `image/png`.
     */
    public data class Image(val base64Data: String, val mimeType: String) : JetWhaleMcpContent
}

/**
 * What a [JetWhaleMcpCommand] hands back to the AI agent.
 *
 * Build one through the companion's factories rather than the constructor — that is what keeps a
 * command source-compatible when the result gains a way to say something new:
 * ```kotlin
 * JetWhaleMcpResult.text("3 widgets are selected")
 * JetWhaleMcpResult.json(buildJsonObject { put("selectedCount", 3) })
 * JetWhaleMcpResult.image(base64Data = png, mimeType = "image/png")
 * JetWhaleMcpResult.error("no widget with id: $id")
 * ```
 * A command whose result is always plain text can extend [JetWhaleMcpTextCommand] and skip the
 * wrapping entirely.
 *
 * @property content          The blocks the agent reads, in order.
 * @property structuredContent A machine-readable payload delivered next to [content]. Agents that
 *   understand it read it instead of parsing the text.
 * @property isError          Whether the call failed. A failed call is one the agent should correct
 *   and retry, not an answer — so it must be reported here rather than as text that happens to
 *   mention a problem.
 */
@ExperimentalJetWhaleApi
// Not a data class: the generated copy() and componentN would be public even though the constructor
// is internal, which would hand plugins the very bypass the factories exist to prevent.
public class JetWhaleMcpResult internal constructor(
    public val content: List<JetWhaleMcpContent>,
    public val structuredContent: JsonObject?,
    public val isError: Boolean,
) {
    override fun equals(other: Any?): Boolean = this === other ||
        (
            other is JetWhaleMcpResult &&
                content == other.content &&
                structuredContent == other.structuredContent &&
                isError == other.isError
            )

    override fun hashCode(): Int {
        var result = content.hashCode()
        result = 31 * result + structuredContent.hashCode()
        result = 31 * result + isError.hashCode()
        return result
    }

    override fun toString(): String = "JetWhaleMcpResult(content=$content, structuredContent=$structuredContent, isError=$isError)"

    public companion object {
        /** A successful result carrying [text] — prose, or JSON the command serialized itself. */
        public fun text(text: String): JetWhaleMcpResult = JetWhaleMcpResult(
            content = listOf(JetWhaleMcpContent.Text(text)),
            structuredContent = null,
            isError = false,
        )

        /**
         * A successful structured result. [json] is delivered as the call's structured content and
         * repeated as a text block, so an agent that reads only text still gets the whole answer.
         */
        public fun json(json: JsonObject): JetWhaleMcpResult = JetWhaleMcpResult(
            content = listOf(JetWhaleMcpContent.Text(json.toString())),
            structuredContent = json,
            isError = false,
        )

        /** A successful result carrying a single image. @see JetWhaleMcpContent.Image */
        public fun image(base64Data: String, mimeType: String): JetWhaleMcpResult = JetWhaleMcpResult(
            content = listOf(JetWhaleMcpContent.Image(base64Data = base64Data, mimeType = mimeType)),
            structuredContent = null,
            isError = false,
        )

        /**
         * A failed call. [message] says what went wrong, and the result is flagged so the agent
         * treats it as a failure to correct rather than as the tool's answer.
         *
         * Throwing [JetWhaleMcpException] produces the same thing, and is the shorter path when the
         * failure is discovered deep inside the command.
         */
        public fun error(message: String): JetWhaleMcpResult = JetWhaleMcpResult(
            content = listOf(JetWhaleMcpContent.Text(message)),
            structuredContent = null,
            isError = true,
        )
    }
}
