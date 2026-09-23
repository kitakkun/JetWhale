package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.AppleNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.advertisedAs
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRect
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSStringFromClass
import platform.Foundation.valueForKey
import platform.UIKit.UIAccessibilityTraitAdjustable
import platform.UIKit.UIAccessibilityTraitAllowsDirectInteraction
import platform.UIKit.UIAccessibilityTraitButton
import platform.UIKit.UIAccessibilityTraitCausesPageTurn
import platform.UIKit.UIAccessibilityTraitHeader
import platform.UIKit.UIAccessibilityTraitImage
import platform.UIKit.UIAccessibilityTraitKeyboardKey
import platform.UIKit.UIAccessibilityTraitLink
import platform.UIKit.UIAccessibilityTraitNotEnabled
import platform.UIKit.UIAccessibilityTraitPlaysSound
import platform.UIKit.UIAccessibilityTraitSearchField
import platform.UIKit.UIAccessibilityTraitSelected
import platform.UIKit.UIAccessibilityTraitStartsMediaSession
import platform.UIKit.UIAccessibilityTraitStaticText
import platform.UIKit.UIAccessibilityTraitSummaryElement
import platform.UIKit.UIAccessibilityTraitSupportsZoom
import platform.UIKit.UIAccessibilityTraitTabBar
import platform.UIKit.UIAccessibilityTraitToggleButton
import platform.UIKit.UIAccessibilityTraitUpdatesFrequently
import platform.UIKit.UIAccessibilityTraits
import platform.UIKit.UIControl
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIFocusItemScrollableContainerProtocol
import platform.UIKit.UIScrollView
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
import platform.objc.objc_getProtocol

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
    val view = this as? UIView
    val hidden = hiddenByAncestor || (view != null && (view.hidden || view.alpha <= 0.0))
    val hidesElements = hidden || accessibilityElementsHidden

    val maxDepth = options.maxDepth
    val children = if (maxDepth != null && depth >= maxDepth) {
        emptyList()
    } else {
        val candidates = accessibilityChildren()

        // A modal child — a presented sheet, an alert — is the only one VoiceOver traverses; its
        // siblings are behind it and must not read as reachable.
        val modal = candidates.filter { it.accessibilityViewIsModal }
        candidates.mapNotNull { child ->
            val behindModal = modal.isNotEmpty() && modal.none { it == child }
            child.toAppleNode(options, window, depth + 1, hiddenByAncestor = hidesElements || behindModal)
        }
    }

    val frame = accessibilityFrame.toNodeBounds()
    val windowFrame = window.frame.toNodeBounds()
    val visibleBounds = frame.intersect(windowFrame)
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
        bounds = frame.translated(-windowFrame.left, -windowFrame.top),
        boundsInScreen = visibleBounds,
        actions = NodeAction.entries.mapNotNull { action -> action.advertisedAs?.takeIf { action.appleNodeHandler.isOfferedBy(this) } },
        isEnabled = isEnabled(),
        isClickable = isClickable(),
        isFocused = view?.isFirstResponder() ?: false,
        isSelected = traits has UIAccessibilityTraitSelected || (this as? UIControl)?.selected ?: false,
        isEditable = editable,
        isScrollable = isScrollable(),
        isVisible = visible,
        children = children,
    )
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

internal fun NSObject.isEnabled(): Boolean = !(accessibilityTraits has UIAccessibilityTraitNotEnabled) && (this as? UIControl)?.enabled != false

/**
 * A button, link or toggle by trait, or a `UIControl` with a target for its tap: a control that
 * declares none of those traits (a custom one, a `UITextField`) only has a click to offer when
 * something is registered for `touchUpInside`, which is where the click fallback sends it.
 */
internal fun NSObject.isClickable(): Boolean {
    val traits = accessibilityTraits
    return traits has UIAccessibilityTraitButton || traits has UIAccessibilityTraitLink || traits has UIAccessibilityTraitToggleButton ||
        (this is UIControl && listensForTouchUp())
}

internal fun UIControl.listensForTouchUp(): Boolean = allTargets.any { target ->
    !actionsForTarget(target, forControlEvent = UIControlEventTouchUpInside).isNullOrEmpty()
}

/**
 * The text-field views, plus the trait the system puts on a text field, which SwiftUI and Compose
 * both set on theirs. That trait has no public constant; it is the one bit both toolkits' fields
 * carry and no other element does.
 */
private fun NSObject.isTextInput(traits: UIAccessibilityTraits = accessibilityTraits): Boolean = this is UITextField || (this is UITextView && editable) || traits has TEXT_ENTRY_TRAIT

private val TEXT_ENTRY_TRAIT: UIAccessibilityTraits = 1uL shl 18

