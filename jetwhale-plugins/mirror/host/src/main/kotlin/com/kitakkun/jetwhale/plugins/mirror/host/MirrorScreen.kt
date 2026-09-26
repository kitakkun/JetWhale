package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIconButton
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwTooltip
import com.kitakkun.jetwhale.host.ui.JwVerticalDivider
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.time.Clock

/** The live screen keeps most of the pane while the captures sit beside it. */
private const val VIDEO_FRACTION = 0.55f

/** Wide enough for a name like "iPhone 15 Pro"; a longer one is cut, with the full name in the list. */
internal val PICKER_MAX_WIDTH = 200.dp

/** Often enough to follow what a device is doing, rarely enough that a screenshot per device stays cheap. */
private const val THUMBNAIL_REFRESH_MILLIS = 1_500L

/** A screenshot runs adb or simctl; a few at a time keep the grid from flooding the machine. */
private const val MAX_CONCURRENT_THUMBNAIL_CAPTURES = 2

/**
 * Binds the mirror to the screen, and keeps it working only while the screen is shown: the device
 * list refreshes and the selected device streams for as long as this is in composition.
 */
@Composable
internal fun MirrorScreenRoot(mirror: DeviceMirror, modifier: Modifier = Modifier) {
    LaunchedEffect(mirror) { mirror.keepDevicesCurrent() }
    // Both views save captures, so the saved folder is restored whichever one opens first.
    LaunchedEffect(mirror.captures) { mirror.captures.restoreFolder() }
    var showGrid by rememberPersistent("showGrid", default = false)
    var showCaptures by rememberPersistent("showCaptures", default = false)
    // Kept across both views: the grid fills it, and the single view's device picker reads it.
    val thumbnails = remember { DeviceThumbnails(refreshIntervalMillis = THUMBNAIL_REFRESH_MILLIS, maxConcurrentCaptures = MAX_CONCURRENT_THUMBNAIL_CAPTURES, decodeDispatcher = Dispatchers.IO, clock = Clock.System) }
    DisposableEffect(thumbnails) { onDispose(thumbnails::close) }
    val devices = mirror.devices
    LaunchedEffect(devices) { thumbnails.retainOnly(devices.map(MirrorDevice::id).toSet()) }
    val scope = rememberCoroutineScope()
    val notices = object : MirrorNoticeActions {
        override val notice: MirrorNotice? get() = mirror.notices.current

        override fun perform(action: NoticeAction) {
            mirror.notices.dismiss()
            when (action) {
                is NoticeAction.OpenCapture -> openCapture(action.capture, mirror) {
                    showGrid = false
                    showCaptures = true
                }

                is NoticeAction.OpenCaptures -> {
                    mirror.captures.showAllDevices(true)
                    if (mirror.selectedDevice == null) mirror.devices.firstOrNull()?.let { mirror.select(it.id) }
                    showGrid = false
                    showCaptures = true
                }

                is NoticeAction.RetryScreenshots -> saveScreenshots(mirror.devices.filter { it.id in action.deviceIds }, mirror, thumbnails, scope)

                is NoticeAction.RetryRecording -> mirror.retryRecording(action.deviceId)
            }
        }

        override fun dismiss() = mirror.notices.dismiss()

        override fun hold(held: Boolean) = mirror.notices.hold(held)
    }
    // Leaving the single view stops the selected device's stream: the grid captures by screenshot.
    Box(modifier.fillMaxSize()) {
        if (showGrid) {
            DeviceGridRoot(mirror, thumbnails, notices, onShowSingle = { showGrid = false })
        } else {
            SingleMirrorRoot(mirror, thumbnails, notices, showCaptures, onToggleCaptures = { showCaptures = !showCaptures }, onShowGrid = { showGrid = true })
        }
    }
}

/** Where Open takes a capture. */
internal enum class CaptureDestination {
    /** Its device's captures panel, with the capture selected. */
    Panel,

