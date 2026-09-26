package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwCodeBlock
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIconButton
import com.kitakkun.jetwhale.host.ui.JwMetrics
import com.kitakkun.jetwhale.host.ui.JwShapes
import com.kitakkun.jetwhale.host.ui.JwSnackbarHost
import com.kitakkun.jetwhale.host.ui.JwSnackbarHostState
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSurface
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwToolbar
import com.kitakkun.jetwhale.host.ui.jwFocusRing
import kotlinx.coroutines.delay

/** Below this a phone's layout stops being readable; tiles scroll instead of shrinking further. */
private val MIN_TILE_SCREEN_HEIGHT = 220.dp

/** Beyond this a tile shows no more than the single view would. */
private val MAX_TILE_SCREEN_HEIGHT = 640.dp

/** Width over height of a phone held upright, for a device whose first screenshot has not arrived. */
private const val PHONE_ASPECT_RATIO = 0.46f

/** A phone's screen corners are rounded by about this share of its width. */
private const val SCREEN_CORNER_FRACTION = 0.1f

private val TILE_PADDING = JwSpacing.small
private val TILE_GAP = JwSpacing.extraLarge

/** The caption's two lines and the space above them; fixed, so every tile lines up. */
private val TILE_CAPTION_HEIGHT = 44.dp

private val TILE_METRICS = TileMetrics(
    gap = TILE_GAP.value,
    tileExtraWidth = (TILE_PADDING * 2).value,
    captionHeight = (TILE_CAPTION_HEIGHT + TILE_PADDING * 2).value,
    minScreenHeight = MIN_TILE_SCREEN_HEIGHT.value,
    maxScreenHeight = MAX_TILE_SCREEN_HEIGHT.value,
)

/**
 * Every device as a tile with a recent screenshot, sized so all of them fit the pane when they can.
 * Tiles are view-only: opening one shows it in the single view, where it can be driven. Only tiles
 * on screen capture, through [poll], which a tile runs for as long as it is shown.
 */
@Composable
internal fun DeviceGrid(
    devices: List<DeviceListing>,
    selectedId: String?,
    missingTools: List<String>,
    snackbarHostState: JwSnackbarHostState,
    thumbnailOf: (String) -> DeviceThumbnail,
    poll: suspend (deviceId: String, heightPx: Int) -> Unit,
    livenessOf: (String) -> DeviceLiveness,
    onOpen: (String) -> Unit,
    onScreenshot: (String) -> Unit,
    onScreenshotAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        JwToolbar(
            navigationIcon = {
                DevicePicker(devices = devices, selected = null, livenessOf = livenessOf, onSelect = onOpen, onShowAll = {}, modifier = Modifier.widthIn(max = PICKER_MAX_WIDTH))
            },
            actions = {
                JwIconButton(tooltip = "Screenshot of every device", onClick = onScreenshotAll, enabled = devices.isNotEmpty()) {
                    JwIcon(imageVector = ScreenshotIcon, contentDescription = null)
                }
            },
        )
        missingTools.forEach { JwBanner(text = it, tone = JwTone.Warning) }
        // Notices float over the tiles instead of pushing them down.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (devices.isEmpty()) {
                NoDevices()
            } else {
                TileGroup(devices, selectedId, thumbnailOf, poll, livenessOf, onOpen, onScreenshot)
            }
            JwSnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(JwSpacing.large))
        }
    }
}

@Composable
private fun TileGroup(
    devices: List<DeviceListing>,
    selectedId: String?,
    thumbnailOf: (String) -> DeviceThumbnail,
    poll: suspend (deviceId: String, heightPx: Int) -> Unit,
    livenessOf: (String) -> DeviceLiveness,
    onOpen: (String) -> Unit,
    onScreenshot: (String) -> Unit,
) {
    val nowMillis by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(FRESHNESS_TICK_MILLIS)
            value = System.currentTimeMillis()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val aspectRatios = devices.map { device -> thumbnailOf(device.id).image?.let { it.width.toFloat() / it.height } ?: PHONE_ASPECT_RATIO }
        val layout = layoutDeviceTiles(aspectRatios, width = (maxWidth - TILE_GAP * 2).value, height = (maxHeight - TILE_GAP * 2).value, metrics = TILE_METRICS)
        val screenHeight = layout.screenHeight.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(TILE_GAP),
            verticalArrangement = Arrangement.spacedBy(TILE_GAP, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            userScrollEnabled = layout.scrolls,
        ) {
            items(layout.rows) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                    row.forEach { index ->
                        val device = devices[index]
                        DeviceTile(
                            device = device,
                            thumbnail = thumbnailOf(device.id),
                            liveness = livenessOf(device.id),
                            selected = device.id == selectedId,
                            screenWidth = screenHeight * aspectRatios[index],
                            screenHeight = screenHeight,
                            nowMillis = nowMillis,
                            poll = poll,
                            onOpen = { onOpen(device.id) },
                            onScreenshot = { onScreenshot(device.id) },
                        )
                    }
                }
            }
        }
    }
}

/** How often "Updated … ago" moves on. */
private const val FRESHNESS_TICK_MILLIS = 1_000L

