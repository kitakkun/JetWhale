package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.semantics.protocol.GetViewAttributes
import com.kitakkun.jetwhale.plugins.semantics.protocol.SetViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResponse
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeValue
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// The two attribute tools. They sit apart from the tree tools because they address one View node
// rather than the tree, and because only View nodes have anything to answer.

private const val TEMPORARY_NOTICE =
    "Only Android View nodes (the ones findNodes marks \"kind\": \"View\", with a negative id) have attributes: a Compose " +
        "node's semantics are a projection of composition state, so writing to one would be undone by the next recomposition. " +
        "An edit is temporary — a relayout, a rebind, or the app writing the property itself takes the value back."

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetViewAttributesCommand(
    private val getAttributes: suspend (GetViewAttributes) -> ViewAttributeResponse,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getViewAttributes"
    override val description =
        "Reads the platform attributes of one Android View node — visibility, layout size, padding, margins, alpha, " +
            "background color, text, text size and color — as {\"rootId\", \"nodeId\", \"viewClass\", \"attributes\": " +
            "[{\"id\", \"label\", \"group\", \"type\", \"value\", \"options\", \"editable\"}]}. \"id\" is what " +
            "setViewAttribute names, and \"type\" says how to write it: bool (\"true\"), int (\"24\"), float (\"0.5\"), " +
            "text, color (\"#AARRGGBB\"), dimension (pixels, \"48\"), enum (one of \"options\") or layoutSize — " +
            "layout.width / layout.height, which take either one of \"constants\" (\"WRAP_CONTENT\", \"MATCH_PARENT\") " +
            "or a pixel figure, whichever the value currently reads as. \"editable\": false " +
            "marks a read-only attribute. Answers {\"message\"} instead when the node has no attributes. " +
            TEMPORARY_NOTICE

    private val rootId by string("The root the node belongs to, as reported by findNodes or getNodeTree.")
    private val nodeId by int("The View node's id, as reported by findNodes or getNodeTree. It is negative.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val rootId = arguments[rootId]
        val nodeId = arguments[nodeId]
        val response = try {
            getAttributes(GetViewAttributes(rootId = rootId, nodeId = nodeId))
        } catch (e: JetWhaleMessagingException) {
            return agentErrorJson(e)
        }
        val snapshot = response.snapshot
            ?: return buildJsonObject {
                put("rootId", rootId)
                put("nodeId", nodeId)
                put("message", response.message ?: "this node has no View attributes")
            }.toString()

        return buildJsonObject {
            put("rootId", snapshot.rootId)
            put("nodeId", snapshot.nodeId)
            put("viewClass", snapshot.viewClass)
            put("attributes", JsonArray(snapshot.attributes.map { it.toMcpJson() }))
        }.toString()
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class SetViewAttributeCommand(
    private val getAttributes: suspend (GetViewAttributes) -> ViewAttributeResponse,
    private val setAttribute: suspend (SetViewAttribute) -> ViewAttributeResult,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.setViewAttribute"
    override val description =
        "Changes one attribute of one Android View node in the running app and returns " +
            "{\"applied\", \"rootId\", \"nodeId\", \"attributeId\", \"message\", \"attribute\"}, where \"attribute\" is the " +
            "value as it reads back afterwards — an app may clamp or ignore what was asked for. The value is given as a " +
            "string and parsed for the attribute's own type: \"GONE\", \"true\", \"0.5\", \"#80FF0000\", \"24\". " +
            "Call getViewAttributes first to see the ids, types and enum options. " + TEMPORARY_NOTICE

    private val rootId by string("The root the node belongs to, as reported by findNodes or getNodeTree.")
    private val nodeId by int("The View node's id, as reported by findNodes or getNodeTree. It is negative.")
    private val attributeId by string("The attribute's \"id\" from getViewAttributes, e.g. visibility, text, padding.left, layout.width.")
    private val value by string(
        "The new value as a string, read according to the attribute's type: bool \"true\"/\"false\", int \"24\", float \"0.5\", " +
            "text as-is, color \"#AARRGGBB\" or \"#RRGGBB\", dimension as a pixel figure \"48\", enum as one of the options " +
            "getViewAttributes listed, e.g. \"GONE\", layoutSize as one of the constants it listed — \"wrap_content\", " +
            "\"match_parent\" — or a pixel figure \"500\".",
    )

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val rootId = arguments[rootId]
        val nodeId = arguments[nodeId]
        val attributeId = arguments[attributeId]
        val text = arguments[value]

        val result = try {
            // The current value is what says how to read the string, so the write always starts from
            // a fresh read; it also means an unknown id is caught here, where the known ids can be listed.
            val response = getAttributes(GetViewAttributes(rootId = rootId, nodeId = nodeId))
            val snapshot = response.snapshot
                ?: return errorJson(response.message ?: "this node has no View attributes")
            val current = snapshot.attributes.firstOrNull { it.id == attributeId }
                ?: throw JetWhaleMcpArgumentException(
                    "unknown attributeId: $attributeId (this ${snapshot.viewClass} exposes ${snapshot.attributes.joinToString { it.id }})",
                )
            if (!current.editable) throw JetWhaleMcpArgumentException("$attributeId is read-only on a ${snapshot.viewClass}")

            setAttribute(
                SetViewAttribute(
                    rootId = rootId,
                    nodeId = nodeId,
                    attributeId = attributeId,
                    value = parseViewAttributeValue(attributeId, current.value, text),
                ),
            )
        } catch (e: JetWhaleMessagingException) {
            return agentErrorJson(e)
        }

        return buildJsonObject {
            put("applied", result.applied)
            put("rootId", rootId)
            put("nodeId", nodeId)
            put("attributeId", attributeId)
            result.message?.let { put("message", it) }
            result.attribute?.let { put("attribute", it.toMcpJson()) }
        }.toString()
    }
}

/**
 * Renders one attribute for an AI agent: the value flattened to a string of the same shape the
 * setter takes, so a value read here can be written back without being reshaped.
 */
internal fun ViewAttribute.toMcpJson(): JsonObject = buildJsonObject {
    val current = value
    put("id", id)
    put("label", label)
    put("group", group)
    put("type", current.typeName)
    put("value", current.asText())
    when (current) {
        is ViewAttributeValue.EnumValue -> put("options", JsonArray(current.options.map { JsonPrimitive(it) }))

        // The pixel figure is authoritative; the dp figure is what makes it recognisable.
        is ViewAttributeValue.DimensionValue -> put("dp", current.dp)

        // Both of what this takes are spelled out whichever one it currently reads as: the
        // constants it accepts, and — when it is a length — that length in dp as well.
        is ViewAttributeValue.LayoutSizeValue -> {
            put("constants", JsonArray(current.constants.map { JsonPrimitive(it) }))
            current.dp?.let { put("dp", it) }
        }

        else -> Unit
    }
    // Most attributes can be written; the read-only ones are the ones worth pointing out.
    if (!editable) put("editable", false)
}

/** The `type` an agent sees, and the name [parseViewAttributeValue] reads a string under. */
internal val ViewAttributeValue.typeName: String
    get() = when (this) {
        is ViewAttributeValue.BooleanValue -> "bool"
        is ViewAttributeValue.IntValue -> "int"
        is ViewAttributeValue.FloatValue -> "float"
        is ViewAttributeValue.TextValue -> "text"
        is ViewAttributeValue.ColorValue -> "color"
        is ViewAttributeValue.DimensionValue -> "dimension"
        is ViewAttributeValue.EnumValue -> "enum"
        is ViewAttributeValue.LayoutSizeValue -> "layoutSize"
    }

/** The value as the string an agent both reads and writes it as. */
internal fun ViewAttributeValue.asText(): String = when (this) {
    is ViewAttributeValue.BooleanValue -> value.toString()
    is ViewAttributeValue.IntValue -> value.toString()
    is ViewAttributeValue.FloatValue -> value.toString()
    is ViewAttributeValue.TextValue -> value
    is ViewAttributeValue.ColorValue -> formatArgb(argb)
    is ViewAttributeValue.DimensionValue -> px.toString()
    is ViewAttributeValue.EnumValue -> value
    is ViewAttributeValue.LayoutSizeValue -> constant ?: px?.toString() ?: ""
}

/**
 * Reads [text] as a new value for an attribute whose value currently reads as [current] — the
 * current variant is what an agent would otherwise have to reconstruct as sealed JSON by hand.
 *
 * The variant is fixed per attribute, so what each branch accepts is the whole of what the attribute
 * takes: a dimension is a number and nothing else, an enum is one of its options, and the one
 * attribute that takes either a name or a number — a layout size — says so in its own variant.
 */
@OptIn(ExperimentalJetWhaleApi::class)
internal fun parseViewAttributeValue(attributeId: String, current: ViewAttributeValue, text: String): ViewAttributeValue = when (current) {
    is ViewAttributeValue.BooleanValue -> ViewAttributeValue.BooleanValue(
        text.trim().toBooleanStrictOrNull() ?: invalidValue(attributeId, text, "true or false"),
    )

    is ViewAttributeValue.IntValue -> ViewAttributeValue.IntValue(
        text.trim().toIntOrNull() ?: invalidValue(attributeId, text, "a whole number"),
    )

    is ViewAttributeValue.FloatValue -> ViewAttributeValue.FloatValue(
        text.trim().toFloatOrNull() ?: invalidValue(attributeId, text, "a number"),
    )

    is ViewAttributeValue.TextValue -> ViewAttributeValue.TextValue(text)

    is ViewAttributeValue.ColorValue -> ViewAttributeValue.ColorValue(
        parseArgb(text) ?: invalidValue(attributeId, text, "a color as #AARRGGBB or #RRGGBB"),
    )

    is ViewAttributeValue.DimensionValue -> {
        val px = text.trim().toFloatOrNull() ?: invalidValue(attributeId, text, "a length in pixels")
        ViewAttributeValue.DimensionValue(px = px, dp = px)
    }

    is ViewAttributeValue.EnumValue -> ViewAttributeValue.EnumValue(
        value = current.options.firstOrNull { it.equals(text.trim(), ignoreCase = true) }
            ?: invalidValue(attributeId, text, "one of ${current.options.joinToString(", ")}"),
        options = current.options,
    )

    is ViewAttributeValue.LayoutSizeValue -> {
        val constant = current.constants.firstOrNull { it.equals(text.trim(), ignoreCase = true) }
        val px = text.trim().toFloatOrNull()
        when {
            constant != null -> current.copy(constant = constant, px = null, dp = null)
            px != null -> current.copy(constant = null, px = px, dp = px)
            else -> invalidValue(attributeId, text, "one of ${current.constants.joinToString(", ")}, or a length in pixels")
        }
    }
}

/** `#AARRGGBB` or `#RRGGBB`, the notation a `View`'s colors are written in; the short form is opaque. */
internal fun parseArgb(text: String): Int? {
    val digits = text.trim().removePrefix("#")
    if (digits.any { it !in HEX_DIGITS }) return null
    return when (digits.length) {
        8 -> digits.toLongOrNull(radix = 16)?.toInt()
        6 -> digits.toLongOrNull(radix = 16)?.toInt()?.or(OPAQUE_ALPHA)
        else -> null
    }
}

internal fun formatArgb(argb: Int): String = "#" + (argb.toLong() and 0xFFFFFFFFL).toString(radix = 16).padStart(8, '0').uppercase()

private const val OPAQUE_ALPHA = 0xFF shl 24

private const val HEX_DIGITS = "0123456789abcdefABCDEF"

@OptIn(ExperimentalJetWhaleApi::class)
private fun invalidValue(attributeId: String, text: String, expected: String): Nothing = throw JetWhaleMcpArgumentException("invalid value for $attributeId: \"$text\" (expected $expected)")