    /** Its file in its folder, since its device is not connected and the panel cannot show it. */
    Folder,

    /** Nowhere: its file is gone. */
    Missing,
}

internal fun destinationOf(capture: Capture, connectedDeviceIds: Set<String>): CaptureDestination = when {
    !capture.file.exists() -> CaptureDestination.Missing
    capture.info.deviceId in connectedDeviceIds -> CaptureDestination.Panel
    else -> CaptureDestination.Folder
}

/** Shows [capture] where [destinationOf] says; for the panel, [showPanel] brings it up. */
private fun openCapture(capture: Capture, mirror: DeviceMirror, showPanel: () -> Unit) {
    when (destinationOf(capture, mirror.devices.map(MirrorDevice::id).toSet())) {
        CaptureDestination.Panel -> {
            mirror.select(capture.info.deviceId)
            showPanel()
            mirror.captures.select(capture)
        }

        CaptureDestination.Folder -> {
            mirror.captures.reveal(capture)
            mirror.notices.show(MirrorNotice.info("${capture.info.deviceName} is not connected, so ${capture.file.name} is shown in its folder instead"))
        }

        CaptureDestination.Missing -> mirror.notices.show(MirrorNotice.failure("${capture.file.name} is no longer in the captures folder", retry = null))
    }
}

@Composable
private fun SingleMirrorRoot(
    mirror: DeviceMirror,
    thumbnails: DeviceThumbnails,
    notices: MirrorNoticeActions,
    showCaptures: Boolean,
    onToggleCaptures: () -> Unit,
    onShowGrid: () -> Unit,
) {
    val device = mirror.selectedDevice
    LaunchedEffect(device?.id) { device?.let { mirror.mirror(it) } }
    val captures = mirror.captures
    LaunchedEffect(captures.library, device?.listing) { captures.showDevice(device?.listing) }
    MirrorScreen(
        devices = mirror.devices.map(MirrorDevice::listing),
        capabilities = device?.controller?.capabilities,
        missingTools = mirror.missingTools,
        selectedId = mirror.selectedId,
        state = mirror.state,
        notices = notices,
        screenPower = mirror.screenPower,
        recordingSinceMillis = mirror.recordingStartedAtMillis.takeIf { mirror.recordingDeviceId == mirror.selectedId },
        // One recording runs at a time, and Record stops it wherever it runs.
        recordingElsewhere = mirror.devices.firstOrNull { it.id == mirror.recordingDeviceId && it.id != mirror.selectedId }?.listing?.name,
        surface = mirror.surface,
        actions = mirror,
        showCaptures = showCaptures,
        livenessOf = { id -> livenessOf(id, mirror, thumbnails, streaming = true) },
        onToggleCaptures = onToggleCaptures,
        onShowGrid = onShowGrid,
        capturesPanel = {
            CapturesPanel(
                captures = captures.captures,
                allDevices = captures.allDevices,
                kind = captures.kind,
                day = captures.day,
                selected = captures.selected,
                thumbnails = captures,
                actions = captures,
            )
        },
    )
}

/**
 * While the selected device [streaming], its liveness comes from its stream; every other device's,
 * and every device's while the grid is shown, from the grid's last look at it, which is nothing
 * until the grid has been shown.
 */
private fun livenessOf(deviceId: String, mirror: DeviceMirror, thumbnails: DeviceThumbnails, streaming: Boolean): DeviceLiveness {
    if (!streaming || deviceId != mirror.selectedId) {
        return when (thumbnails.thumbnailOf(deviceId).state) {
            is ThumbnailState.Live -> DeviceLiveness.Live
            is ThumbnailState.ScreenOff -> DeviceLiveness.ScreenOff
            is ThumbnailState.Failed -> DeviceLiveness.Unavailable
            is ThumbnailState.Loading -> DeviceLiveness.Unknown
        }
    }
    return when (mirror.state) {
        is MirrorState.Streaming, is MirrorState.Polling -> if (mirror.screenPower?.awake == false) DeviceLiveness.ScreenOff else DeviceLiveness.Live
        is MirrorState.Failed, is MirrorState.NoFrames -> DeviceLiveness.Unavailable
        is MirrorState.Idle, is MirrorState.Connecting -> DeviceLiveness.Unknown
    }
}

