package com.kitakkun.jetwhale.plugins.permissions.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
private const val ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
private const val ACCESS_BACKGROUND_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION"
private const val CAMERA = "android.permission.CAMERA"

private const val ANDROID_10 = 29
private const val ANDROID_11 = 30
private const val ANDROID_14 = 34
private const val ANDROID_15 = 35

class RuntimeRequestTest {
    @Test
    fun `fine location is asked for with coarse location when the app declares both`() {
        assertEquals(
            RuntimeRequest.Ask(listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION), caveat = null),
            runtimeRequestFor(ACCESS_FINE_LOCATION, RuntimeDenial.Unknown, declaredPermissions = setOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)),
        )
    }

    @Test
    fun `fine location is asked for alone with a caveat when the app does not declare coarse location`() {
        val request = assertIs<RuntimeRequest.Ask>(runtimeRequestFor(ACCESS_FINE_LOCATION, RuntimeDenial.Once, declaredPermissions = setOf(ACCESS_FINE_LOCATION)))

        assertEquals(listOf(ACCESS_FINE_LOCATION), request.permissions)
        assertNotNull(request.caveat)
    }

    @Test
    fun `any other permission is asked for alone`() {
        assertEquals(
            RuntimeRequest.Ask(listOf(CAMERA), caveat = null),
            runtimeRequestFor(CAMERA, RuntimeDenial.Once, declaredPermissions = setOf(CAMERA, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)),
        )
    }

    @Test
    fun `a permanently denied permission is refused`() {
        assertIs<RuntimeRequest.Refused>(runtimeRequestFor(CAMERA, RuntimeDenial.Permanently, declaredPermissions = setOf(CAMERA)))
    }

    @Test
    fun `background location is neither requestable nor asked for on any Android version while no foreground location is granted`() {
        assertEquals(BackgroundLocationOutlook.WithoutForegroundLocation, backgroundLocationOutlookOf(ACCESS_BACKGROUND_LOCATION, ANDROID_14, ANDROID_15) { false })
        assertEquals(BackgroundLocationOutlook.WithoutForegroundLocation, backgroundLocationOutlookOf(ACCESS_BACKGROUND_LOCATION, ANDROID_10, ANDROID_10) { false })
        assertFalse(BackgroundLocationOutlook.WithoutForegroundLocation.requestable)
        assertIs<RuntimeRequest.Refused>(runtimeRequestFor(ACCESS_BACKGROUND_LOCATION, BackgroundLocationOutlook.WithoutForegroundLocation, declaredPermissions = setOf(ACCESS_BACKGROUND_LOCATION, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)))
    }

    @Test
    fun `background location is asked for on the settings page from Android 11 when the app targets 11 and fine or coarse location is granted`() {
        assertEquals(BackgroundLocationOutlook.OnSettingsPage, backgroundLocationOutlookOf(ACCESS_BACKGROUND_LOCATION, ANDROID_11, ANDROID_11) { it == ACCESS_COARSE_LOCATION })
        assertEquals(BackgroundLocationOutlook.OnSettingsPage, backgroundLocationOutlookOf(ACCESS_BACKGROUND_LOCATION, ANDROID_14, ANDROID_15) { it == ACCESS_FINE_LOCATION })
        assertTrue(BackgroundLocationOutlook.OnSettingsPage.requestable)

        val request = assertIs<RuntimeRequest.Ask>(runtimeRequestFor(ACCESS_BACKGROUND_LOCATION, BackgroundLocationOutlook.OnSettingsPage, declaredPermissions = setOf(ACCESS_BACKGROUND_LOCATION, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)))
        assertEquals(listOf(ACCESS_BACKGROUND_LOCATION), request.permissions)
        assertNotNull(request.caveat)
    }

    @Test
    fun `background location before Android 11 or in an app that targets older is read from its denials like other runtime permissions`() {
        assertNull(backgroundLocationOutlookOf(ACCESS_BACKGROUND_LOCATION, ANDROID_10, ANDROID_15) { it == ACCESS_FINE_LOCATION })
        assertNull(backgroundLocationOutlookOf(ACCESS_BACKGROUND_LOCATION, ANDROID_14, ANDROID_10) { it == ACCESS_FINE_LOCATION })
    }

    @Test
    fun `permissions other than background location are read from their denials`() {
        assertNull(backgroundLocationOutlookOf(CAMERA, ANDROID_14, ANDROID_15) { false })
        assertNull(backgroundLocationOutlookOf(ACCESS_FINE_LOCATION, ANDROID_14, ANDROID_15) { false })
    }
}
