package com.kitakkun.jetwhale.plugins.semantics.agent

import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeValue
import kotlin.math.roundToInt

// The attributes of an Android `View` that this plugin reads, and the subset it writes.
//
// The list is an explicit allowlist rather than reflection over the view's getters. Reflection is
// what makes the equivalent in other layout inspectors fragile, and Android's non-SDK interface
// restrictions block most of what it would reach anyway; a typed list is smaller, says exactly what
// it will touch, and cannot surprise the app being debugged.
//
// Everything here must run on the main thread.

/**
 * One attribute: how to read it off a view, and how to write it back when it can be written.
 *
 * @param read `null` when the attribute does not apply to this view — a `text` on a non-`TextView`,
 *   a margin on a view whose parent hands out no margins — in which case it is simply absent.
 * @param write `null` for a read-only attribute. The caller invalidates afterwards.
 * @param relayouts whether a successful write needs a new layout pass rather than only a redraw.
 *   Defaults to `false` because most attributes only change how the view is painted; the ones that
 *   change how big it is, or where, say so.
 */
private class ViewAttributeDescriptor(
    val id: String,
    val label: String,
    val group: String,
    val read: (View) -> ViewAttributeValue?,
    val write: ((View, ViewAttributeValue) -> Unit)?,
    val relayouts: Boolean = false,
)

/** Reads every attribute this view exposes, in the order the descriptors are declared. */
internal fun View.readAttributes(rootId: String, nodeId: Int): ViewAttributeSnapshot = ViewAttributeSnapshot(
    rootId = rootId,
    nodeId = nodeId,
    viewClass = javaClass.name,
    attributes = VIEW_ATTRIBUTES.mapNotNull { it.toAttribute(this) },
)

/**
 * Writes one attribute and reads it back, so the caller reports the value that actually took effect
 * rather than the one it asked for — an app can clamp a value, or ignore it.
 */
internal fun View.writeAttribute(attributeId: String, value: ViewAttributeValue): ViewAttributeResult {
    val descriptor = VIEW_ATTRIBUTES.firstOrNull { it.id == attributeId }
        ?: return ViewAttributeResult(applied = false, message = "unknown attributeId: $attributeId")
    if (descriptor.read(this) == null) {
        return ViewAttributeResult(applied = false, message = "${descriptor.id} does not apply to a ${javaClass.name}")
    }
    val write = descriptor.write
        ?: return ViewAttributeResult(applied = false, message = "${descriptor.id} is read-only")

    return try {
        write(this, value)
        // An app may reject a layoutParams change from its own onLayout, so the value is read back
        // after the pass that would apply it is requested rather than before.
        if (descriptor.relayouts) requestLayout() else invalidate()
        ViewAttributeResult(applied = true, attribute = descriptor.toAttribute(this))
    } catch (e: Throwable) {
        ViewAttributeResult(applied = false, message = e.message?.takeIf { it.isNotBlank() } ?: (e::class.simpleName ?: "the write failed"))
    }
}

private fun ViewAttributeDescriptor.toAttribute(view: View): ViewAttribute? = read(view)?.let { value ->
    ViewAttribute(id = id, label = label, group = group, value = value, editable = write != null)
}

// -- The allowlist ------------------------------------------------------------

private const val GROUP_STATE = "State"
private const val GROUP_LAYOUT = "Layout"
private const val GROUP_APPEARANCE = "Appearance"
private const val GROUP_TEXT = "Text"
private const val GROUP_INFO = "Info"

private val VISIBILITY_OPTIONS = listOf("VISIBLE", "INVISIBLE", "GONE")

