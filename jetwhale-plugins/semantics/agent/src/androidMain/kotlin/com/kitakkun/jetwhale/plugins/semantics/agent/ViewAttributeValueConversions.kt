package com.kitakkun.jetwhale.plugins.semantics.agent

import android.view.View
import android.view.ViewGroup
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeValue
import kotlin.math.roundToInt

internal val VISIBILITY_OPTIONS: List<String> = listOf("VISIBLE", "INVISIBLE", "GONE")

internal fun ViewAttributeValue.asBoolean(attributeId: String): Boolean = (this as? ViewAttributeValue.BooleanValue)?.value
    ?: throw IllegalArgumentException(wrongVariantMessage(attributeId, "bool", this))

internal fun ViewAttributeValue.asInt(attributeId: String): Int = (this as? ViewAttributeValue.IntValue)?.value
    ?: throw IllegalArgumentException(wrongVariantMessage(attributeId, "int", this))

internal fun ViewAttributeValue.asFloat(attributeId: String): Float = (this as? ViewAttributeValue.FloatValue)?.value
    ?: throw IllegalArgumentException(wrongVariantMessage(attributeId, "float", this))

internal fun ViewAttributeValue.asText(attributeId: String): String = (this as? ViewAttributeValue.TextValue)?.value
    ?: throw IllegalArgumentException(wrongVariantMessage(attributeId, "text", this))

internal fun ViewAttributeValue.asColor(attributeId: String): Int = (this as? ViewAttributeValue.ColorValue)?.argb
    ?: throw IllegalArgumentException(wrongVariantMessage(attributeId, "color", this))

internal fun ViewAttributeValue.asDimensionPx(attributeId: String): Float = (this as? ViewAttributeValue.DimensionValue)?.px
    ?: throw IllegalArgumentException(wrongVariantMessage(attributeId, "dimension", this))

internal fun ViewAttributeValue.asVisibility(attributeId: String): Int {
    val name = (this as? ViewAttributeValue.EnumValue)?.value
        ?: throw IllegalArgumentException(wrongVariantMessage(attributeId, "enum", this))
    return when (name) {
        "VISIBLE" -> View.VISIBLE
        "INVISIBLE" -> View.INVISIBLE
        "GONE" -> View.GONE
        else -> throw IllegalArgumentException("unknown $attributeId: $name (expected one of ${VISIBILITY_OPTIONS.joinToString(", ")})")
    }
}

/** Either of the two constants or a pixel length, exactly as the attribute reads back. */
internal fun ViewAttributeValue.asLayoutSize(attributeId: String): Int {
    val size = this as? ViewAttributeValue.LayoutSizeValue
        ?: throw IllegalArgumentException(wrongVariantMessage(attributeId, "layoutSize", this))
    return when (size.constant) {
        null -> (size.px ?: throw IllegalArgumentException("$attributeId needs either a constant or a pixel length, but neither was sent")).roundToInt()
        "MATCH_PARENT" -> ViewGroup.LayoutParams.MATCH_PARENT
        "WRAP_CONTENT" -> ViewGroup.LayoutParams.WRAP_CONTENT
        else -> throw IllegalArgumentException("unknown $attributeId: ${size.constant} (expected one of ${LAYOUT_SIZE_CONSTANTS.joinToString(", ")}, or a pixel length)")
    }
}
