package com.kitakkun.jetwhale.plugins.androiddevice.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdbDeviceParsingTest {
    @Test
    fun `reads the serial, state and long-form properties of every listed device`() {
        val devices = parseAdbDevices(
            "List of devices attached\n" +
                "emulator-5554          device product:sdk_gphone64_arm64 model:sdk_gphone64_arm64 device:emu64a transport_id:1\n" +
                "1A2B3C4D5E             device product:example_product model:Example_Model device:example transport_id:4\n",
        )

        assertEquals(2, devices.size)
        assertEquals("emulator-5554", devices[0].serial)
        assertEquals("device", devices[0].state)
        assertEquals("sdk_gphone64_arm64", devices[0].model)
        assertEquals("1", devices[0].transportId)
        assertTrue(devices[0].isEmulator)
        assertEquals("Example_Model", devices[1].model)
        assertTrue(!devices[1].isEmulator)
    }

    @Test
    fun `keeps unusable devices so a caller can be told why its serial does not work`() {
        val devices = parseAdbDevices(
            "List of devices attached\n" +
                "emulator-5554          offline\n" +
                "1A2B3C4D5E             unauthorized\n",
        )

        assertEquals(listOf("offline", "unauthorized"), devices.map { it.state })
        assertTrue(devices.none { it.isUsable })
    }

    @Test
    fun `ignores the header and adb's own daemon chatter`() {
        val devices = parseAdbDevices(
            "* daemon not running; starting now at tcp:5037\n" +
                "* daemon started successfully\n" +
                "List of devices attached\n",
        )

        assertTrue(devices.isEmpty())
    }
}

class ScreenMetricsParsingTest {
    @Test
    fun `prefers the override size, because that is what the window manager is driving`() {
        val size = parseWmSize("Physical size: 1080x2400\nOverride size: 720x1600\n")

        assertEquals(ScreenSize(720, 1600), size)
    }

    @Test
    fun `falls back to the physical size when nothing is overridden`() {
        assertEquals(ScreenSize(1080, 2400), parseWmSize("Physical size: 1080x2400\n"))
    }

    @Test
    fun `reads the density the same way round`() {
        assertEquals(320, parseWmDensity("Physical density: 440\nOverride density: 320\n"))
        assertEquals(440, parseWmDensity("Physical density: 440\n"))
    }

    @Test
    fun `converts dp with the device density`() {
        assertEquals(200, dpToPixels(100, density = 320))
        assertEquals(100, dpToPixels(100, density = 160))
    }

    @Test
    fun `converts the degrees the window manager names into a Surface rotation index`() {
        assertEquals(0, parseRotation("      mCurrentRotation=ROTATION_0\n"))
        assertEquals(1, parseRotation("      mCurrentRotation=ROTATION_90\n"))
        assertEquals(2, parseRotation("      mCurrentRotation=ROTATION_180\n"))
        assertEquals(3, parseRotation("      mCurrentRotation=ROTATION_270\n"))
    }

    @Test
    fun `falls back to the index mRotation already carries`() {
        assertEquals(3, parseRotation("  DisplayRotation\n    mRotation=3 mDeferredRotationPauseCount=0\n"))
    }

    @Test
    fun `reads the bracketed getprop listing`() {
        val properties = parseGetProps(
            "[ro.product.model]: [Example Model]\n" +
                "[ro.build.version.sdk]: [36]\n" +
                "[ro.product.manufacturer]: [Example]\n",
        )

        assertEquals("Example Model", properties["ro.product.model"])
        assertEquals("36", properties["ro.build.version.sdk"])
    }
}

class PackageParsingTest {
    @Test
    fun `reads the version of an installed package`() {
        val app = parseInstalledApp(
            "Packages:\n" +
                "  Package [$TEST_PACKAGE] (1a2b3c):\n" +
                "    userId=10123\n" +
                "    versionCode=12 minSdk=24 targetSdk=36\n" +
                "    versionName=1.2.3\n" +
                "    firstInstallTime=2026-01-02 03:04:05\n",
            TEST_PACKAGE,
        )

        assertEquals("1.2.3", app?.versionName)
        assertNull(parseInstalledApp("  Package [$TEST_PACKAGE] (1a2b3c):\n    versionName=null\n", TEST_PACKAGE)?.versionName)
        assertEquals(12L, app?.versionCode)
        assertEquals("2026-01-02 03:04:05", app?.firstInstallTime)
    }

    @Test
    fun `reports a package with no block of its own as not installed`() {
        assertNull(parseInstalledApp("Unable to find package: $TEST_PACKAGE\n", TEST_PACKAGE))
    }

    @Test
    fun `reads the top resumed activity`() {
        val top = parseTopActivity(
            "  ResumedActivity: ActivityRecord{aaa u0 com.example.qa.other/.OtherActivity t1}\n" +
                "  topResumedActivity=ActivityRecord{bbb u0 $TEST_PACKAGE/.MainActivity t42}\n",
        )

        assertEquals(TEST_PACKAGE, top?.packageName)
        assertEquals(".MainActivity", top?.activity)
    }

    @Test
    fun `falls back to mResumedActivity on builds that do not report a top one`() {
        val top = parseTopActivity("    mResumedActivity: ActivityRecord{ccc u0 $TEST_PACKAGE/.MainActivity t7}\n")

        assertEquals(TEST_PACKAGE, top?.packageName)
        assertEquals(".MainActivity", top?.activity)
    }

    @Test
    fun `reads the component resolve-activity prints in its brief form`() {
        val resolved = parseResolvedActivity(
            "priority=0 preferredOrder=0 match=0x0 specificIndex=-1 isDefault=false\n" +
                "$TEST_PACKAGE/.MainActivity\n",
        )

        assertEquals(TEST_PACKAGE, resolved?.packageName)
        assertEquals(".MainActivity", resolved?.activity)
    }

    @Test
    fun `reports no component when nothing resolves`() {
        assertNull(parseResolvedActivity("No activity found\n"))
    }

    @Test
    fun `names the install failure reason rather than only the exit code`() {
        assertEquals("INSTALL_FAILED_UPDATE_INCOMPATIBLE", parseInstallFailure("Performing Streamed Install\nFailure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]\n"))
        assertNull(parseInstallFailure("Performing Streamed Install\nSuccess\n"))
    }

    @Test
    fun `reads the first pid pidof prints`() {
        assertEquals(4321, parsePid("4321 4399\n"))
        assertNull(parsePid("\n"))
    }
}