@Composable
internal fun MirrorScreen(
    devices: List<DeviceListing>,
    capabilities: DeviceCapabilities?,
    missingTools: List<String>,
    selectedId: String?,
    state: MirrorState,
    notices: MirrorNoticeActions,
    screenPower: ScreenPower?,
    recordingSinceMillis: Long?,
    recordingElsewhere: String?,
    surface: MirrorSurface,
    actions: MirrorActions,
    showCaptures: Boolean,
    livenessOf: (String) -> DeviceLiveness,
    onToggleCaptures: () -> Unit,
    onShowGrid: () -> Unit,
    capturesPanel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        missingTools.forEach { JwBanner(text = it, tone = JwTone.Warning) }
        val device = devices.firstOrNull { it.id == selectedId }
        if (device == null || capabilities == null) {
            JwEmptyState(
                title = "No device",
                description = "Start an Android emulator, boot an iOS simulator, or connect a device by USB. It appears here within a few seconds.",
            )
        } else {
            // While switching, the screen state still describes the previous device.
            val ownScreenPower = screenPower.takeIf { surface.deviceId == device.id }
            val pane = DevicePaneState(devices, device, capabilities, state, ownScreenPower, recordingSinceMillis, recordingElsewhere)
            DevicePane(pane, surface, actions, notices, showCaptures, livenessOf, onToggleCaptures, onShowGrid, capturesPanel)
        }
    }
}

/**
 * What the device pane shows about the selected device.
 *
 * @property recordingSinceMillis when the selected device's recording started; null while it is not recording.
 */
private class DevicePaneState(
    val devices: List<DeviceListing>,
    val device: DeviceListing,
    val capabilities: DeviceCapabilities,
    val state: MirrorState,
    val screenPower: ScreenPower?,
    val recordingSinceMillis: Long?,
    val recordingElsewhere: String?,
)

@Composable
private fun DevicePane(
    pane: DevicePaneState,
    surface: MirrorSurface,
    actions: MirrorActions,
    notices: MirrorNoticeActions,
    showCaptures: Boolean,
    livenessOf: (String) -> DeviceLiveness,
    onToggleCaptures: () -> Unit,
    onShowGrid: () -> Unit,
    capturesPanel: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        DeviceToolbar(pane, actions, showCaptures, livenessOf, onToggleCaptures, onShowGrid)
        if (showCaptures) {
            JwSplitPane(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = rememberJwSplitPaneState(VIDEO_FRACTION),
                first = { LiveView(pane, surface, actions, notices) },
                second = capturesPanel,
            )
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) { LiveView(pane, surface, actions, notices) }
        }
    }
}

@Composable
private fun LiveView(pane: DevicePaneState, surface: MirrorSurface, actions: MirrorActions, notices: MirrorNoticeActions) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MirrorVideo(surface = surface, deviceId = pane.device.id, interactive = pane.capabilities.input, onTap = actions::tap, onSwipe = actions::swipe, modifier = Modifier.fillMaxSize())
            val switching = surface.deviceId != pane.device.id
            when {
                // A frame kept from the last visit stays up, dimmed, until the new stream sends one.
                surface.showingKeptFrame || (switching && surface.hasKeptFrame(pane.device.id)) -> ReconnectingScrim()

                switching -> JwEmptyState(title = "Connecting to ${pane.device.name}…")

                else -> StateOverlay(pane.device, pane.state)
            }
            // A screen that is off streams nothing, so the mirror would otherwise just stay black.
            if (pane.screenPower?.awake == false) ScreenOffOverlay(onWake = actions::wake)
            // Over the video rather than above it, so a notice never moves the picture.
            MirrorNoticeHost(notices, Modifier.align(Alignment.BottomCenter).padding(JwSpacing.large))
        }
        // Screenshots move a few times a second; say so, or the mirror just looks broken.
        (pane.state as? MirrorState.Polling)?.let { JwBanner(text = "Showing screenshots, since live video is unavailable: ${it.reason}", tone = JwTone.Warning) }
        if (pane.capabilities.input) TextInput(onSend = actions::inputText)
        MirrorStatsLine(surface = surface, state = pane.state)
    }
}

