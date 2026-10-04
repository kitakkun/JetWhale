package com.kitakkun.jetwhale.host.release

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostVersionTest {
    @Test
    fun `orders numbers then stage then stage number`() {
        val chain = listOf("1.0.0-alpha1", "1.0.0-alpha9", "1.0.0-alpha10", "1.0.0-beta1", "1.0.0-rc1", "1.0.0-rc199", "1.0.0", "1.0.1-alpha1", "1.1.0", "2.0.0-alpha1")
            .map { assertNotNull(HostVersion.parse(it), it) }

        chain.zipWithNext().forEach { (lower, higher) -> assertTrue(lower < higher, "$lower < $higher") }
        assertEquals(chain, chain.shuffled().sorted())
    }

    @Test
    fun `a zero-padded stage number is the same version`() {
        assertEquals(HostVersion.parse("1.0.0-alpha9"), HostVersion.parse("1.0.0-alpha09"))
        assertEquals(0, assertNotNull(HostVersion.parse("1.0.0-alpha09")).compareTo(assertNotNull(HostVersion.parse("1.0.0-alpha9"))))
    }

    @Test
    fun `a snapshot or unparseable tag is never a version`() {
        listOf(
            "1.0.0-alpha13-SNAPSHOT",
            "1.0.0-SNAPSHOT",
            "1.0",
            "v1.0.0",
            "1.0.0-alpha",
            "1.0.0-gamma1",
            "1.0.0-alpha0",
            "1.0.0-rc200",
            "1.0.0.12",
            "",
            "1.0.99999999999",
        ).forEach { assertNull(HostVersion.parse(it), it) }
    }
}
