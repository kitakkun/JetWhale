package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalJetWhaleApi::class)
class MirrorMcpCommandsTest {
    private val emulator = FakeController(DeviceCapabilities(input = true, buttons = listOf(DeviceButton.Home, DeviceButton.Back), recording = true))
    private val iphone = FakeController(DeviceCapabilities(input = false, buttons = emptyList(), recording = false), refusal = VIEW_ONLY)
    private val mirror = FakeMirrorDevices(
        listOf(
            MirrorDevice(DeviceListing("emulator-5554", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null), emulator),
            MirrorDevice(DeviceListing("00008110", "iPhone 13", DeviceKind.IosDevice, osVersion = "iOS 26.6.1"), iphone),
        ),
    )

    @Test
    fun `listDevices tells a view-only iPhone from a device that takes input`() {
        val devices = ListDevicesCommand(mirror).run().getValue("devices").jsonArray.map(JsonElement::jsonObject)

        val phone = devices.single { it.getValue("deviceId").jsonPrimitive.content == "00008110" }
        assertEquals("iOS", phone.getValue("platform").jsonPrimitive.content)
        assertFalse(phone.getValue("input").jsonPrimitive.boolean)
        assertTrue(devices.single { it.getValue("deviceId").jsonPrimitive.content == "emulator-5554" }.getValue("input").jsonPrimitive.boolean)
    }

    @Test
    fun `a tap reaches the selected device when no device is named`() {
        TapCommand(mirror).run(
            buildJsonObject {
                put("x", 10)
                put("y", 20)
            },
        )

        assertEquals(listOf("tap 10,20"), emulator.calls)
    }

    @Test
    fun `a tap on a physical iPhone is refused with the reason`() {
        val failure = assertFailsWith<JetWhaleMcpArgumentException> {
            TapCommand(mirror).run(
                buildJsonObject {
                    put("deviceId", "00008110")
                    put("x", 10)
                    put("y", 20)
                },
            )
        }

        assertTrue("view-only" in failure.message.orEmpty())
    }

    @Test
    fun `a swipe passes both ends and the duration in order`() {
        SwipeCommand(mirror).run(
            buildJsonObject {
                put("fromX", 1)
                put("fromY", 2)
                put("toX", 3)
                put("toY", 4)
                put("durationMillis", 500)
            },
        )

        assertEquals(listOf("swipe 1,2 -> 3,4 in 500"), emulator.calls)
    }

    @Test
    fun `a swipe with a negative point, a bad duration or an end off the screen never reaches the device`() {
        val refused = listOf(
            swipe(fromX = -1, toY = 600, durationMillis = null),
            swipe(fromX = 540, toY = 600, durationMillis = -5),
            swipe(fromX = 540, toY = 600, durationMillis = 60_000),
            swipe(fromX = 1080, toY = 600, durationMillis = null),
            swipe(fromX = 540, toY = 2400, durationMillis = null),
        ).map { arguments -> runCatching { SwipeCommand(mirror).run(arguments) }.exceptionOrNull() }

        assertTrue(refused.all { it is JetWhaleMcpArgumentException }, refused.toString())
        assertEquals(emptyList(), emulator.calls)
    }

    @Test
    fun `a swipe on a physical iPhone is refused without asking for its screen size`() {
        val failure = assertFailsWith<JetWhaleMcpArgumentException> {
            SwipeCommand(mirror).run(
                buildJsonObject {
                    put("deviceId", "00008110")
                    put("fromX", 10)
                    put("fromY", 20)
                    put("toX", 10)
                    put("toY", 200)
                },
            )
        }

        assertTrue("view-only" in failure.message.orEmpty())
        assertEquals(0, iphone.screenSizeQueries)
    }

    @Test
    fun `a negative swipe is refused before the device is looked up`() {
        val failure = assertFailsWith<JetWhaleMcpArgumentException> {
            SwipeCommand(mirror).run(
                buildJsonObject {
                    put("deviceId", "nope")
                    put("fromX", -1)
                    put("fromY", 0)
                    put("toX", 0)
                    put("toY", 0)
                },
            )
        }

        assertTrue("negative" in failure.message.orEmpty())
    }

    @Test
    fun `an unknown device is refused with a pointer to listDevices`() {
        val failure = assertFailsWith<JetWhaleMcpArgumentException> {
            PressButtonCommand(mirror).run(
                buildJsonObject {
                    put("deviceId", "nope")
                    put("button", "Home")
                },
            )
        }

        assertTrue("listDevices" in failure.message.orEmpty())
    }

