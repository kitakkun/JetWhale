package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.AppleNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.advertisedAs
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSStringFromClass
import platform.Foundation.valueForKey
import platform.UIKit.UIAccessibilityTraitSelected
import platform.UIKit.UIAccessibilityTraitToggleButton
import platform.UIKit.UIAccessibilityTraits
import platform.UIKit.UIControl
import platform.UIKit.UISwitch
import platform.UIKit.UITextField
import platform.UIKit.UITextView
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.accessibilityElements
import platform.UIKit.accessibilityElementsHidden
import platform.UIKit.accessibilityFrame
import platform.UIKit.accessibilityHint
import platform.UIKit.accessibilityLabel
import platform.UIKit.accessibilityTraits
import platform.UIKit.accessibilityValue
import platform.UIKit.accessibilityViewIsModal
import platform.UIKit.isAccessibilityElement
import platform.darwin.NSObject

/**
 * Converts one iOS window into the transport model by walking the `NSObject` accessibility protocol.
 *
 * One walk covers three toolkits. UIKit views are their own accessibility objects; SwiftUI publishes
 * its nodes through its hosting view's `accessibilityElements`; Compose Multiplatform publishes its
 * semantics tree the same way from the view it draws into. So the walk never asks which toolkit an
 * object belongs to — the class name in the node says, for a reader who cares.
 *
 * Children come from one source per object, never both: `accessibilityElements` when the object
 * sets it (the toolkit's own statement of its semantic tree, which lists the interop views it embeds
 * as well as its nodes), otherwise a view's `subviews`. A hosting view's element list repeats views
 * that `subviews` also reaches, and taking both would report every such subtree twice.
 *
 * Returns `null` when the object is filtered out: it is invisible and
 * [NodeTreeCaptureOptions.includeInvisible] is off, or it sits past
 * [NodeTreeCaptureOptions.maxDepth]. An object kept only because a descendant survived stays in
 * the tree, exactly as on the Compose side, so the descendant does not get reparented.
 *
 * Must be called on the main thread.
 *
 * @param window the window this object is in, whose frame is the clip every node is tested
 *   against and whose origin is subtracted for root-relative bounds.
 * @param hiddenByAncestor `true` once a hidden view, an `accessibilityElementsHidden` container, or
 *   a sibling of an `accessibilityViewIsModal` view was passed on the way down: what it hides stays
 *   in the tree, marked invisible.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun NSObject.toAppleNode(
    options: NodeTreeCaptureOptions,
    window: UIWindow,
    depth: Int,
    hiddenByAncestor: Boolean = false,
): AppleNode? {
    val hidden = hiddenByAncestor || (this as? UIView)?.let { it.hidden || it.alpha <= 0.0 } == true
    val children = childAppleNodes(
        options = options,
        window = window,
        depth = depth,
        hiddenByAncestor = hidden || accessibilityElementsHidden,
    )

    val frame = accessibilityFrame.toNodeBounds()
    val windowFrame = window.frame.toNodeBounds()
    val visibleBounds = frame.intersect(windowFrame)
    val bounds = frame.translated(-windowFrame.left, -windowFrame.top)
    // The window is the only clip a bare element can be tested against: a SwiftUI or Compose node
    // scrolled out of its container still reports its laid-out frame, and which container clips it
    // is not part of the protocol. A view is no better off, so the two are treated alike.
    val visible = !hidden && !visibleBounds.isEmpty
    if (!visible && !options.includeInvisible && children.isEmpty()) return null

    val traits = accessibilityTraits
    val editable = isTextInput(traits)
    return AppleNode(
        id = AppleNodeIds.idOf(this, window),
        className = className(),
        accessibilityIdentifier = accessibilityIdentifierOrNull(),
        accessibilityValue = accessibilityValue,
        traits = traits.names(),
        text = accessibilityLabel,
        editableText = if (editable) editableText() else null,
        contentDescription = accessibilityHint,
        toggleableState = toggleableState(traits),
        bounds = bounds,
        boundsInScreen = visibleBounds,
        actions = NodeAction.entries.mapNotNull { action -> action.advertisedAs?.takeIf { action.appleNodeHandler.isOfferedBy(this) } },
        isEnabled = isEnabled(),
        isClickable = isClickable(),
        isFocused = (this as? UIView)?.isFirstResponder() ?: false,
        isSelected = traits has UIAccessibilityTraitSelected || (this as? UIControl)?.selected ?: false,
        isEditable = editable,
        isScrollable = isScrollable(),
        isVisible = visible,
        children = children,
    )
}

/**
 * The nodes for this object's accessibility children, or none once [NodeTreeCaptureOptions.maxDepth]
 * is reached.
 */
