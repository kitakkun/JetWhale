package com.kitakkun.jetwhale.plugins.semantics.agent

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

internal infix fun UIAccessibilityTraits.has(trait: UIAccessibilityTraits): Boolean = this and trait != 0uL

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