private val VIEW_ATTRIBUTES: List<ViewAttributeDescriptor> = buildList {
    // State ------------------------------------------------------------------
    add(
        ViewAttributeDescriptor(
            id = "visibility",
            label = "visibility",
            group = GROUP_STATE,
            read = { view -> ViewAttributeValue.EnumValue(view.visibilityName(), VISIBILITY_OPTIONS) },
            write = { view, value -> view.visibility = value.asVisibility("visibility") },
            relayouts = true,
        ),
    )
    addFlag(id = "enabled", group = GROUP_STATE, read = { it.isEnabled }, write = { view, on -> view.isEnabled = on })
    addFlag(id = "selected", group = GROUP_STATE, read = { it.isSelected }, write = { view, on -> view.isSelected = on })
    addFlag(id = "activated", group = GROUP_STATE, read = { it.isActivated }, write = { view, on -> view.isActivated = on })
    addFlag(id = "clickable", group = GROUP_STATE, read = { it.isClickable }, write = { view, on -> view.isClickable = on })
    addFlag(id = "focusable", group = GROUP_STATE, read = { it.isFocusable }, write = { view, on -> view.isFocusable = on })
    // Read-only: taking focus is an action with side effects of its own — a keyboard, a scroll —
    // so it belongs to performNodeAction's RequestFocus rather than to a property editor.
    addFlag(id = "focused", group = GROUP_STATE, read = { it.isFocused }, write = null)

    // Layout -----------------------------------------------------------------
    addLayoutSize(id = "layout.width", read = { it.width }, write = { params, size -> params.width = size })
    addLayoutSize(id = "layout.height", read = { it.height }, write = { params, size -> params.height = size })
    addDimension(
        id = "padding.left",
        group = GROUP_LAYOUT,
        read = { it.paddingLeft.toFloat() },
        write = { view, px -> view.setPadding(px.roundToInt(), view.paddingTop, view.paddingRight, view.paddingBottom) },
        relayouts = true,
    )
    addDimension(
        id = "padding.top",
        group = GROUP_LAYOUT,
        read = { it.paddingTop.toFloat() },
        write = { view, px -> view.setPadding(view.paddingLeft, px.roundToInt(), view.paddingRight, view.paddingBottom) },
        relayouts = true,
    )
    addDimension(
        id = "padding.right",
        group = GROUP_LAYOUT,
        read = { it.paddingRight.toFloat() },
        write = { view, px -> view.setPadding(view.paddingLeft, view.paddingTop, px.roundToInt(), view.paddingBottom) },
        relayouts = true,
    )
    addDimension(
        id = "padding.bottom",
        group = GROUP_LAYOUT,
        read = { it.paddingBottom.toFloat() },
        write = { view, px -> view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, px.roundToInt()) },
        relayouts = true,
    )
    addMargin(id = "margin.left", read = { it.leftMargin }, write = { params, px -> params.leftMargin = px })
    addMargin(id = "margin.top", read = { it.topMargin }, write = { params, px -> params.topMargin = px })
    addMargin(id = "margin.right", read = { it.rightMargin }, write = { params, px -> params.rightMargin = px })
    addMargin(id = "margin.bottom", read = { it.bottomMargin }, write = { params, px -> params.bottomMargin = px })
    addDimension(
        id = "minWidth",
        group = GROUP_LAYOUT,
        read = { it.minimumWidth.toFloat() },
        write = { view, px -> view.minimumWidth = px.roundToInt() },
        relayouts = true,
    )
    addDimension(
        id = "minHeight",
        group = GROUP_LAYOUT,
        read = { it.minimumHeight.toFloat() },
        write = { view, px -> view.minimumHeight = px.roundToInt() },
        relayouts = true,
    )
    add(
        ViewAttributeDescriptor(
            id = "bounds",
            label = "bounds (window)",
            group = GROUP_LAYOUT,
            read = { view ->
                val location = IntArray(2).also(view::getLocationInWindow)
                ViewAttributeValue.TextValue("${location[0]},${location[1]},${location[0] + view.width},${location[1] + view.height}")
            },
            write = null,
        ),
    )

    // Appearance -------------------------------------------------------------
    addFloat(
        id = "alpha",
        group = GROUP_APPEARANCE,
        read = { it.alpha },
        // Anything outside 0..1 is silently treated as opaque by the platform, which would read back
        // as "applied" while nothing changed; clamping makes the reported value the honest one.
        write = { view, value -> view.alpha = value.coerceIn(0f, 1f) },
    )
    add(
        ViewAttributeDescriptor(
            id = "backgroundColor",
            label = "backgroundColor",
            group = GROUP_APPEARANCE,
            // Only a flat color background has a color to report; anything else is described by the
            // read-only `background` attribute below.
            read = { view -> (view.background as? ColorDrawable)?.let { ViewAttributeValue.ColorValue(it.color) } },
            write = { view, value -> view.setBackgroundColor(value.asColor("backgroundColor")) },
        ),
    )
    add(
        ViewAttributeDescriptor(
            id = "background",
            label = "background",
            group = GROUP_APPEARANCE,
            read = { view ->
                view.background
                    ?.takeIf { it !is ColorDrawable }
                    ?.let { ViewAttributeValue.TextValue(it.javaClass.name) }
            },
            write = null,
        ),
    )
    addDimension(id = "elevation", group = GROUP_APPEARANCE, read = { it.elevation }, write = { view, px -> view.elevation = px }, relayouts = false)
    addDimension(id = "translationX", group = GROUP_APPEARANCE, read = { it.translationX }, write = { view, px -> view.translationX = px }, relayouts = false)
    addDimension(id = "translationY", group = GROUP_APPEARANCE, read = { it.translationY }, write = { view, px -> view.translationY = px }, relayouts = false)
    addFloat(id = "rotation", group = GROUP_APPEARANCE, read = { it.rotation }, write = { view, value -> view.rotation = value })
    addFloat(id = "scaleX", group = GROUP_APPEARANCE, read = { it.scaleX }, write = { view, value -> view.scaleX = value })
    addFloat(id = "scaleY", group = GROUP_APPEARANCE, read = { it.scaleY }, write = { view, value -> view.scaleY = value })

    // Text -------------------------------------------------------------------
    add(
        ViewAttributeDescriptor(
            id = "text",
            label = "text",
            group = GROUP_TEXT,
            read = { view -> (view as? TextView)?.let { ViewAttributeValue.TextValue(it.text?.toString().orEmpty()) } },
            write = { view, value -> (view as TextView).text = value.asText("text") },
            relayouts = true,
        ),
    )
    add(
        ViewAttributeDescriptor(
            id = "hint",
            label = "hint",
            group = GROUP_TEXT,
            read = { view -> (view as? TextView)?.let { ViewAttributeValue.TextValue(it.hint?.toString().orEmpty()) } },
            write = { view, value -> (view as TextView).hint = value.asText("hint") },
            relayouts = true,
        ),
    )
    add(
        ViewAttributeDescriptor(
            id = "textSize",
            label = "textSize",
            group = GROUP_TEXT,
            // The px figure is what the platform stores; the second figure is the sp the app would
            // have written, which is the number a reader recognises.
            read = { view -> (view as? TextView)?.let { ViewAttributeValue.DimensionValue(px = it.textSize, dp = it.textSize / view.scaledTextDensity()) } },
            write = { view, value -> (view as TextView).setTextSize(TypedValue.COMPLEX_UNIT_PX, value.asDimensionPx("textSize")) },
            relayouts = true,
        ),
    )
    add(
        ViewAttributeDescriptor(
            id = "textColor",
            label = "textColor",
            group = GROUP_TEXT,
            read = { view -> (view as? TextView)?.let { ViewAttributeValue.ColorValue(it.currentTextColor) } },
            write = { view, value -> (view as TextView).setTextColor(value.asColor("textColor")) },
        ),
    )
    add(
        ViewAttributeDescriptor(
            id = "maxLines",
            label = "maxLines",
            group = GROUP_TEXT,
            read = { view -> (view as? TextView)?.let { ViewAttributeValue.IntValue(it.maxLines) } },
            write = { view, value -> (view as TextView).maxLines = value.asInt("maxLines") },
            relayouts = true,
        ),
    )

    // Info -------------------------------------------------------------------
    add(
        ViewAttributeDescriptor(
            id = "id",
            label = "android:id",
            group = GROUP_INFO,
            read = { view -> view.resourceEntryNameOrNull()?.let { ViewAttributeValue.TextValue("@id/$it") } },
            write = null,
        ),
    )
    add(
        ViewAttributeDescriptor(
            id = "class",
            label = "class",
            group = GROUP_INFO,
            read = { view -> ViewAttributeValue.TextValue(view.javaClass.name) },
            write = null,
        ),
    )
}

