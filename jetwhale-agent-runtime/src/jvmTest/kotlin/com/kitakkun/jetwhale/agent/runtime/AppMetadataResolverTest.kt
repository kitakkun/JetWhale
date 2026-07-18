package com.kitakkun.jetwhale.agent.runtime

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppMetadataResolverTest {

    @Test
    fun `encodeAppIconOrNull returns null for a null icon`() {
        assertNull(encodeAppIconOrNull(null))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `encodeAppIconOrNull encodes a small icon as base64`() {
        val png = byteArrayOf(1, 2, 3, 4)
        assertEquals(Base64.encode(png), encodeAppIconOrNull(png))
    }

    @Test
    fun `encodeAppIconOrNull drops an icon whose encoded form exceeds the cap`() {
        // 3 raw bytes encode to 4 base64 chars, so this comfortably exceeds the encoded-length cap.
        val oversized = ByteArray(MAX_APP_ICON_BASE64_LENGTH) { 0 }
        assertNull(encodeAppIconOrNull(oversized))
    }

    @Test
    fun `encodeAppIconOrNull keeps an icon right at the encoded-length cap`() {
        // Raw byte count that encodes to exactly the cap length (base64 encodes 3 bytes to 4 chars).
        val rawSize = MAX_APP_ICON_BASE64_LENGTH / 4 * 3
        val encoded = encodeAppIconOrNull(ByteArray(rawSize) { 0 })
        assertNotNull(encoded)
        assertEquals(MAX_APP_ICON_BASE64_LENGTH, encoded.length)
    }

    @Test
    fun `resolveAppMetadata prefers explicit values over auto-resolved ones`() {
        val config = ResolvedAppConfiguration(
            appName = "Explicit App",
            deviceId = "explicit-device-id",
            deviceName = "Explicit Device",
            appIconPng = byteArrayOf(9, 8, 7),
        )

        val metadata = resolveAppMetadata(config)

        assertEquals("Explicit App", metadata.appName)
        assertEquals("explicit-device-id", metadata.deviceId)
        assertEquals("Explicit Device", metadata.deviceName)
        assertEquals(encodeAppIconOrNull(byteArrayOf(9, 8, 7)), metadata.appIconPngBase64)
    }

    @Test
    fun `resolveAppMetadata falls back to the platform device name when unset`() {
        val config = ResolvedAppConfiguration(
            appName = null,
            deviceId = null,
            deviceName = null,
            appIconPng = null,
        )

        val metadata = resolveAppMetadata(config)

        // On the JVM the device name falls back to the OS name and is never blank.
        assertEquals(getDeviceModelName(), metadata.deviceName)
    }
}