@Composable
private fun DeviceTile(
    device: DeviceListing,
    thumbnail: DeviceThumbnail,
    liveness: DeviceLiveness,
    selected: Boolean,
    screenWidth: Dp,
    screenHeight: Dp,
    nowMillis: Long,
    poll: suspend (deviceId: String, heightPx: Int) -> Unit,
    onOpen: () -> Unit,
    onScreenshot: () -> Unit,
) {
    val heightPx = with(LocalDensity.current) { screenHeight.roundToPx() }
    val currentPoll by rememberUpdatedState(poll)
    LaunchedEffect(device.id, heightPx) { currentPoll(device.id, heightPx) }
    val interaction = remember(calculation = ::MutableInteractionSource)
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = JwShapes.large
    Column(
        modifier = Modifier
            .width(screenWidth + TILE_PADDING * 2)
            .jwFocusRing(interaction, shape)
            .clip(shape)
            .background(if (hovered) JwTheme.colors.hover else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = "Open ${device.name}", onClick = onOpen)
            .padding(TILE_PADDING),
    ) {
        Box(Modifier.size(screenWidth, screenHeight)) {
            TileScreen(device, thumbnail, selected, cornerRadius = screenWidth * SCREEN_CORNER_FRACTION)
            if (hovered || focused) {
                TileActions(device, Modifier.align(Alignment.TopEnd).padding(JwSpacing.small), onOpen, onScreenshot)
            }
        }
        TileCaption(device, thumbnail, liveness, nowMillis)
    }
}

@Composable
private fun TileScreen(device: DeviceListing, thumbnail: DeviceThumbnail, selected: Boolean, cornerRadius: Dp) {
    val screenShape = RoundedCornerShape(cornerRadius)
    val outline = if (selected) BorderStroke(SELECTED_OUTLINE_WIDTH, JwTheme.colors.accent) else BorderStroke(JwMetrics.borderWidth, JwTheme.colors.border)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(screenShape)
            .background(JwTheme.colors.panelBackground)
            .border(outline, screenShape),
        contentAlignment = Alignment.Center,
    ) {
        thumbnail.image?.let { image ->
            Image(
                bitmap = image,
                contentDescription = "Screen of ${device.name}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().alpha(if (thumbnail.state == ThumbnailState.Live) 1f else STALE_ALPHA),
            )
        }
        when (thumbnail.state) {
            is ThumbnailState.Live -> Unit
            is ThumbnailState.Loading -> if (thumbnail.image == null) JwText(text = "Capturing…", color = JwTheme.colors.textSecondary)
            is ThumbnailState.ScreenOff -> JwTag(text = "Screen off")
            is ThumbnailState.Failed -> JwTag(text = "Unavailable", tone = JwTone.Error)
        }
    }
}

/** The device shown in the single view stands out without shouting. */
private val SELECTED_OUTLINE_WIDTH = 2.dp

/** Dim enough to read as out of date, light enough to recognize the screen. */
private const val STALE_ALPHA = 0.4f

@Composable
private fun TileActions(device: DeviceListing, modifier: Modifier, onOpen: () -> Unit, onScreenshot: () -> Unit) {
    JwSurface(
        color = JwTheme.colors.elevatedBackground,
        shape = JwShapes.medium,
        border = BorderStroke(JwMetrics.borderWidth, JwTheme.colors.border),
        modifier = modifier,
    ) {
        Row(Modifier.padding(JwSpacing.tiny)) {
            JwIconButton(tooltip = "Open ${device.name}", onClick = onOpen) { JwIcon(imageVector = OpenFullIcon, contentDescription = null) }
            JwIconButton(tooltip = "Screenshot of ${device.name}", onClick = onScreenshot) { JwIcon(imageVector = ScreenshotIcon, contentDescription = null) }
        }
    }
}

@Composable
private fun TileCaption(device: DeviceListing, thumbnail: DeviceThumbnail, liveness: DeviceLiveness, nowMillis: Long) {
    Column(Modifier.fillMaxWidth().height(TILE_CAPTION_HEIGHT).padding(top = JwSpacing.small)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
            LivenessDot(liveness)
            // The name is measured first and keeps its room; the kind gives way when space runs out.
            JwText(text = device.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            JwText(
                text = listOfNotNull(device.kind.label, device.osVersion).joinToString(" · "),
                style = JwTheme.textStyles.bodySmall,
                color = JwTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        val failure = thumbnail.state as? ThumbnailState.Failed
        JwText(
            text = failure?.reason ?: freshness(thumbnail, nowMillis),
            style = JwTheme.textStyles.labelSmall,
            color = if (failure != null) JwTheme.colors.error else JwTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = JwSpacing.small + JwSpacing.extraLarge),
        )
    }
}

/** "Updated 3 s ago", or what the tile is waiting for. */
internal fun freshness(thumbnail: DeviceThumbnail, nowMillis: Long): String {
    val updated = thumbnail.updatedAtMillis ?: return "Capturing…"
    val seconds = ((nowMillis - updated) / 1_000).coerceAtLeast(0)
    val age = when {
        seconds < 2 -> "just now"
        seconds < 60 -> "$seconds s ago"
        seconds < 3_600 -> "${seconds / 60} min ago"
        else -> "over an hour ago"
    }
    return if (thumbnail.state == ThumbnailState.ScreenOff) "Screen off · last seen $age" else "Updated $age"
}

@Composable
private fun NoDevices() {
    JwEmptyState(
        title = "No devices",
        description = "Start an Android emulator or boot an iOS simulator, connect an Android device by USB with USB debugging on, " +
            "or pair an iPhone in Xcode (Window › Devices and Simulators). Devices appear here within a few seconds.",
        action = {
            Column(Modifier.widthIn(max = EMPTY_STATE_COMMANDS_WIDTH), verticalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
                JwCodeBlock(text = "emulator -list-avds", copyLabel = "Copy")
                JwCodeBlock(text = "xcrun simctl list devices available", copyLabel = "Copy")
                JwCodeBlock(text = "adb devices", copyLabel = "Copy")
            }
        },
    )
}

private val EMPTY_STATE_COMMANDS_WIDTH = 360.dp
