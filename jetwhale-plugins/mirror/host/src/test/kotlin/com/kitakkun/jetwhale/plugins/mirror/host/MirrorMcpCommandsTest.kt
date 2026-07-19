package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalJetWhaleApi::class)
class MirrorMcpCommandsTest {
    private class FakeDeviceController : DeviceController {
        val taps = mutableListOf<Pair<Int, Int>>()
        val swipes = mutableListOf<List<Int>>()

        override suspend fun captureScreenshot(): ByteArray = ByteArray(0)

        override suspend fun tap(x: Int, y: Int) {
            taps += x to y
        }

        override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) {
            swipes += listOf(fromX, fromY, toX, toY, durationMillis)
        }

        override suspend fun pressButton(button: DeviceButton) = Unit

        override suspend fun inputText(text: String) = Unit

        override suspend fun startRecording(outputFile: File): DeviceRecording = object : DeviceRecording {
            override suspend fun stop(): File = outputFile
        }
    }

    private val controller = FakeDeviceController()
    private val device = MirrorDevice(id = "emulator-5554", name = "Pixel 7", platform = DevicePlatform.ANDROID, controller = controller)

    @Test
    fun `tap forwards coordinates to the device`() = runBlocking {
        val result = TapCommand(resolveDevice = { device }).execute(JetWhaleMcpArguments(mapOf("x" to "10", "y" to "20")))
        assertEquals(listOf(10 to 20), controller.taps)
        assertTrue(Json.parseToJsonElement(result).jsonObject["ok"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `tap rejects negative coordinates`(): Unit = runBlocking {
        assertFailsWith<JetWhaleMcpArgumentException> {
            TapCommand(resolveDevice = { device }).execute(JetWhaleMcpArguments(mapOf("x" to "-1", "y" to "20")))
        }
        assertTrue(controller.taps.isEmpty())
    }

    @Test
    fun `swipe defaults duration to 300ms`() = runBlocking {
        SwipeCommand(resolveDevice = { device }).execute(
            JetWhaleMcpArguments(mapOf("fromX" to "0", "fromY" to "1", "toX" to "2", "toY" to "3")),
        )
        assertEquals(listOf(listOf(0, 1, 2, 3, 300)), controller.swipes)
    }

    @Test
    fun `swipe rejects negative coordinates`(): Unit = runBlocking {
        assertFailsWith<JetWhaleMcpArgumentException> {
            SwipeCommand(resolveDevice = { device }).execute(
                JetWhaleMcpArguments(mapOf("fromX" to "0", "fromY" to "-5", "toX" to "2", "toY" to "3")),
            )
        }
        assertTrue(controller.swipes.isEmpty())
    }

    @Test
    fun `swipe rejects non-positive duration`(): Unit = runBlocking {
        assertFailsWith<JetWhaleMcpArgumentException> {
            SwipeCommand(resolveDevice = { device }).execute(
                JetWhaleMcpArguments(mapOf("fromX" to "0", "fromY" to "1", "toX" to "2", "toY" to "3", "durationMillis" to "0")),
            )
        }
        assertTrue(controller.swipes.isEmpty())
    }

    @Test
    fun `listDevices reports ids, platforms, and the selection`() = runBlocking {
        val result = ListDevicesCommand(refreshDevices = { listOf(device) }, selectedDeviceId = { device.id }).execute(JetWhaleMcpArguments(emptyMap()))
        val devices = Json.parseToJsonElement(result).jsonObject["devices"]!!.jsonArray
        val entry = devices.single().jsonObject
        assertEquals("emulator-5554", entry["deviceId"]!!.jsonPrimitive.content)
        assertEquals("ANDROID", entry["platform"]!!.jsonPrimitive.content)
        assertEquals("true", entry["selected"]!!.jsonPrimitive.content)
    }

    @Test
    fun `stopRecording returns the finished file path`() = runBlocking {
        val file = File("/tmp/jetwhale-mirror-test.mp4")
        val result = StopRecordingCommand(stopRecording = { file }).execute(JetWhaleMcpArguments(emptyMap()))
        assertEquals(file.absolutePath, Json.parseToJsonElement(result).jsonObject["path"]!!.jsonPrimitive.content)
    }

    @Test
    fun `unknown device id surfaces as an argument error`(): Unit = runBlocking {
        assertFailsWith<JetWhaleMcpArgumentException> {
            TapCommand(resolveDevice = { throw JetWhaleMcpArgumentException("unknown deviceId") })
                .execute(JetWhaleMcpArguments(mapOf("deviceId" to "nope", "x" to "1", "y" to "1")))
        }
    }
}
