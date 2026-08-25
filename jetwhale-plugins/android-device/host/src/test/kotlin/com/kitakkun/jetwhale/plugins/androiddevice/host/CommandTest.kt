package com.kitakkun.jetwhale.plugins.androiddevice.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpContent
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpCommand.call(build: JsonObjectBuilder.() -> Unit = {}): JetWhaleMcpResult = runBlocking {
    execute(JetWhaleMcpArguments(buildJsonObject(build)))
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpResult.textJson(): JsonObject = kotlinx.serialization.json.Json
    .parseToJsonElement(content.filterIsInstance<JetWhaleMcpContent.Text>().single().text)
    .jsonObject

/** The screen every device fixture reports, so a coordinate assertion has something to be out of. */
private val SCREEN_RULES = listOf(
    reply("wm size", "Physical size: 1080x2400\n"),
    reply("wm density", "Physical density: 440\n"),
)

private fun devicesRule(output: String = ONE_DEVICE_ATTACHED) = reply("devices -l", output)

@OptIn(ExperimentalJetWhaleApi::class)
class DeviceTargetingTest {
    @Test
    fun `resolves the single connected device when no serial is given`() {
        val adb = FakeAdb(listOf(devicesRule()) + SCREEN_RULES)

        val json = TapCommand(adb).call {
            put("x", 10)
            put("y", 20)
        }.textJson()

        assertEquals(TEST_SERIAL, json["serial"]?.jsonPrimitive?.content)
        assertTrue(adb.commands.any { it == "-s $TEST_SERIAL shell input tap 10 20" })
    }

    @Test
    fun `refuses a serial no device is listed under, and names the ones that are`() {
        val adb = FakeAdb(listOf(devicesRule()) + SCREEN_RULES)

        val error = assertFailsWith<JetWhaleMcpArgumentException> {
            TapCommand(adb).call {
                put("serial", "emulator-9999")
                put("x", 10)
                put("y", 20)
            }
        }

        assertTrue(error.message!!.contains("unknown serial: emulator-9999"))
        assertTrue(error.message!!.contains("$TEST_SERIAL (device)"))
    }

    @Test
    fun `refuses a device that is listed but not in state device`() {
        val adb = FakeAdb(listOf(devicesRule("List of devices attached\n$TEST_SERIAL          offline\n")))

        val error = assertFailsWith<JetWhaleMcpArgumentException> {
            TapCommand(adb).call {
                put("serial", TEST_SERIAL)
                put("x", 10)
                put("y", 20)
            }
        }

        assertTrue(error.message!!.contains("state 'offline'"))
    }

    @Test
    fun `refuses to guess when several devices are connected`() {
        val adb = FakeAdb(
            listOf(
                devicesRule(
                    "List of devices attached\n" +
                        "emulator-5554          device transport_id:1\n" +
                        "emulator-5556          device transport_id:2\n",
                ),
            ),
        )

        val error = assertFailsWith<JetWhaleMcpArgumentException> {
            TapCommand(adb).call {
                put("x", 10)
                put("y", 20)
            }
        }

        assertTrue(error.message!!.contains("several devices"))
        assertTrue(error.message!!.contains("emulator-5556"))
    }

    @Test
    fun `says so when no device is connected at all`() {
        val adb = FakeAdb(listOf(devicesRule("List of devices attached\n")))

        val error = assertFailsWith<JetWhaleMcpArgumentException> {
            TapCommand(adb).call {
                put("x", 10)
                put("y", 20)
            }
        }

        assertTrue(error.message!!.contains("no device is connected"))
    }

    @Test
    fun `reports the adb argument vectors it ran, in order`() {
        val adb = FakeAdb(listOf(devicesRule()) + SCREEN_RULES)

        val json = TapCommand(adb).call {
            put("x", 10)
            put("y", 20)
        }.textJson()

        val ran = json["adb"]!!.jsonArray.map { vector -> vector.jsonArray.map { it.jsonPrimitive.content } }
        assertEquals(listOf("devices", "-l"), ran.first())
        assertEquals(listOf("-s", TEST_SERIAL, "shell", "input", "tap", "10", "20"), ran.last())
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
class PointerInputTest {
    @Test
    fun `rejects a tap that would land off the screen, naming the size`() {
        val adb = FakeAdb(listOf(devicesRule()) + SCREEN_RULES)

        val error = assertFailsWith<JetWhaleMcpArgumentException> {
            TapCommand(adb).call {
                put("x", 1080)
                put("y", 100)
            }
        }

        assertTrue(error.message!!.contains("1080x2400"))
        assertFalse(adb.commands.any { it.contains("input tap") })
    }

    @Test
    fun `converts dp coordinates with the device density`() {
        val adb = FakeAdb(listOf(devicesRule()) + SCREEN_RULES)

        TapCommand(adb).call {
            put("x", 100)
            put("y", 200)
            put("unit", "DP")
        }

        assertTrue(adb.commands.any { it.endsWith("input tap 275 550") })
    }

    @Test
    fun `holds a long press past the platform's long-press timeout`() {
        val adb = FakeAdb(listOf(devicesRule()) + SCREEN_RULES)

        LongPressCommand(adb).call {
            put("x", 10)
            put("y", 20)
        }

        assertTrue(adb.commands.any { it.endsWith("input swipe 10 20 10 20 800") })
    }

    @Test
    fun `checks both ends of a swipe`() {
        val adb = FakeAdb(listOf(devicesRule()) + SCREEN_RULES)

        val error = assertFailsWith<JetWhaleMcpArgumentException> {
            SwipeCommand(adb).call {
                put("fromX", 100)
                put("fromY", 100)
                put("toX", 100)
                put("toY", 4000)
            }
        }

        assertTrue(error.message!!.contains("toY=4000"))
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
class TypeCommandTest {
    @Test
    fun `escapes spaces and quotes on the way to input text`() {
        val adb = FakeAdb(listOf(devicesRule()) + SCREEN_RULES)

        TypeCommand(adb).call { put("text", "it's a test") }

        assertTrue(adb.commands.any { it.endsWith("shell input text it\\'s%sa%stest") })
    }

    @Test
    fun `refuses text input text cannot type, instead of sending garbage`() {
        val adb = FakeAdb(listOf(devicesRule()) + SCREEN_RULES)

        val error = assertFailsWith<JetWhaleMcpArgumentException> {
            TypeCommand(adb).call { put("text", "héllo") }
        }

        assertTrue(error.message!!.contains("printable ASCII"))
        assertFalse(adb.commands.any { it.contains("input text") })
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
class AppCommandTest {
    @Test
    fun `resolves the launcher activity when none is named`() {
        val adb = FakeAdb(
            listOf(
                devicesRule(),
                reply("resolve-activity", "priority=0 preferredOrder=0\n$TEST_PACKAGE/.MainActivity\n"),
                reply("am start", "Starting: Intent { cmp=$TEST_PACKAGE/.MainActivity }\n"),
            ),
        )

        val json = LaunchAppCommand(adb).call { put("packageName", TEST_PACKAGE) }.textJson()

        assertEquals("$TEST_PACKAGE/.MainActivity", json["component"]?.jsonPrimitive?.content)
        assertTrue(adb.commands.any { it.endsWith("shell am start -n '$TEST_PACKAGE/.MainActivity'") })
    }

    @Test
    fun `reports an am start error as a failed result rather than a success`() {
        val adb = FakeAdb(
            listOf(
                devicesRule(),
                reply("resolve-activity", "$TEST_PACKAGE/.MainActivity\n"),
                reply("am start", "Error: Activity class does not exist.\n"),
            ),
        )

        val json = LaunchAppCommand(adb).call { put("packageName", TEST_PACKAGE) }.textJson()

        assertFalse(json["ok"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(json["output"]!!.jsonPrimitive.content.contains("Activity class does not exist"))
    }

    @Test
    fun `names the install failure reason`() {
        val apk = File.createTempFile("android-device-test", ".apk").apply { deleteOnExit() }
        val adb = FakeAdb(listOf(devicesRule(), reply("install", "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]\n")))

        val json = InstallApkCommand(adb).call { put("apkPath", apk.absolutePath) }.textJson()

        assertFalse(json["ok"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("INSTALL_FAILED_UPDATE_INCOMPATIBLE", json["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun `reinstalls and grants permissions by default, because that is what a QA loop needs`() {
        val apk = File.createTempFile("android-device-test", ".apk").apply { deleteOnExit() }
        val adb = FakeAdb(listOf(devicesRule(), reply("install", "Success\n")))

        InstallApkCommand(adb).call { put("apkPath", apk.absolutePath) }

        assertTrue(adb.commands.any { it.contains("install -r -g ") })
    }

    @Test
    fun `refuses an apk path that is not a file on this machine`() {
        val adb = FakeAdb(listOf(devicesRule()))

        val error = assertFailsWith<JetWhaleMcpArgumentException> {
            InstallApkCommand(adb).call { put("apkPath", "/nonexistent/example.apk") }
        }

        assertTrue(error.message!!.contains("no such file"))
    }

    @Test
    fun `refuses a package name that cannot be one`() {
        val adb = FakeAdb(listOf(devicesRule()))

        assertFailsWith<JetWhaleMcpArgumentException> {
            StopAppCommand(adb).call { put("packageName", "not a package; rm -rf /") }
        }
    }

    @Test
    fun `reports the foreground activity`() {
        val adb = FakeAdb(
            listOf(
                devicesRule(),
                reply("dumpsys activity activities", "  topResumedActivity=ActivityRecord{bbb u0 $TEST_PACKAGE/.MainActivity t42}\n"),
            ),
        )

        val json = CurrentActivityCommand(adb).call().textJson()

        assertEquals(TEST_PACKAGE, json["packageName"]?.jsonPrimitive?.content)
        assertEquals(".MainActivity", json["activity"]?.jsonPrimitive?.content)
    }

    @Test
    fun `builds a generic intent from the parts it is given`() {
        val adb = FakeAdb(listOf(devicesRule(), reply("am start", "Starting: Intent { ... }\n")))

        StartActivityCommand(adb).call {
            put("action", "android.intent.action.VIEW")
            put("dataUri", "https://example.com/orders/42")
            put(
                "extras",
                buildJsonObject { put("source", "qa run") },
            )
        }

        assertTrue(
            adb.commands.any {
                it.endsWith("shell am start -a 'android.intent.action.VIEW' -d 'https://example.com/orders/42' --es 'source' 'qa run'")
            },
        )
    }

    @Test
    fun `refuses an intent with neither an action nor a component`() {
        val adb = FakeAdb(listOf(devicesRule()))

        assertFailsWith<JetWhaleMcpArgumentException> { StartActivityCommand(adb).call() }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
class LogcatCommandTest {
    @Test
    fun `bounds the output and selects the app's own process`() {
        val adb = FakeAdb(
            listOf(
                devicesRule(),
                reply("pidof", "4321\n"),
                reply("logcat -d", "08-25 19:00:00.000  4321  4321 I Example: hello\n"),
            ),
        )

        val json = LogcatCommand(adb).call { put("packageName", TEST_PACKAGE) }.textJson()

        assertEquals(4321, json["pid"]?.jsonPrimitive?.content?.toInt())
        assertTrue(adb.commands.any { it.endsWith("shell logcat -d -t 200 --pid 4321") })
    }

    @Test
    fun `silences everything but the requested tag`() {
        val adb = FakeAdb(listOf(devicesRule(), reply("logcat -d", "")))

        LogcatCommand(adb).call {
            put("tag", "Example")
            put("priority", "W")
            put("lines", 50)
        }

        assertTrue(adb.commands.any { it.endsWith("shell logcat -d -t 50 'Example:W' '*:S'") })
    }

    @Test
    fun `says why an app with no running process cannot be filtered by pid`() {
        val adb = FakeAdb(listOf(devicesRule(), reply("pidof", "")))

        val json = LogcatCommand(adb).call { put("packageName", TEST_PACKAGE) }.textJson()

        assertFalse(json["ok"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(json["error"]!!.jsonPrimitive.content.contains("no running process"))
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
class ScreenshotCommandTest {
    @Test
    fun `returns the capture as an image block next to its description`() {
        val png = onePixelPng()
        val adb = FakeAdb(listOf(devicesRule()), streamBytes = png)

        val result = ScreenshotCommand(adb).call()

        val image = result.content.filterIsInstance<JetWhaleMcpContent.Image>().single()
        assertEquals("image/png", image.mimeType)
        assertEquals(png.size, image.data.size)
        val json = result.textJson()
        assertEquals(1, json["width"]?.jsonPrimitive?.content?.toInt())
        assertTrue(adb.commands.any { it == "-s $TEST_SERIAL exec-out screencap -p" })
    }

    @Test
    fun `refuses a scale outside the range it can honour`() {
        val adb = FakeAdb(listOf(devicesRule()), streamBytes = onePixelPng())

        assertFailsWith<JetWhaleMcpArgumentException> {
            ScreenshotCommand(adb).call { put("scale", 1.5) }
        }
    }

    private fun onePixelPng(): ByteArray {
        val image = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val bytes = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(image, "png", bytes)
        return bytes.toByteArray()
    }
}
