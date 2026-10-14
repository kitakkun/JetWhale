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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
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

private const val IPHONE_REFUSAL = "driving an iPhone needs your Apple development team"

@OptIn(ExperimentalJetWhaleApi::class)
class MirrorMcpCommandsTest {
    private val emulator = FakeController(DeviceCapabilities(inputRefusal = null, buttons = listOf(DeviceButton.Home, DeviceButton.Back), recording = true, screenPower = true))
    private val iphone = FakeController(DeviceCapabilities(inputRefusal = IPHONE_REFUSAL, buttons = emptyList(), recording = false, screenPower = false), refusal = IPHONE_REFUSAL)
    private val mirror = FakeMirrorDevices(
        listOf(
            MirrorDevice(DeviceListing("emulator-5554", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null), emulator),
            MirrorDevice(DeviceListing("00008110", "iPhone 13", DeviceKind.IosDevice, osVersion = "iOS 26.6.1"), iphone),
        ),
    )

    @Test
    fun `listDevices tells an iPhone that takes no input, and why, from a device that takes input`() {
        val devices = ListDevicesCommand(mirror).run().getValue("devices").jsonArray.map(JsonElement::jsonObject)

        val phone = devices.single { it.getValue("deviceId").jsonPrimitive.content == "00008110" }
        assertEquals("iOS", phone.getValue("platform").jsonPrimitive.content)
        assertFalse(phone.getValue("input").jsonPrimitive.boolean)
        assertEquals(IPHONE_REFUSAL, phone.getValue("inputUnavailableReason").jsonPrimitive.content)
        val android = devices.single { it.getValue("deviceId").jsonPrimitive.content == "emulator-5554" }
        assertTrue(android.getValue("input").jsonPrimitive.boolean)
        assertFalse("inputUnavailableReason" in android)
    }

    @Test
    fun `listDevices reports whether an Android screen is on and says nothing of an iPhone's`() {
        emulator.power = ScreenPower(awake = false, locked = true)

        val devices = ListDevicesCommand(mirror).run().getValue("devices").jsonArray.map(JsonElement::jsonObject)

        val android = devices.single { it.getValue("deviceId").jsonPrimitive.content == "emulator-5554" }
        assertFalse(android.getValue("screenOn").jsonPrimitive.boolean)
        assertTrue(android.getValue("locked").jsonPrimitive.boolean)
        assertFalse("screenOn" in devices.single { it.getValue("deviceId").jsonPrimitive.content == "00008110" })
    }

    @Test
    fun `setScreen turns an Android screen off and answers the state it leaves`() {
        val answer = SetScreenCommand(mirror).run(buildJsonObject { put("on", false) })

        assertEquals(listOf("sleep"), emulator.calls)
        assertFalse(answer.getValue("screenOn").jsonPrimitive.boolean)
    }

