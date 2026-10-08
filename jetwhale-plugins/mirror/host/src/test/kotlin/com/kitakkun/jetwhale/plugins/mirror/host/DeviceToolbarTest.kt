package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
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
}

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
        showCaptures = false,
        livenessOf = { DeviceLiveness.Live },
        onToggleCaptures = {},
        onShowGrid = {},
    )
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

    override val developmentTeam: String? get() = null

    override fun updateDevelopmentTeam(team: String?) = Unit
}
