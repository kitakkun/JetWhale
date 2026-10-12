package com.kitakkun.jetwhale.plugins.semantics.agent

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIAccessibilityTraitButton
import platform.UIKit.UIAccessibilityTraitLink
import platform.UIKit.UIAccessibilityTraitNotEnabled
import platform.UIKit.UIAccessibilityTraitToggleButton
import platform.UIKit.UIControl
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIFocusItemScrollableContainerProtocol
import platform.UIKit.UIScrollView
import platform.UIKit.accessibilityTraits
import platform.darwin.NSObject
import platform.objc.objc_getProtocol

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
 * Something with somewhere to scroll to: a `UIScrollView` whose content plus insets exceed its
 * viewport, or a focus-scrolling container (see [scrollableContainer]) whose content exceeds its
 * visible size — the same ranges `ScrollBy` moves within.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun NSObject.isScrollable(): Boolean {
    if (this is UIScrollView) {
        val (contentWidth, contentHeight) = contentSize.useContents { width to height }
        val (paddedWidth, paddedHeight) = adjustedContentInset.useContents {
            contentWidth + left + right to contentHeight + top + bottom
        }
        val (width, height) = bounds.useContents { size.width to size.height }
        return paddedWidth > width || paddedHeight > height
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