    @Test
    fun `setScreen on an iPhone is refused with the reason`() {
        val failure = assertFailsWith<JetWhaleMcpArgumentException> {
            SetScreenCommand(mirror).run(
                buildJsonObject {
                    put("deviceId", "00008110")
                    put("on", true)
                },
            )
        }

        assertEquals(NO_SCREEN_POWER, failure.message)
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
    fun `a tap on a physical iPhone is refused with the reason and without asking for its screen size`() {
        val failure = assertFailsWith<JetWhaleMcpArgumentException> {
            TapCommand(mirror).run(
                buildJsonObject {
                    put("deviceId", "00008110")
                    put("x", 10)
                    put("y", 20)
                },
            )
        }

        assertEquals(IPHONE_REFUSAL, failure.message)
        assertEquals(0, iphone.screenSizeQueries)
    }

    @Test
    fun `text for an iPhone that takes no input is refused with the reason before it reaches the device`() {
        val failure = assertFailsWith<JetWhaleMcpArgumentException> {
            InputTextCommand(mirror).run(
                buildJsonObject {
                    put("deviceId", "00008110")
                    put("text", "hello")
                },
            )
        }

        assertEquals(IPHONE_REFUSAL, failure.message)
        assertEquals(0, iphone.inputAttempts)
    }

    @Test
    fun `keepRunnerAlive refuses a device no XCTest runner drives, and minutes out of range`() {
        val android = assertFailsWith<JetWhaleMcpArgumentException> {
            KeepRunnerAliveCommand(mirror).run(
                buildJsonObject {
                    put("deviceId", "emulator-5554")
                    put("minutes", 30)
                },
            )
        }
        val tooLong = assertFailsWith<JetWhaleMcpArgumentException> {
            KeepRunnerAliveCommand(mirror).run(
                buildJsonObject {
                    put("deviceId", "emulator-5554")
                    put("minutes", 121)
                },
            )
        }

        assertEquals("Pixel 9 is not driven through an XCTest runner; only iOS simulators and devices are", android.message)
        assertEquals("minutes must be from 0 to 120 (got 121)", tooLong.message)
    }

    @Test
    fun `a tap off the screen never reaches the device`() {
        val refused = listOf(1080 to 600, 540 to 2400).map { (x, y) ->
            runCatching {
                TapCommand(mirror).run(
                    buildJsonObject {
                        put("x", x)
                        put("y", y)
                    },
                )
            }.exceptionOrNull()
        }

        assertTrue(refused.all { it is JetWhaleMcpArgumentException }, refused.toString())
        assertEquals(emptyList(), emulator.calls)
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

        assertEquals(IPHONE_REFUSAL, failure.message)
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
    fun `startRecording with all starts every device at once and reports each device's result`() {
        val results = StartRecordingCommand(mirror).run(buildJsonObject { put("all", true) }).getValue("results").jsonArray.map(JsonElement::jsonObject)

        assertEquals(listOf<List<String>?>(null), mirror.recordingRequests)
        assertEquals(listOf("started", "failed"), results.map { it.getValue("status").jsonPrimitive.content })
        assertEquals("cannot record", results[1].getValue("error").jsonPrimitive.content)
    }

    @Test
    fun `startRecording with deviceIds starts just those`() {
        StartRecordingCommand(mirror).run(buildJsonObject { put("deviceIds", buildJsonArray { add("emulator-5554") }) })

        assertEquals(listOf<List<String>?>(listOf("emulator-5554")), mirror.recordingRequests)
    }

    @Test
    fun `startRecording refuses a device together with all`() {
        assertFailsWith<JetWhaleMcpArgumentException> {
            StartRecordingCommand(mirror).run(
                buildJsonObject {
                    put("deviceId", "emulator-5554")
                    put("all", true)
                },
            )
        }
        assertEquals(emptyList(), mirror.recordingRequests)
    }

    @Test
    fun `stopRecording with all reports each saved file`() {
        val result = StopRecordingCommand(mirror).run(buildJsonObject { put("all", true) }).getValue("results").jsonArray.single().jsonObject

        assertEquals("stopped", result.getValue("status").jsonPrimitive.content)
        assertEquals(recording.file.absolutePath, result.getValue("path").jsonPrimitive.content)
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

    val recordingRequests = mutableListOf<List<String>?>()

    override suspend fun startRecordings(deviceIds: List<String>?): List<RecordingResult> {
        recordingRequests += deviceIds
        return (deviceIds ?: devices.map(MirrorDevice::id)).map { id ->
            if (id == devices.first().id) RecordingResult.Started(deviceId = id, deviceName = "Pixel 9") else RecordingResult.Failed(deviceId = id, deviceName = id, reason = "cannot record")
        }
    }

    override suspend fun stopRecording(deviceId: String?): Capture = throw deviceControlError("no recording is running")

    override suspend fun stopRecordings(deviceIds: List<String>?): List<RecordingResult> = listOf(RecordingResult.Saved(recording))

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

    var power = ScreenPower(awake = true, locked = false)

    override suspend fun screenPower(): ScreenPower {
        if (!capabilities.screenPower) throw deviceControlError(NO_SCREEN_POWER)
        return power
    }

    override suspend fun wake() {
        if (!capabilities.screenPower) throw deviceControlError(NO_SCREEN_POWER)
        record("wake")
        power = ScreenPower(awake = true, locked = false)
    }

    override suspend fun sleep() {
        if (!capabilities.screenPower) throw deviceControlError(NO_SCREEN_POWER)
        record("sleep")
        power = ScreenPower(awake = false, locked = true)
    }

    override suspend fun startRecording(outputFile: File): DeviceRecording = throw deviceControlError("not recording in tests")

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream = throw deviceControlError("no stream in tests")

    override suspend fun release() = Unit

    /** Every input that reached the controller, refused or not. */
    var inputAttempts = 0

    private fun record(call: String) {
        inputAttempts++
        refusal?.let { throw deviceControlError(it) }
        calls += call
    }
}
