package com.kitakkun.jetwhale.plugins.permissions.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val FINE = "android.permission.ACCESS_FINE_LOCATION"
private const val COARSE = "android.permission.ACCESS_COARSE_LOCATION"
private const val BACKGROUND = "android.permission.ACCESS_BACKGROUND_LOCATION"
private const val CAMERA = "android.permission.CAMERA"

private const val ANDROID_10 = 29
private const val ANDROID_11 = 30
private const val ANDROID_14 = 34
private const val ANDROID_15 = 35

class RuntimeRequestTest {
    @Test
    fun `fine location is asked for with coarse location when the app declares both`() {
        assertEquals(
            RuntimeRequest.Ask(listOf(FINE, COARSE), caveat = null),
            runtimeRequestFor(FINE, RuntimeDenial.Unknown, declared = setOf(FINE, COARSE)),
        )
    }

    @Test
    fun `fine location is asked for alone with a caveat when the app does not declare coarse location`() {
        val request = assertIs<RuntimeRequest.Ask>(runtimeRequestFor(FINE, RuntimeDenial.Once, declared = setOf(FINE)))

        assertEquals(listOf(FINE), request.permissions)
        assertNotNull(request.caveat)
    }

    @Test
    fun `any other permission is asked for alone`() {
        assertEquals(
            RuntimeRequest.Ask(listOf(CAMERA), caveat = null),
            runtimeRequestFor(CAMERA, RuntimeDenial.Once, declared = setOf(CAMERA, FINE, COARSE)),
        )
    }

    @Test
    fun `a permanently denied permission is refused`() {
        assertIs<RuntimeRequest.Refused>(runtimeRequestFor(CAMERA, RuntimeDenial.Permanently, declared = setOf(CAMERA)))
    }

    @Test
    fun `background location is neither requestable nor asked for on any Android version while no foreground location is granted`() {
        assertEquals(BackgroundLocation.WithoutForegroundLocation, backgroundLocationOf(BACKGROUND, ANDROID_14, ANDROID_15) { false })
        assertEquals(BackgroundLocation.WithoutForegroundLocation, backgroundLocationOf(BACKGROUND, ANDROID_10, ANDROID_10) { false })
        assertFalse(BackgroundLocation.WithoutForegroundLocation.requestable)
        assertIs<RuntimeRequest.Refused>(runtimeRequestFor(BACKGROUND, BackgroundLocation.WithoutForegroundLocation, declared = setOf(BACKGROUND, FINE, COARSE)))
    }

    @Test
    fun `background location is asked for on the settings page from Android 11 when the app targets 11 and fine or coarse location is granted`() {
        assertEquals(BackgroundLocation.OnSettingsPage, backgroundLocationOf(BACKGROUND, ANDROID_11, ANDROID_11) { it == COARSE })
        assertEquals(BackgroundLocation.OnSettingsPage, backgroundLocationOf(BACKGROUND, ANDROID_14, ANDROID_15) { it == FINE })
        assertTrue(BackgroundLocation.OnSettingsPage.requestable)

        val request = assertIs<RuntimeRequest.Ask>(runtimeRequestFor(BACKGROUND, BackgroundLocation.OnSettingsPage, declared = setOf(BACKGROUND, FINE, COARSE)))
        assertEquals(listOf(BACKGROUND), request.permissions)
        assertNotNull(request.caveat)
    }

    @Test
    fun `background location before Android 11 or in an app that targets older is read from its denials like other runtime permissions`() {
        assertNull(backgroundLocationOf(BACKGROUND, ANDROID_10, ANDROID_15) { it == FINE })
        assertNull(backgroundLocationOf(BACKGROUND, ANDROID_14, ANDROID_10) { it == FINE })
    }

    @Test
    fun `permissions other than background location are read from their denials`() {
        assertNull(backgroundLocationOf(CAMERA, ANDROID_14, ANDROID_15) { false })
        assertNull(backgroundLocationOf(FINE, ANDROID_14, ANDROID_15) { false })
    }
}
