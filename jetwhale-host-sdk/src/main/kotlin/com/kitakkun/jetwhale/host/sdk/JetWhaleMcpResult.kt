package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import kotlinx.serialization.json.JsonObject

/**
 * One block of a tool result, in the order the AI agent reads them.
 *
 * JetWhale owns this hierarchy instead of handing plugins the MCP library's own content types, so a
 * plugin keeps compiling and loading when the host updates that library.
 */
@ExperimentalJetWhaleApi
public sealed interface JetWhaleMcpContent {
    /**
     * Text the agent reads verbatim: prose, or a JSON document that is already serialized.
     *
     * @property text The text itself.
     */
    public class Text internal constructor(public val text: String) : JetWhaleMcpContent {
        override fun equals(other: Any?): Boolean = other is Text && text == other.text

        override fun hashCode(): Int = text.hashCode()

        override fun toString(): String = "Text(text=$text)"
    }

    /**
     * An image the agent can look at.
     *
     * @property data The encoded image, e.g. the bytes of a PNG file. The host Base64-encodes it for
     *   the wire.
     * @property mimeType The image's MIME type, e.g. `image/png`.
     */
    public class Image internal constructor(
        public val data: ByteArray,
        public val mimeType: String,
    ) : JetWhaleMcpContent {
        override fun equals(other: Any?): Boolean = other is Image && data.contentEquals(other.data) && mimeType == other.mimeType

        override fun hashCode(): Int = 31 * data.contentHashCode() + mimeType.hashCode()

        override fun toString(): String = "Image(mimeType=$mimeType, size=${data.size} bytes)"
    }
}

/**
 * What a [JetWhaleMcpCommand] hands back to the AI agent.
 *
 * Build one through the companion's factories rather than a constructor, so that a command keeps
 * compiling when the result gains a way to say something new:
 * ```kotlin
 * JetWhaleMcpResult.text("3 widgets are selected")
 * JetWhaleMcpResult.json(buildJsonObject { put("selectedCount", 3) })
 * JetWhaleMcpResult.image(data = pngBytes, mimeType = "image/png")
 * JetWhaleMcpResult.error("no widget with id: $id")
 * ```
 * [withImage] adds an image to any of them, for an answer that is both data and a picture. A
 * command whose result is always plain text can extend [JetWhaleMcpTextCommand] and skip the
 * wrapping entirely.
 *
 * @property content The blocks the agent reads, in order.
 * @property structuredContent A machine-readable payload delivered next to [content]. Agents that
 *   understand it read it instead of parsing the text.
 * @property isError Whether the call failed. A failed call is one the agent should correct and
 *   retry, not an answer, so it must be reported here rather than as text that happens to mention
 *   a problem.
 */
@ExperimentalJetWhaleApi
public class JetWhaleMcpResult internal constructor(
    public val content: List<JetWhaleMcpContent>,
    public val structuredContent: JsonObject?,
    public val isError: Boolean,
    internal val output: JetWhaleMcpOutput<*>?,
) {
    /**
     * This result with an image appended to its [content], e.g. a screenshot next to the JSON that
     * says where it was saved. A result built from a declared output stays one.
     *
     * @param data The encoded image, e.g. the bytes of a PNG file.
     * @param mimeType The image's MIME type, e.g. `image/png`.
     */
    public fun withImage(data: ByteArray, mimeType: String): JetWhaleMcpResult = JetWhaleMcpResult(
        content = content + JetWhaleMcpContent.Image(data = data, mimeType = mimeType),
        structuredContent = structuredContent,
        isError = isError,
        output = output,
    )

    override fun equals(other: Any?): Boolean = other is JetWhaleMcpResult &&
        content == other.content &&
        structuredContent == other.structuredContent &&
        isError == other.isError

    override fun hashCode(): Int {
        var result = content.hashCode()
        result = 31 * result + structuredContent.hashCode()
        result = 31 * result + isError.hashCode()
        return result
    }

    override fun toString(): String = "JetWhaleMcpResult(content=$content, structuredContent=$structuredContent, isError=$isError)"

    public companion object {
        /** A successful result carrying [text]: prose, or JSON the command serialized itself. */
        public fun text(text: String): JetWhaleMcpResult = JetWhaleMcpResult(
            content = listOf(JetWhaleMcpContent.Text(text)),
            structuredContent = null,
            isError = false,
            output = null,
        )

        /**
         * A successful structured result. [json] is delivered as the call's structured content and
         * repeated as a text block, so an agent that reads only text still gets the whole answer.
         */
        public fun json(json: JsonObject): JetWhaleMcpResult = JetWhaleMcpResult(
            content = listOf(JetWhaleMcpContent.Text(json.toString())),
            structuredContent = json,
            isError = false,
            output = null,
        )

        /**
         * A successful result carrying a single image.
         *
         * @param data The encoded image, e.g. the bytes of a PNG file.
         * @param mimeType The image's MIME type, e.g. `image/png`.
         */
        public fun image(data: ByteArray, mimeType: String): JetWhaleMcpResult = JetWhaleMcpResult(
            content = listOf(JetWhaleMcpContent.Image(data = data, mimeType = mimeType)),
            structuredContent = null,
            isError = false,
            output = null,
        )

        /**
         * A failed call. [message] says what went wrong, and the result is flagged so the agent
         * treats it as a failure to correct rather than as the tool's answer.
         *
         * Throwing [JetWhaleMcpException] produces the same result, and is the shorter path when
         * the failure is found deep inside the command.
         */
        public fun error(message: String): JetWhaleMcpResult = JetWhaleMcpResult(
            content = listOf(JetWhaleMcpContent.Text(message)),
            structuredContent = null,
            isError = true,
            output = null,
        )
    }
}