/**
 * The device picker and the device's controls, in groups that wrap to another line as a whole when the window is
 * too narrow for one: every control stays visible, and the capture group keeps to the line's end.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceToolbar(
    pane: DevicePaneState,
    actions: MirrorActions,
    showCaptures: Boolean,
    livenessOf: (String) -> DeviceLiveness,
    onToggleCaptures: () -> Unit,
    onShowGrid: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(JwTheme.colors.toolbarBackground)
                .padding(horizontal = JwSpacing.medium, vertical = JwSpacing.extraSmall),
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.large),
            verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            DevicePicker(pane.devices, pane.device, livenessOf, onSelect = actions::select, onShowAll = onShowGrid, modifier = Modifier.widthIn(max = PICKER_MAX_WIDTH))
            ButtonGroup(pane.capabilities.buttons.filter(NAVIGATION_BUTTONS::contains), actions)
            VolumeGroup(pane.device.kind, pane.capabilities, actions)
            ScreenPowerButton(pane.screenPower, actions)
            // Pushed to the line's end, and wrapped to a line of its own only as a whole.
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JwVerticalDivider(Modifier.height(CAPTURE_DIVIDER_HEIGHT).padding(end = JwSpacing.extraSmall))
                CaptureActions(pane.capabilities, pane.recordingSinceMillis, pane.recordingElsewhere, actions)
                JwIconButton(tooltip = if (showCaptures) "Hide captures" else "Captures", onClick = onToggleCaptures, selected = showCaptures) {
                    JwIcon(imageVector = CapturesIcon, contentDescription = null)
                }
            }
        }
        JwHorizontalDivider()
    }
}

private val NAVIGATION_BUTTONS = setOf(DeviceButton.Home, DeviceButton.Back, DeviceButton.Recents, DeviceButton.Power)

@Composable
private fun ButtonGroup(buttons: List<DeviceButton>, actions: MirrorActions) {
    if (buttons.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        buttons.forEach { button ->
            JwIconButton(tooltip = button.label, onClick = { actions.pressButton(button) }) {
                JwIcon(imageVector = button.icon, contentDescription = button.label)
            }
        }
    }
}

@Composable
private fun VolumeGroup(kind: DeviceKind, capabilities: DeviceCapabilities, actions: MirrorActions) {
    val volume = listOf(DeviceButton.VolumeUp, DeviceButton.VolumeDown)
    when {
        volume.any { it in capabilities.buttons } -> ButtonGroup(capabilities.buttons.filter(volume::contains), actions)

        // Shown disabled rather than left out, so their absence is explained rather than puzzling.
        kind == DeviceKind.IosSimulator -> Row(verticalAlignment = Alignment.CenterVertically) {
            volume.forEach { button ->
                JwIconButton(tooltip = "${button.label}: idb cannot press a simulator's volume buttons", onClick = {}, enabled = false) {
                    JwIcon(imageVector = button.icon, contentDescription = button.label)
                }
            }
        }

        else -> Unit
    }
}

// Unlike Power, which toggles, these say which way they go, and Wake also lifts a plain lock screen.
@Composable
private fun ScreenPowerButton(screenPower: ScreenPower?, actions: MirrorActions) {
    when (screenPower?.awake) {
        true -> JwIconButton(tooltip = "Screen off", onClick = actions::sleep) { JwIcon(imageVector = ScreenOffIcon, contentDescription = "Screen off") }
        false -> JwIconButton(tooltip = "Wake", onClick = actions::wake) { JwIcon(imageVector = WakeIcon, contentDescription = "Wake") }
        null -> Unit
    }
}

@Composable
private fun CaptureActions(capabilities: DeviceCapabilities, recordingSinceMillis: Long?, recordingElsewhere: String?, actions: MirrorActions) {
    when {
        recordingElsewhere != null -> JwButton(
            text = "Stop recording on $recordingElsewhere",
            onClick = actions::toggleRecording,
            tone = JwTone.Error,
            leadingIcon = { RecordingDot() },
        )

        recordingSinceMillis != null -> RecordingButton(recordingSinceMillis, onStop = actions::toggleRecording)

        capabilities.recording -> JwIconButton(tooltip = "Record", onClick = actions::toggleRecording) {
            JwIcon(imageVector = RecordIcon, contentDescription = null)
        }

        else -> Unit
    }
    JwIconButton(tooltip = "Screenshot", onClick = actions::saveScreenshot) {
        JwIcon(imageVector = ScreenshotIcon, contentDescription = null)
    }
}

/** A running recording can't be missed: red, counting, and a click away from stopping. */
@Composable
private fun RecordingButton(sinceMillis: Long, onStop: () -> Unit) {
    val elapsed by produceState(recordingElapsed(System.currentTimeMillis() - sinceMillis), sinceMillis) {
        while (true) {
            delay(RECORDING_TICK_MILLIS)
            value = recordingElapsed(System.currentTimeMillis() - sinceMillis)
        }
    }
    JwTooltip(text = "Stop recording") {
        JwButton(
            text = elapsed,
            onClick = onStop,
            tone = JwTone.Error,
            leadingIcon = { RecordingDot() },
            modifier = Modifier.semantics { contentDescription = "Stop recording, $elapsed recorded" },
        )
    }
}