// -- Descriptor builders ------------------------------------------------------

private fun MutableList<ViewAttributeDescriptor>.addFlag(
    id: String,
    group: String,
    read: (View) -> Boolean,
    write: ((View, Boolean) -> Unit)?,
) {
    val setValue: ((View, ViewAttributeValue) -> Unit)? = if (write == null) {
        null
    } else {
        { view, value -> write(view, value.asBoolean(id)) }
    }
    add(
        ViewAttributeDescriptor(
            id = id,
            label = id,
            group = group,
            read = { view -> ViewAttributeValue.BooleanValue(read(view)) },
            write = setValue,
        ),
    )
}

private fun MutableList<ViewAttributeDescriptor>.addFloat(
    id: String,
    group: String,
    read: (View) -> Float,
    write: (View, Float) -> Unit,
) {
    add(
        ViewAttributeDescriptor(
            id = id,
            label = id,
            group = group,
            read = { view -> ViewAttributeValue.FloatValue(read(view)) },
            write = { view, value -> write(view, value.asFloat(id)) },
        ),
    )
}

private fun MutableList<ViewAttributeDescriptor>.addDimension(
    id: String,
    group: String,
    read: (View) -> Float,
    write: (View, Float) -> Unit,
    relayouts: Boolean,
) {
    add(
        ViewAttributeDescriptor(
            id = id,
            label = id,
            group = group,
            read = { view -> view.dimension(read(view)) },
            write = { view, value -> write(view, value.asDimensionPx(id)) },
            relayouts = relayouts,
        ),
    )
}

