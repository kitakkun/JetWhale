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
    LaunchedEffect(mirror.captures) { mirror.captures.restoreFolder() }
    var showGrid by rememberPersistent("showGrid", default = false)
    var showCaptures by rememberPersistent("showCaptures", default = false)
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

                is NoticeAction.RetryRecordings -> mirror.retryRecordings(action.deviceIds)
            }
        }

        override fun dismiss() = mirror.notices.dismiss()

        override fun hold(held: Boolean) = mirror.notices.hold(held)
    }
    Box(modifier.fillMaxSize()) {
        if (showGrid) {
            DeviceGridRoot(mirror, thumbnails, notices, onShowSingle = { showGrid = false })
        } else {
            SingleMirrorRoot(mirror, thumbnails, notices, showCaptures, onToggleCaptures = { showCaptures = !showCaptures }, onShowGrid = { showGrid = true })
        }
    }
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
        recordingSinceMillis = mirror.selectedId?.let(mirror.recordingsStartedAtMillis::get),
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
internal fun livenessOf(deviceId: String, mirror: DeviceMirror, thumbnails: DeviceThumbnails, streaming: Boolean): DeviceLiveness {
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
            val ownScreenPower = screenPower.takeIf { surface.deviceId == device.id }
            val pane = DevicePaneState(devices, device, capabilities, state, ownScreenPower, recordingSinceMillis)
            DevicePane(pane, surface, actions, notices, showCaptures, livenessOf, onToggleCaptures, onShowGrid, capturesPanel)
        }
    }
}

/**
 * What the device pane shows about the selected device.
 *
 * @property recordingSinceMillis when the selected device's recording started; null while it is not recording.
 */
internal class DevicePaneState(
    val devices: List<DeviceListing>,
    val device: DeviceListing,
    val capabilities: DeviceCapabilities,
    val state: MirrorState,
    val screenPower: ScreenPower?,
    val recordingSinceMillis: Long?,
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
                surface.showingKeptFrame || (switching && surface.hasKeptFrame(pane.device.id)) -> ReconnectingScrim()
                switching -> JwEmptyState(title = "Connecting to ${pane.device.name}…")
                else -> StateOverlay(pane.device, pane.state)
            }
            if (pane.screenPower?.awake == false) ScreenOffOverlay(onWake = actions::wake)
            MirrorNoticeHost(notices, Modifier.align(Alignment.BottomCenter).padding(JwSpacing.large))
        }
        (pane.state as? MirrorState.Polling)?.let { JwBanner(text = "Showing screenshots, since live video is unavailable: ${it.reason}", tone = JwTone.Warning) }
        if (pane.capabilities.input) TextInput(onSend = actions::inputText)
        MirrorStatsLine(surface = surface, state = pane.state)
    }
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
    val sourceLabel = when (state) {
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
            text = if (shown) statsText(sourceLabel, surface.stats) else "",
            style = JwTheme.textStyles.labelSmall,
            color = JwTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        JwButton(text = if (shown) "Hide stats" else "Stats", onClick = { shown = !shown }, style = JwButtonStyle.Text)
    }
}

private fun statsText(sourceLabel: String, stats: MirrorStats): String {
    if (stats.receivedFps == 0) return "$sourceLabel · the screen is not changing, so the device sends no frames"
    return "$sourceLabel · ${stats.receivedFps} fps in, ${stats.displayedFps} shown · longest gap ${"%.0f".format(stats.longestGapMillis)} ms · " +
        "decode ${"%.1f".format(stats.decodeMillis)} ms · copy ${"%.1f".format(stats.copyMillis)} ms · draw ${"%.1f".format(stats.drawMillis)} ms"
}