/**
 * Something with somewhere to scroll to: a `UIScrollView` whose content plus insets exceed its
 * viewport, or a focus-scrolling container (see [scrollableContainer]) whose content exceeds its
 * visible size — the same ranges `ScrollBy` moves within.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun NSObject.isScrollable(): Boolean {
    if (this is UIScrollView) {
        val (contentWidth, contentHeight) = contentSize.useContents { width to height }
        val (width, height) = bounds.useContents { size.width to size.height }
        val (insetTop, insetLeft, insetBottom, insetRight) = adjustedContentInset.useContents { listOf(top, left, bottom, right) }
        return contentWidth + insetLeft + insetRight > width || contentHeight + insetTop + insetBottom > height
    }
    val container = scrollableContainer() ?: return false
    val (contentWidth, contentHeight) = container.contentSize.useContents { width to height }
    val (width, height) = container.visibleSize.useContents { width to height }
    return contentWidth > width || contentHeight > height
}

/**
 * The object as the `UIFocusItemScrollableContainer` it declares itself to be, or `null`.
 *
 * The protocol is UIKit's own description of a scrolling region for focus movement — offset,
 * content size, visible size, and a setter for the offset — and Compose Multiplatform's
 * accessibility element answers `conformsToProtocol` for it per instance, exactly when its node
 * scrolls. So this is the one side-effect-free way to tell a Compose scrollable from any other
 * element, and the way to move it by a distance rather than a page.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun NSObject.scrollableContainer(): UIFocusItemScrollableContainerProtocol? {
    val protocol = SCROLLABLE_CONTAINER_PROTOCOL ?: return null
    if (!conformsToProtocol(protocol)) return null
    return this as? UIFocusItemScrollableContainerProtocol
}

@OptIn(ExperimentalForeignApi::class)
private val SCROLLABLE_CONTAINER_PROTOCOL = objc_getProtocol("UIFocusItemScrollableContainer")

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

private infix fun UIAccessibilityTraits.has(trait: UIAccessibilityTraits): Boolean = this and trait != 0uL

/** The public trait names for the bits set, with any bit outside the public set kept as its number. */
internal fun UIAccessibilityTraits.names(): List<String> {
    if (this == 0uL) return emptyList()
    val named = NAMED_TRAITS.filter { (trait, _) -> this has trait }.map { (_, name) -> name }
    val namedBits = NAMED_TRAITS.fold(0uL) { acc, (trait, _) -> acc or trait }
    val unnamed = (0 until ULong.SIZE_BITS)
        .map { 1uL shl it }
        .filter { bit -> this has bit && bit and namedBits == 0uL }
        .map { bit -> "bit${bit.countTrailingZeroBits()}" }
    return named + unnamed
}

private val NAMED_TRAITS: List<Pair<UIAccessibilityTraits, String>> = listOf(
    UIAccessibilityTraitButton to "Button",
    UIAccessibilityTraitLink to "Link",
    UIAccessibilityTraitHeader to "Header",
    UIAccessibilityTraitSearchField to "SearchField",
    UIAccessibilityTraitImage to "Image",
    UIAccessibilityTraitSelected to "Selected",
    UIAccessibilityTraitPlaysSound to "PlaysSound",
    UIAccessibilityTraitKeyboardKey to "KeyboardKey",
    UIAccessibilityTraitStaticText to "StaticText",
    UIAccessibilityTraitSummaryElement to "SummaryElement",
    UIAccessibilityTraitNotEnabled to "NotEnabled",
    UIAccessibilityTraitUpdatesFrequently to "UpdatesFrequently",
    UIAccessibilityTraitStartsMediaSession to "StartsMediaSession",
    UIAccessibilityTraitAdjustable to "Adjustable",
    UIAccessibilityTraitAllowsDirectInteraction to "AllowsDirectInteraction",
    UIAccessibilityTraitCausesPageTurn to "CausesPageTurn",
    UIAccessibilityTraitTabBar to "TabBar",
    UIAccessibilityTraitToggleButton to "ToggleButton",
    UIAccessibilityTraitSupportsZoom to "SupportsZoom",
)

@OptIn(ExperimentalForeignApi::class)
internal fun CValue<CGRect>.toNodeBounds(): NodeBounds = useContents {
    NodeBounds(
        left = origin.x.toFloat(),
        top = origin.y.toFloat(),
        right = (origin.x + size.width).toFloat(),
        bottom = (origin.y + size.height).toFloat(),
    )
}

internal fun NodeBounds.intersect(other: NodeBounds): NodeBounds {
    val left = maxOf(left, other.left)
    val top = maxOf(top, other.top)
    val right = minOf(right, other.right)
    val bottom = minOf(bottom, other.bottom)
    return if (right <= left || bottom <= top) {
        NodeBounds(left = 0f, top = 0f, right = 0f, bottom = 0f)
    } else {
        NodeBounds(left = left, top = top, right = right, bottom = bottom)
    }
}

private fun NodeBounds.translated(offsetX: Float, offsetY: Float): NodeBounds = NodeBounds(
    left = left + offsetX,
    top = top + offsetY,
    right = right + offsetX,
    bottom = bottom + offsetY,
)