@Composable
private fun RecordingDot() {
    Box(Modifier.size(RECORDING_DOT_SIZE).background(JwTheme.colors.error, CircleShape))
}

private val RECORDING_DOT_SIZE = 8.dp
private val CAPTURE_DIVIDER_HEIGHT = 20.dp
private const val RECORDING_TICK_MILLIS = 1_000L

/** "0:12", or "1:02:03" past an hour. */
internal fun recordingElapsed(millis: Long): String {
    val seconds = (millis / 1_000).coerceAtLeast(0)
    val hours = seconds / 3_600
    val minutes = seconds / 60 % 60
    val rest = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
}

/** What the mirror has to say in place of a picture: connecting, or why nothing arrives. */
@Composable
private fun StateOverlay(device: DeviceListing, state: MirrorState) {
    when (state) {
        is MirrorState.Idle, is MirrorState.Streaming, is MirrorState.Polling -> Unit

        is MirrorState.Connecting -> JwEmptyState(title = "Connecting to ${device.name}…")

        is MirrorState.NoFrames -> JwEmptyState(
            title = "No picture from ${device.name}",
            description = state.hints.joinToString("\n") { "• $it" },
        )

        is MirrorState.Failed -> JwEmptyState(title = "Cannot mirror ${device.name}", description = state.message)
    }
}

@Composable
private fun ReconnectingScrim() {
    Box(Modifier.fillMaxSize().background(JwTheme.colors.panelBackground.copy(alpha = RECONNECTING_SCRIM_ALPHA)), contentAlignment = Alignment.Center) {
        JwTag(text = "Reconnecting…")
    }
}

/** Dim enough to read as not live, light enough to recognize the screen. */
private const val RECONNECTING_SCRIM_ALPHA = 0.5f