/** A margin exists only when the view's parent hands out [ViewGroup.MarginLayoutParams]; otherwise it is absent. */
private fun MutableList<ViewAttributeDescriptor>.addMargin(
    id: String,
    read: (ViewGroup.MarginLayoutParams) -> Int,
    write: (ViewGroup.MarginLayoutParams, Int) -> Unit,
) {
    add(
        ViewAttributeDescriptor(
            id = id,
            label = id,
            group = GROUP_LAYOUT,
            read = { view -> view.marginParams()?.let { view.dimension(read(it).toFloat()) } },
            write = { view, value ->
                val params = view.marginParams() ?: throw IllegalStateException("$id needs a parent that hands out margins")
                write(params, value.asDimensionPx(id).roundToInt())
                // MarginLayoutParams is the live object, but the parent only re-reads it when the
                // params are set back on the view.
                view.layoutParams = params
            },
            relayouts = true,
        ),
    )
}

/**
 * `layout.width` / `layout.height`: either of the two constants that name a rule, or a pixel length.
 *
 * Read and written through `layoutParams`, so a view that has none yet — one not attached to a
 * parent — reports neither.
 */
private fun MutableList<ViewAttributeDescriptor>.addLayoutSize(
    id: String,
    read: (ViewGroup.LayoutParams) -> Int,
    write: (ViewGroup.LayoutParams, Int) -> Unit,
) {
    add(
        ViewAttributeDescriptor(
            id = id,
            label = id,
            group = GROUP_LAYOUT,
            read = { view ->
                view.layoutParams?.let { params ->
                    val raw = read(params)
                    layoutSizeValue(constantName = layoutSizeConstantName(raw), px = raw.toFloat(), density = view.density())
                }
            },
            write = { view, value ->
                val params = view.layoutParams ?: throw IllegalStateException("$id needs a view that has layout params")
                write(params, value.asLayoutSize(id))
                view.layoutParams = params
            },
            relayouts = true,
        ),
    )
}

// -- Value conversion ---------------------------------------------------------

private fun View.density(): Float = resources.displayMetrics.density

