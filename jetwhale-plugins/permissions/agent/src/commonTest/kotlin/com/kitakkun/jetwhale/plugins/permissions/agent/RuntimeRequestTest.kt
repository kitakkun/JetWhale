package com.kitakkun.jetwhale.plugins.permissions.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val FINE = "android.permission.ACCESS_FINE_LOCATION"
private const val COARSE = "android.permission.ACCESS_COARSE_LOCATION"
private const val CAMERA = "android.permission.CAMERA"

class RuntimeRequestTest {
    @Test
    fun `fine location is asked for with coarse location when the app declares both`() {
        assertEquals(RuntimeRequest(listOf(FINE, COARSE), caveat = null), runtimeRequestFor(FINE, declared = setOf(FINE, COARSE)))
    }

    @Test
    fun `fine location is asked for alone with a caveat when the app does not declare coarse location`() {
        val request = runtimeRequestFor(FINE, declared = setOf(FINE))

        assertEquals(listOf(FINE), request.permissions)
        assertNotNull(request.caveat)
    }

    @Test
    fun `any other permission is asked for alone`() {
        assertEquals(RuntimeRequest(listOf(CAMERA), caveat = null), runtimeRequestFor(CAMERA, declared = setOf(CAMERA, FINE, COARSE)))
    }
}
