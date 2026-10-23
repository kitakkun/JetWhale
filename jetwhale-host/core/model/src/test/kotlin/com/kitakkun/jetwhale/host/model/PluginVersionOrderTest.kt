package com.kitakkun.jetwhale.host.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PluginVersionOrderTest {
    @Test
    fun `versions compare numerically component by component`() {
        assertEquals(1, PluginVersionOrder.compare("1.10.0", "1.9.2"))
        assertEquals(-1, PluginVersionOrder.compare("1.2.0", "1.3.0-alpha01"))
    }

    @Test
    fun `a release comes after its pre-releases`() {
        assertEquals(listOf("1.0.0-alpha02", "1.0.0"), listOf("1.0.0", "1.0.0-alpha02").sortedWith(PluginVersionOrder))
    }

    @Test
    fun `pre-releases compare their numbers as numbers and their words alphabetically`() {
        val ordered = listOf("1.0.0-alpha1", "1.0.0-alpha02", "1.0.0-alpha10", "1.0.0-beta1", "1.0.0-rc.2", "1.0.0-rc.10", "1.0.0-SNAPSHOT")

        assertEquals(ordered, ordered.reversed().sortedWith(PluginVersionOrder))
    }
}