@OptIn(ExperimentalForeignApi::class)
private fun NSObject.childAppleNodes(
    options: NodeTreeCaptureOptions,
    window: UIWindow,
    depth: Int,
    hiddenByAncestor: Boolean,
): List<AppleNode> {
    val maxDepth = options.maxDepth
    if (maxDepth != null && depth >= maxDepth) return emptyList()

    val candidates = accessibilityChildren()
    // A modal child — a presented sheet, an alert — is the only one VoiceOver traverses; its
    // siblings are behind it and must not read as reachable.
    val modal = candidates.filter { it.accessibilityViewIsModal }
    return candidates.mapNotNull { child ->
        val behindModal = modal.isNotEmpty() && modal.none { it == child }
        child.toAppleNode(options, window, depth + 1, hiddenByAncestor = hiddenByAncestor || behindModal)
    }
}

/**
 * The objects under this one in the accessibility tree; see [toAppleNode] for why it is one list
 * or the other. A view that is itself an accessibility element (a `UIButton`, a `UISwitch`) is one
 * object to accessibility, and the label and image views inside it are its implementation, so the
 * walk stops there.
 */
internal fun NSObject.accessibilityChildren(): List<NSObject> {
    accessibilityElements?.let { elements -> return elements.map { it as NSObject } }
    val view = this as? UIView ?: return emptyList()
    if (view.isAccessibilityElement) return emptyList()
    return view.subviews.map { it as NSObject }
}

@OptIn(BetaInteropApi::class)
internal fun NSObject.className(): String = `class`()?.let(::NSStringFromClass) ?: "NSObject"

/**
 * `accessibilityIdentifier` belongs to `UIAccessibilityIdentification`, which `UIView` adopts and
 * a bare element may or may not. The Kotlin cast to that protocol answers `null` even for a view,
 * so the property is read by selector, which is what the protocol amounts to at runtime.
 */
@OptIn(ExperimentalForeignApi::class)
private fun NSObject.accessibilityIdentifierOrNull(): String? {
    if (!respondsToSelector(NSSelectorFromString("accessibilityIdentifier"))) return null
    return valueForKey("accessibilityIdentifier") as? String
}

/**
 * The text-field views, plus the trait the system puts on a text field, which SwiftUI and Compose
 * both set on theirs. That trait has no public constant; it is the one bit both toolkits' fields
 * carry and no other element does.
 */
private fun NSObject.isTextInput(traits: UIAccessibilityTraits = accessibilityTraits): Boolean =
    this is UITextField || (this is UITextView && editable) || traits has TEXT_ENTRY_TRAIT

private val TEXT_ENTRY_TRAIT: UIAccessibilityTraits = 1uL shl 18

/**
 * A field's `accessibilityValue` is its placeholder while it is empty, so the view's own `text` is
 * read where there is one. A bare element has only its value. A secure field (a password) never
 * gives up its text: the node stays editable, and its `accessibilityValue` carries what UIKit
 * shows VoiceOver, which is masked.
 */
private fun NSObject.editableText(): String? = when (this) {
    is UITextField -> if (secureTextEntry) null else text ?: ""
    is UITextView -> if (secureTextEntry) null else text
    else -> accessibilityValue
}

/**
 * A `UISwitch` knows its state; a toggle-button element reports it as a `"1"`/`"0"` value, or, when
 * it reports no value (Compose's `Switch`), through the selected trait.
 */
private fun NSObject.toggleableState(traits: UIAccessibilityTraits): String? {
    if (this is UISwitch) return if (on) "On" else "Off"
    if (!(traits has UIAccessibilityTraitToggleButton)) return null
    return when (accessibilityValue) {
        "1" -> "On"
        "0" -> "Off"
        else -> if (traits has UIAccessibilityTraitSelected) "On" else "Off"
    }
}

private fun NodeBounds.translated(offsetX: Float, offsetY: Float): NodeBounds = NodeBounds(
    left = left + offsetX,
    top = top + offsetY,
    right = right + offsetX,
    bottom = bottom + offsetY,
)
