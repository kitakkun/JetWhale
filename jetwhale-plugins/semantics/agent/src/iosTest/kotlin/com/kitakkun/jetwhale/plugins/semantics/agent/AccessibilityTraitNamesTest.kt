package com.kitakkun.jetwhale.plugins.semantics.agent

import platform.UIKit.UIAccessibilityTraitButton
import platform.UIKit.UIAccessibilityTraitNone
import platform.UIKit.UIAccessibilityTraitNotEnabled
import platform.UIKit.UIAccessibilityTraitSelected
import platform.UIKit.UIAccessibilityTraitToggleButton
import kotlin.test.Test
import kotlin.test.assertEquals

class AccessibilityTraitNamesTest {
    @Test
    fun `no traits reads as an empty list`() {
        assertEquals(emptyList(), UIAccessibilityTraitNone.names())
    }

    @Test
    fun `public traits are named`() {
        assertEquals(
            listOf("Button", "Selected", "NotEnabled", "ToggleButton"),
            (
                UIAccessibilityTraitButton or UIAccessibilityTraitSelected or UIAccessibilityTraitNotEnabled or
                    UIAccessibilityTraitToggleButton
                ).names(),
        )
    }

    @Test
    fun `a bit outside the public set keeps its number`() {
        assertEquals(listOf("Button", "bit18"), (UIAccessibilityTraitButton or (1uL shl 18)).names())
    }
}