    @Test
    fun `stopping when nothing records is refused`() {
        assertFailsWith<JetWhaleMcpArgumentException> { StopRecordingCommand(mirror).run() }
    }

    @Test
    fun `listCaptures passes its filters on and reports each capture's file, device and length`() {
        val captures = ListCapturesCommand(mirror).run(
            buildJsonObject {
                put("deviceId", "emulator-5554")
                put("kind", "Recording")
                put("since", 1_789_000_000_000)
            },
        ).getValue("captures").jsonArray.map(JsonElement::jsonObject)

        assertEquals(listOf(CaptureQuery("emulator-5554", CaptureKind.Recording, 1_789_000_000_000)), mirror.captureQueries)
        val capture = captures.single()
        assertEquals(File("/captures/Pixel-9-0a1b2c3d/2026-09-25/123005-recording.mp4").absolutePath, capture.getValue("path").jsonPrimitive.content)
        assertEquals("Recording", capture.getValue("kind").jsonPrimitive.content)
        assertEquals(12_400, capture.getValue("durationMillis").jsonPrimitive.long)
    }

    @Test
    fun `listCaptures without filters asks for every device's captures`() {
        ListCapturesCommand(mirror).run()

        assertEquals(listOf(CaptureQuery(deviceId = null, kind = null, sinceEpochMillis = null)), mirror.captureQueries)
    }
}

// A swipe on the 1080x2400 test screen from ([fromX], 1800) to (540, [toY]).
private fun swipe(fromX: Int, toY: Int, durationMillis: Int?) = buildJsonObject {
    put("fromX", fromX)
    put("fromY", 1800)
    put("toX", 540)
    put("toY", toY)
    durationMillis?.let { put("durationMillis", it) }
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpCommand.run(arguments: JsonObject = buildJsonObject { }): JsonObject = runBlocking {
    Json.parseToJsonElement(execute(JetWhaleMcpArguments(arguments))).jsonObject
}

private class FakeMirrorDevices(private val devices: List<MirrorDevice>) : MirrorDevices {
    override val selectedId: String = devices.first().id

    override suspend fun refresh(): List<MirrorDevice> = devices

    override fun resolve(deviceId: String?): MirrorDevice = devices.firstOrNull { it.id == (deviceId ?: selectedId) }
        ?: throw deviceControlError("no device has the id '$deviceId'; call $TOOL_PREFIX.listDevices")

    val captureQueries = mutableListOf<CaptureQuery>()

    override suspend fun saveScreenshot(device: MirrorDevice): Capture = throw deviceControlError("no screenshots in tests")

    override suspend fun startRecording(device: MirrorDevice) = Unit

    override suspend fun stopRecording(): Capture = throw deviceControlError("no recording is running")

    override fun listCaptures(deviceId: String?, kind: CaptureKind?, sinceEpochMillis: Long?): List<Capture> {
        captureQueries += CaptureQuery(deviceId, kind, sinceEpochMillis)
        return listOf(recording)
    }
}

private data class CaptureQuery(val deviceId: String?, val kind: CaptureKind?, val sinceEpochMillis: Long?)

private val recording = Capture(
    file = File("/captures/Pixel-9-0a1b2c3d/2026-09-25/123005-recording.mp4"),
    info = CaptureInfo(
        deviceId = "emulator-5554",
        deviceName = "Pixel 9",
        platform = "Android",
        deviceKind = "Emulator",
        osVersion = null,
        kind = CaptureKind.Recording,
        widthPx = 1080,
        heightPx = 2400,
        capturedAtEpochMillis = 1_790_000_000_000,
        durationMillis = 12_400,
    ),
)

private class FakeController(
    override val capabilities: DeviceCapabilities,
    private val refusal: String? = null,
) : DeviceController {
    val calls = mutableListOf<String>()

    override suspend fun captureScreenshot(): ByteArray = ByteArray(0)

    var screenSizeQueries = 0

    override suspend fun screenSize(): IntSize {
        screenSizeQueries++
        return IntSize(1080, 2400)
    }

    override suspend fun tap(x: Int, y: Int) = record("tap $x,$y")

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = record("swipe $fromX,$fromY -> $toX,$toY in $durationMillis")

    override suspend fun pressButton(button: DeviceButton) = record("press $button")

    override suspend fun inputText(text: String) = record("type $text")

    override suspend fun startRecording(outputFile: File): DeviceRecording = throw deviceControlError("not recording in tests")

    override suspend fun openVideoStream(): Process = throw deviceControlError("no stream in tests")

    override suspend fun release() = Unit

    private fun record(call: String) {
        refusal?.let { throw deviceControlError(it) }
        calls += call
    }
}
