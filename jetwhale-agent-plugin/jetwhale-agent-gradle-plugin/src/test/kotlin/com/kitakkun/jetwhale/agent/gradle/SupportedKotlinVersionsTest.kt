package com.kitakkun.jetwhale.agent.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SupportedKotlinVersionsTest {
    @Test
    fun `versions inside the tested range are supported`() {
        listOf("2.3.0", "2.3.21", "2.4.0", "2.4.10").forEach {
            assertEquals(KotlinSupport.Supported, kotlinSupportFor(it), it)
        }
    }

    @Test
    fun `anything below the floor is rejected outright`() {
        // 2.2 cannot work: CompilerPluginRegistrar.pluginId does not exist there, so the registrar
        // fails to load rather than misbehaving quietly.
        assertIs<KotlinSupport.TooOld>(kotlinSupportFor("2.2.20"))
        assertIs<KotlinSupport.TooOld>(kotlinSupportFor("1.9.24"))
    }

    @Test
    fun `anything above the tested ceiling is untested rather than rejected`() {
        // Absence of evidence, not evidence of breakage — the plugin may well work on 2.5.
        assertIs<KotlinSupport.Untested>(kotlinSupportFor("2.5.0"))
        assertIs<KotlinSupport.Untested>(kotlinSupportFor("3.0.0"))
    }

    @Test
    fun `dev and IDE-tagged versions resolve to their base minor`() {
        // Versions in the wild are not all x.y.z: dev builds, IntelliJ tags, and the placeholder
        // majors Android Studio reports all appear, and all of them carry a usable major.minor.
        assertEquals(KotlinMinor(2, 4), parseKotlinMinor("2.4.20-dev-1762"))
        assertEquals(KotlinMinor(2, 3), parseKotlinMinor("2.3.20-ij253-105"))
        assertEquals(KotlinMinor(2, 3), parseKotlinMinor("2.3.255-dev-255"))
        assertEquals(KotlinSupport.Supported, kotlinSupportFor("2.4.20-dev-1762"))
    }

    @Test
    fun `an unrecognised version says nothing rather than guessing`() {
        // KotlinCompilerVersion reports "@snapshot@" for compiler dev builds. Guessing a minor from
        // that would produce a confident, wrong diagnostic; staying quiet is the honest answer.
        assertEquals(KotlinSupport.Unknown, kotlinSupportFor("@snapshot@"))
        assertEquals(KotlinSupport.Unknown, kotlinSupportFor("2"))
        assertNull(parseKotlinMinor("not-a-version"))
    }

    @Test
    fun `only the cases worth reporting carry a message`() {
        assertNull(KotlinSupport.Supported.message())
        assertNull(KotlinSupport.Unknown.message())
        assertNotNull(KotlinSupport.TooOld(KotlinMinor(2, 2)).message())
        assertNotNull(KotlinSupport.Untested(KotlinMinor(2, 5)).message())
    }

    @Test
    fun `minors order by major first`() {
        assertEquals(KotlinMinor(2, 4), maxOf(KotlinMinor(2, 4), KotlinMinor(2, 3)))
        assertEquals(KotlinMinor(3, 0), maxOf(KotlinMinor(3, 0), KotlinMinor(2, 30)))
    }
}
