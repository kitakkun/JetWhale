package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.LocalRunningMcpCommandNames
import com.kitakkun.jetwhale.host.ui.JwColors
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalJetWhaleApi::class)
class DeviceToolbarTest {
    @Test
    fun `each device button is announced by its label once`() = runComposeUiTest {
        setContent { JwTheme(darkTheme = false) { Toolbar(emulator, androidCapabilities, ScreenPower(awake = true, locked = false)) } }

        listOf("Home", "Back", "Recent apps", "Power", "Volume up", "Volume down", "Screen off").forEach { label ->
            assertEquals(listOf(label), contentDescriptionOf(label))
        }
    }

    @Test
    fun `a simulator's disabled volume buttons are announced with the reason once`() = runComposeUiTest {
        setContent { JwTheme(darkTheme = false) { Toolbar(simulator, simulatorCapabilities, screenPower = null) } }

        val label = "Volume up: a simulator's volume buttons cannot be pressed from here"
        assertEquals(listOf(label), contentDescriptionOf(label))
    }

    @Test
    fun `a control is ringed only while an agent runs the command that does the same`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var runningCommandNames by mutableStateOf(emptySet<String>())
        setContent {
            JwTheme(darkTheme = false) {
                CompositionLocalProvider(LocalRunningMcpCommandNames provides runningCommandNames) {
                    Toolbar(emulator, androidCapabilities, ScreenPower(awake = true, locked = false))
                }
            }
        }
        val controlLabels = listOf("Home", "Volume up", "Screen off", "Record", "Screenshot")
        mapOf(
            toolbarMcpCommands.pressButton to listOf("Home", "Volume up"),
            toolbarMcpCommands.setScreen to listOf("Screen off"),
            toolbarMcpCommands.startRecording to listOf("Record"),
            toolbarMcpCommands.stopRecording to listOf("Record"),
            toolbarMcpCommands.captureScreenshot to listOf("Screenshot"),
            toolbarMcpCommands.listDevices to emptyList(),
        ).forEach { (command, ringedLabels) ->
            runningCommandNames = setOf(command.name)
            mainClock.advanceTimeByFrame()

            assertEquals(ringedLabels, controlLabels.filter { isControlRinged(it) }, "while ${command.name} runs")
        }
    }

    /** Whether the AI accent shows on the control: nothing else in the toolbar is drawn in it. */
    private fun SemanticsNodeInteractionsProvider.isControlRinged(label: String): Boolean {
        val aiAccent = JwColors.light().aiAccent
        val pixels = onNodeWithContentDescription(label).captureToImage().toPixelMap()
        return (0 until pixels.width).any { x ->
            (0 until pixels.height).any { y ->
                val pixel = pixels[x, y]
                abs(pixel.red - aiAccent.red) < ACCENT_TOLERANCE && abs(pixel.green - aiAccent.green) < ACCENT_TOLERANCE && abs(pixel.blue - aiAccent.blue) < ACCENT_TOLERANCE
            }
        }
    }

    private fun SemanticsNodeInteractionsProvider.contentDescriptionOf(label: String): List<String>? = onNodeWithContentDescription(label).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)

    private val emulator = DeviceListing(id = "emulator-5554", name = "Pixel 9", kind = DeviceKind.AndroidEmulator, osVersion = null)

    private val simulator = DeviceListing(id = "0A1B2C3D-SIMULATOR", name = "iPhone 16", kind = DeviceKind.IosSimulator, osVersion = "iOS 18.5")

    private val androidCapabilities = DeviceCapabilities(inputRefusal = null, buttons = DeviceButton.entries, recording = true, screenPower = true)

    private val simulatorCapabilities = DeviceCapabilities(
        inputRefusal = null,
        buttons = listOf(DeviceButton.Home, DeviceButton.Recents, DeviceButton.Power),
        recording = true,
        screenPower = false,
    )

    private companion object {
        /** Close enough to count a pixel where the ring's highlight peaks, far enough to tell it from the toolbar's greys. */
        const val ACCENT_TOLERANCE = 0.1f
    }
}

private val toolbarMcpCommands = MirrorMcpCommands(NoMirrorDevices)

@Composable
private fun Toolbar(device: DeviceListing, capabilities: DeviceCapabilities, screenPower: ScreenPower?) {
    DeviceToolbar(
        pane = DevicePaneState(
            devices = listOf(device),
            device = device,
            capabilities = capabilities,
            state = MirrorState.Streaming,
            screenPower = screenPower,
            recordingSinceMillis = null,
        ),
        actions = NoMirrorActions,
        mcpCommands = toolbarMcpCommands,
        showCaptures = false,
        livenessOf = { DeviceLiveness.Live },
        onToggleCaptures = {},
        onShowGrid = {},
    )
}

private object NoMirrorDevices : MirrorDevices {
    override val selectedId: String? = null

    override suspend fun refresh(): List<MirrorDevice> = emptyList()

    override fun resolve(deviceId: String?): MirrorDevice = error("A toolbar test has no devices")

    override suspend fun saveScreenshot(device: MirrorDevice): Capture = error("A toolbar test has no devices")

    override suspend fun startRecording(device: MirrorDevice) = Unit

    override suspend fun startRecordings(deviceIds: List<String>?): List<RecordingResult> = emptyList()

    override suspend fun stopRecording(deviceId: String?): Capture = error("A toolbar test has no devices")

    override suspend fun stopRecordings(deviceIds: List<String>?): List<RecordingResult> = emptyList()

    override fun listCaptures(deviceId: String?, kind: CaptureKind?, sinceEpochMillis: Long?): List<Capture> = emptyList()
}

private object NoMirrorActions : MirrorActions {
    override fun select(deviceId: String) = Unit

    override fun tap(x: Int, y: Int) = Unit

    override fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int) = Unit

    override fun pressButton(button: DeviceButton) = Unit

    override fun inputText(text: String) = Unit

    override fun saveScreenshot() = Unit

    override fun recordSelectedDevice() = Unit

    override fun stopSelectedRecording() = Unit

    override fun wake() = Unit

    override fun sleep() = Unit

    override fun retryLiveVideo() = Unit

    override val developmentTeam: String? get() = null

    override fun updateDevelopmentTeam(team: String?) = Unit
}
