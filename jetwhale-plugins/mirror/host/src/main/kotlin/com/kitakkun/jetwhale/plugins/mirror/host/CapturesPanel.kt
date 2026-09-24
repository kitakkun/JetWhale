package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwDialog
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwKeyValueRow
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSegmentedButtons
import com.kitakkun.jetwhale.host.ui.JwShapes
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwTagStyle
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Narrow enough for three thumbnails side by side in a third of the window. */
private val CellWidth = 112.dp

private val DetailPreviewHeight = 160.dp

private val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())
private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

/** Where the panel gets thumbnails: decoded ones at once, the others once loaded off the UI thread. */
internal interface ThumbnailSource {
    fun cachedThumbnail(capture: Capture): ImageBitmap?

    suspend fun loadThumbnail(capture: Capture): ImageBitmap?
}

@Composable
internal fun CapturesPanel(
    captures: List<Capture>,
    allDevices: Boolean,
    kind: CaptureKind?,
    day: String?,
    selected: Capture?,
    status: MirrorStatus?,
    thumbnails: ThumbnailSource,
    actions: CapturesActions,
    modifier: Modifier = Modifier,
) {
    val days = captures.map { DayFormat.format(Instant.ofEpochMilli(it.info.capturedAtEpochMillis)) }.distinct()
    val shown = captures.filter { day == null || DayFormat.format(Instant.ofEpochMilli(it.info.capturedAtEpochMillis)) == day }
    Column(modifier.fillMaxSize()) {
        CaptureFilters(allDevices, kind, day, days, actions)
        status?.let { JwBanner(text = it.message, tone = if (it.isError) JwTone.Error else JwTone.Neutral) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (shown.isEmpty()) {
                JwEmptyState(title = "No captures", description = "Screenshots and recordings of this device appear here as soon as they are taken.")
            } else {
                CaptureGrid(shown, selected, thumbnails, onSelect = actions::select)
            }
        }
        selected?.let { CaptureDetail(it, thumbnails, actions) }
    }
}

@Composable
private fun CaptureFilters(allDevices: Boolean, kind: CaptureKind?, day: String?, days: List<String>, actions: CapturesActions) {
    Column(Modifier.padding(JwSpacing.medium), verticalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
        JwSegmentedButtons(options = listOf(false, true), selected = allDevices, onSelect = actions::showAllDevices, label = { if (it) "All devices" else "This device" })
        JwSegmentedButtons(options = listOf(null) + CaptureKind.entries, selected = kind, onSelect = actions::filterKind, label = { it?.label ?: "All kinds" })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall)) {
            (listOf(null) + days).forEach { option ->
                JwTag(
                    text = option ?: "All dates",
                    style = if (option == day) JwTagStyle.Filled else JwTagStyle.Outlined,
                    tone = if (option == day) JwTone.Accent else JwTone.Neutral,
                    onClick = { actions.filterDay(option) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
            JwButton(text = "Open folder", onClick = actions::openDeviceFolder)
            JwButton(text = "Change folder…", onClick = actions::chooseFolder, style = JwButtonStyle.Text)
        }
    }
}

@Composable
private fun CaptureGrid(captures: List<Capture>, selected: Capture?, thumbnails: ThumbnailSource, onSelect: (Capture) -> Unit) {
    val byDay = captures.groupBy { DayFormat.format(Instant.ofEpochMilli(it.info.capturedAtEpochMillis)) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CellWidth),
        modifier = Modifier.fillMaxSize().padding(horizontal = JwSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.small),
    ) {
        byDay.forEach { (day, onDay) ->
            item(key = day, span = { GridItemSpan(maxLineSpan) }) { JwSectionHeader(title = day, count = onDay.size) }
            items(onDay, key = { it.file.path }) { capture ->
                CaptureCell(capture, selected = capture == selected, thumbnails = thumbnails, onClick = { onSelect(capture) })
            }
        }
    }
}