@Composable
private fun ScreenOffOverlay(onWake: () -> Unit) {
    Box(Modifier.fillMaxSize().background(JwTheme.colors.panelBackground)) {
        JwEmptyState(
            title = "The device's screen is off",
            description = "Nothing reaches the mirror until it is on again.",
            action = { JwButton(text = "Wake", onClick = onWake, style = JwButtonStyle.Primary) },
        )
    }
}

@Composable
private fun TextInput(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = JwSpacing.large, vertical = JwSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JwTextField(value = text, onValueChange = { text = it }, placeholder = "Text to type on the device", modifier = Modifier.weight(1f))
        JwButton(
            text = "Type",
            style = JwButtonStyle.Secondary,
            enabled = text.isNotEmpty(),
            onClick = {
                onSend(text)
                text = ""
            },
        )
    }
}

/** The mirror's frame rates and per-stage costs, for when it feels slow; hidden until asked for. */
@Composable
private fun MirrorStatsLine(surface: MirrorSurface, state: MirrorState) {
    val source = when (state) {
        is MirrorState.Streaming -> "Live"
        is MirrorState.Polling -> "Screenshots"
        else -> return
    }
    var shown by rememberPersistent("showStats", default = false)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = JwSpacing.large),
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JwText(
            text = if (shown) statsText(source, surface.stats) else "",
            style = JwTheme.textStyles.labelSmall,
            color = JwTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        JwButton(text = if (shown) "Hide stats" else "Stats", onClick = { shown = !shown }, style = JwButtonStyle.Text)
    }
}

private fun statsText(source: String, stats: MirrorStats): String {
    // A still screen sends nothing; that is not a stall.
    if (stats.receivedFps == 0) return "$source · the screen is not changing, so the device sends no frames"
    return "$source · ${stats.receivedFps} fps in, ${stats.displayedFps} shown · longest gap ${"%.0f".format(stats.longestGapMillis)} ms · " +
        "decode ${"%.1f".format(stats.decodeMillis)} ms · copy ${"%.1f".format(stats.copyMillis)} ms · draw ${"%.1f".format(stats.drawMillis)} ms"
}

/**
 * The grid, wired to the mirror: its tiles capture through the mirror's devices, and opening one
 * selects it in the single view.
 */
@Composable
private fun DeviceGridRoot(mirror: DeviceMirror, thumbnails: DeviceThumbnails, notices: MirrorNoticeActions, onShowSingle: () -> Unit) {
    val devices = mirror.devices
    val scope = rememberCoroutineScope()
    DeviceGrid(
        devices = devices.map(MirrorDevice::listing),
        selectedId = mirror.selectedId,
        missingTools = mirror.missingTools,
        notices = notices,
        thumbnailOf = thumbnails::thumbnailOf,
        poll = { deviceId, heightPx -> thumbnails.keepFresh(heightPx) { mirror.devices.firstOrNull { it.id == deviceId } } },
        livenessOf = { id -> livenessOf(id, mirror, thumbnails, streaming = false) },
        onOpen = { deviceId ->
            mirror.select(deviceId)
            onShowSingle()
        },
        onScreenshot = { deviceId ->
            val device = mirror.devices.firstOrNull { it.id == deviceId } ?: return@DeviceGrid
            saveScreenshots(listOf(device), mirror, thumbnails, scope)
        },
        onScreenshotAll = { saveScreenshots(mirror.devices, mirror, thumbnails, scope) },
    )
}

/**
 * Saves one screenshot of each of [devices] into the capture library, and says how that went. The
 * captures share the tiles' limit, so they never add to the processes the grid already runs.
 */
private fun saveScreenshots(devices: List<MirrorDevice>, mirror: DeviceMirror, thumbnails: DeviceThumbnails, scope: CoroutineScope) {
    scope.launch {
        val results = devices.map { device -> thumbnails.withCapturePermit(device.id) { mirror.screenshotResultOf(device) } }
        mirror.notices.show(MirrorNotice.screenshotsSaved(results))
    }
}