// A TextView's size is written in sp, which scales with the user's font-size setting on top of the
// display density — so the sp figure needs that scale, not the plain one.
@Suppress("DEPRECATION")
private fun View.scaledTextDensity(): Float = resources.displayMetrics.scaledDensity

private fun View.dimension(px: Float): ViewAttributeValue.DimensionValue = ViewAttributeValue.DimensionValue(px = px, dp = px / density())

private fun View.marginParams(): ViewGroup.MarginLayoutParams? = layoutParams as? ViewGroup.MarginLayoutParams

private fun View.visibilityName(): String = when (visibility) {
    View.VISIBLE -> "VISIBLE"
    View.INVISIBLE -> "INVISIBLE"
    else -> "GONE"
}

private fun layoutSizeConstantName(raw: Int): String? = when (raw) {
    ViewGroup.LayoutParams.MATCH_PARENT -> "MATCH_PARENT"
    ViewGroup.LayoutParams.WRAP_CONTENT -> "WRAP_CONTENT"
    else -> null
}

/**
 * The entry name of the view's `android:id`, or `null` when it has none. A generated id has no entry
 * to look up, so the lookup is allowed to fail rather than being guarded by a check on the packing.
 */
private fun View.resourceEntryNameOrNull(): String? {
    if (id == View.NO_ID) return null
    return try {
        resources?.getResourceEntryName(id)
    } catch (_: Resources.NotFoundException) {
        null
    }
}

private fun ViewAttributeValue.asBoolean(attributeId: String): Boolean = (this as? ViewAttributeValue.BooleanValue)?.value
    ?: throw IllegalArgumentException(wrongVariantMessage(attributeId, "bool", this))

private fun ViewAttributeValue.asInt(attributeId: String): Int = (this as? ViewAttributeValue.IntValue)?.value
    ?: throw IllegalArgumentException(wrongVariantMessage(attributeId, "int", this))

private fun ViewAttributeValue.asFloat(attributeId: String): Float = (this as? ViewAttributeValue.FloatValue)?.value
    ?: throw IllegalArgumentException(wrongVariantMessage(attributeId, "float", this))

private fun ViewAttributeValue.asText(attributeId: String): String = (this as? ViewAttributeValue.TextValue)?.value
    ?: throw IllegalArgumentException(wrongVariantMessage(attributeId, "text", this))

private fun ViewAttributeValue.asColor(attributeId: String): Int = (this as? ViewAttributeValue.ColorValue)?.argb
    ?: throw IllegalArgumentException(wrongVariantMessage(attributeId, "color", this))

private fun ViewAttributeValue.asDimensionPx(attributeId: String): Float = (this as? ViewAttributeValue.DimensionValue)?.px
    ?: throw IllegalArgumentException(wrongVariantMessage(attributeId, "dimension", this))

private fun ViewAttributeValue.asVisibility(attributeId: String): Int {
    val name = (this as? ViewAttributeValue.EnumValue)?.value
        ?: throw IllegalArgumentException(wrongVariantMessage(attributeId, "enum", this))
    return when (name) {
        "VISIBLE" -> View.VISIBLE
        "INVISIBLE" -> View.INVISIBLE
        "GONE" -> View.GONE
        else -> throw IllegalArgumentException("unknown $attributeId: $name (expected one of ${VISIBILITY_OPTIONS.joinToString(", ")})")
    }
}

/** Accepts either of the two constants or a pixel length, which is how the attribute reads back too. */
private fun ViewAttributeValue.asLayoutSize(attributeId: String): Int = when (this) {
    is ViewAttributeValue.EnumValue -> when (value) {
        "MATCH_PARENT" -> ViewGroup.LayoutParams.MATCH_PARENT
        "WRAP_CONTENT" -> ViewGroup.LayoutParams.WRAP_CONTENT
        else -> throw IllegalArgumentException("unknown $attributeId: $value (expected one of ${LAYOUT_SIZE_CONSTANTS.joinToString(", ")}, or a dimension)")
    }

    is ViewAttributeValue.DimensionValue -> px.roundToInt()

    else -> throw IllegalArgumentException(wrongVariantMessage(attributeId, "enum or dimension", this))
}