@Composable
private fun CaptureCell(capture: Capture, selected: Boolean, thumbnails: ThumbnailSource, onClick: () -> Unit) {
    val border = if (selected) JwTheme.colors.accent else JwTheme.colors.border
    Column(
        modifier = Modifier.clickable(onClick = onClick).border(1.dp, border, JwShapes.small).padding(JwSpacing.extraSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Thumbnail(capture, thumbnails, Modifier.fillMaxWidth().aspectRatio(THUMBNAIL_ASPECT))
        JwText(
            text = "${TimeFormat.format(Instant.ofEpochMilli(capture.info.capturedAtEpochMillis))} · ${capture.info.kind.label}",
            style = JwTheme.textStyles.labelSmall,
            color = JwTheme.colors.textSecondary,
        )
    }
}

/** A portrait phone screen, the common case; a landscape one is letterboxed inside it. */
private const val THUMBNAIL_ASPECT = 0.5f

@Composable
private fun Thumbnail(capture: Capture, thumbnails: ThumbnailSource, modifier: Modifier) {
    var image by remember(capture.file) { mutableStateOf(thumbnails.cachedThumbnail(capture)) }
    LaunchedEffect(capture.file) {
        if (image == null) image = thumbnails.loadThumbnail(capture)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        image?.let { Image(bitmap = it, contentDescription = capture.file.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            ?: JwText(text = capture.info.kind.label, style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary)
    }
}

@Composable
private fun CaptureDetail(capture: Capture, thumbnails: ThumbnailSource, actions: CapturesActions) {
    var confirmingDelete by remember(capture) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(JwSpacing.medium), verticalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
        Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
            Thumbnail(capture, thumbnails, Modifier.height(DetailPreviewHeight).aspectRatio(THUMBNAIL_ASPECT))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall)) { CaptureFacts(capture) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall), verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall)) {
            JwButton(text = "Open", onClick = { actions.open(capture) })
            JwButton(text = "Reveal", onClick = { actions.reveal(capture) }, style = JwButtonStyle.Text)
            if (capture.info.kind == CaptureKind.Screenshot) JwButton(text = "Copy image", onClick = { actions.copyImage(capture) }, style = JwButtonStyle.Text)
            JwButton(text = "Copy path", onClick = { actions.copyPath(capture) }, style = JwButtonStyle.Text)
            JwButton(text = "Delete…", onClick = { confirmingDelete = true }, style = JwButtonStyle.Text, tone = JwTone.Error)
        }
    }
    if (confirmingDelete) {
        JwDialog(
            title = "Delete ${capture.file.name}?",
            closeLabel = "Cancel",
            onDismissRequest = { confirmingDelete = false },
            confirmButton = {
                JwButton(
                    text = "Delete",
                    style = JwButtonStyle.Primary,
                    tone = JwTone.Error,
                    onClick = {
                        confirmingDelete = false
                        actions.delete(capture)
                    },
                )
            },
            dismissButton = { JwButton(text = "Cancel", onClick = { confirmingDelete = false }) },
        ) {
            JwText(text = "The file is removed from disk. This cannot be undone.")
        }
    }
}

@Composable
private fun CaptureFacts(capture: Capture) {
    val info = capture.info
    JwKeyValueRow(key = "Device", value = listOfNotNull(info.deviceName, info.deviceKind, info.osVersion).joinToString(" · "))
    JwKeyValueRow(key = "Taken", value = "${DayFormat.format(Instant.ofEpochMilli(info.capturedAtEpochMillis))} ${TimeFormat.format(Instant.ofEpochMilli(info.capturedAtEpochMillis))}")
    if (info.widthPx != null && info.heightPx != null) JwKeyValueRow(key = "Size", value = "${info.widthPx}×${info.heightPx}")
    info.durationMillis?.let { JwKeyValueRow(key = "Length", value = "%.1f s".format(it / 1000.0)) }
    JwKeyValueRow(key = "File", value = capture.file.absolutePath, monospace = true, wrap = false)
}
