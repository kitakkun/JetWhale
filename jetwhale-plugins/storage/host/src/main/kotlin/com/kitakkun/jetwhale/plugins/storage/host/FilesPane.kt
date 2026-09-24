package com.kitakkun.jetwhale.plugins.storage.host

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwCodeBlock
import com.kitakkun.jetwhale.host.ui.JwDialog
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwKeyValueRow
import com.kitakkun.jetwhale.host.ui.JwSegmentedButtons
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwTreeRow
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.FileRootInfo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.jetbrains.skia.Image as SkiaImage

/** The tree is a column of names; the preview beside it needs the room. */
private const val TREE_FRACTION = 0.35f

/** A hex dump of more than this many bytes is too long to scroll through usefully. */
private const val HEX_PREVIEW_BYTES = 16 * 1024

private val TimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
internal fun FilesPane(
    treeRows: List<FileTreeRow>,
    fileRoots: List<FileRootInfo>,
    selectedRow: FileTreeRow?,
    loadedFile: LoadedFile?,
    actions: StorageInspectorActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        if (fileRoots.isEmpty()) {
            JwEmptyState(
                title = "No file roots",
                description = "The app exposes no directory to browse. The web has no file system; elsewhere, pass FileRoot entries to JetWhaleStorageAgentPlugin.",
            )
            return@Box
        }
        FileTreeSplit(treeRows = treeRows, fileRoots = fileRoots, selectedRow = selectedRow, loadedFile = loadedFile, actions = actions)
    }
}

@Composable
private fun FileTreeSplit(
    treeRows: List<FileTreeRow>,
    fileRoots: List<FileRootInfo>,
    selectedRow: FileTreeRow?,
    loadedFile: LoadedFile?,
    actions: StorageInspectorActions,
) {
    JwSplitPane(
        state = rememberJwSplitPaneState(TREE_FRACTION),
        first = {
            LazyColumn(Modifier.fillMaxSize()) {
                items(treeRows, key = FileTreeRow::location) { row ->
                    JwTreeRow(
                        text = row.location.name,
                        depth = row.depth,
                        expandable = row.isDirectory,
                        expanded = row.expanded,
                        selected = row.location == selectedRow?.location,
                        onClick = { actions.select(row) },
                        onToggleExpanded = { actions.toggleDirectory(row.location) },
                        trailingContent = row.entry?.takeUnless(FileEntry::isDirectory)?.let { entry ->
                            {
                                JwText(
                                    text = formatByteSize(entry.sizeBytes),
                                    style = JwTheme.textStyles.labelSmall,
                                    color = JwTheme.colors.textSecondary,
                                )
                            }
                        },
                    )
                }
            }
        },
        second = {
            if (selectedRow == null) {
                JwEmptyState(title = "Nothing selected", description = "Pick a file to preview it, or a directory to see where it is.")
            } else {
                EntryDetail(
                    row = selectedRow,
                    root = fileRoots.firstOrNull { it.name == selectedRow.location.rootName },
                    loadedFile = loadedFile?.takeIf { it.location == selectedRow.location },
                    onDelete = { actions.delete(selectedRow.location) },
                )
            }
        },
    )
}

@Composable
private fun EntryDetail(
    row: FileTreeRow,
    root: FileRootInfo?,
    loadedFile: LoadedFile?,
    onDelete: () -> Unit,
) {
    var confirmingDelete by remember(row.location) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(JwSpacing.large), verticalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JwText(text = row.location.name, style = JwTheme.textStyles.title, modifier = Modifier.weight(1f))
            // A root is where the app keeps things, not a thing it keeps: there is nothing to delete.
            if (row.location.path.isNotEmpty()) {
                JwButton(text = "Delete…", onClick = { confirmingDelete = true }, tone = JwTone.Error)
            }
        }
        root?.let { JwKeyValueRow(key = "Path", value = (listOf(it.absolutePath.trimEnd('/')) + row.location.path).joinToString("/"), monospace = true, wrap = false) }
        row.entry?.let { entry ->
            if (!entry.isDirectory) JwKeyValueRow(key = "Size", value = "${formatByteSize(entry.sizeBytes)} (${entry.sizeBytes} bytes)")
            entry.lastModifiedEpochMillis?.let { JwKeyValueRow(key = "Modified", value = TimestampFormatter.format(Instant.ofEpochMilli(it))) }
        }
        if (!row.isDirectory && loadedFile != null) FilePreview(loadedFile, Modifier.weight(1f))
    }
    if (confirmingDelete) {
        JwDialog(
            title = "Delete ${row.location.name}?",
            closeLabel = "Cancel",
            onDismissRequest = { confirmingDelete = false },
            confirmButton = {
                JwButton(
                    text = "Delete",
                    style = JwButtonStyle.Primary,
                    tone = JwTone.Error,
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    },
                )
            },
            dismissButton = { JwButton(text = "Cancel", onClick = { confirmingDelete = false }) },
        ) {
            JwText(
                text = if (row.isDirectory) {
                    "The directory and everything in it is removed from the app's storage. This cannot be undone."
                } else {
                    "The file is removed from the app's storage. This cannot be undone."
                },
            )
        }
    }
}

@Composable
private fun FilePreview(file: LoadedFile, modifier: Modifier = Modifier) {
    val formats = remember(file) { previewFormatsOf(file) }
    var format by remember(file) { mutableStateOf(formats.first()) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
        JwSegmentedButtons(options = formats, selected = format, onSelect = { format = it }, label = PreviewFormat::label)
        if (file.isTruncated) {
            JwText(
                text = "Showing the first ${formatByteSize(file.bytes.size.toLong())} of ${formatByteSize(file.totalSizeBytes)}.",
                style = JwTheme.textStyles.labelSmall,
                color = JwTheme.colors.textSecondary,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (format) {
                PreviewFormat.Preferences -> PreferencesPreview(file.bytes)

                PreviewFormat.Image -> ImagePreview(file.bytes)

                PreviewFormat.Text -> ScrollingCode(decodeTextOrNull(file.bytes).orEmpty())

                PreviewFormat.Hex -> {
                    val shown = file.bytes.copyOf(minOf(file.bytes.size, HEX_PREVIEW_BYTES))
                    ScrollingCode(remember(file) { hexDump(shown) })
                }
            }
        }
    }
}

@Composable
private fun PreferencesPreview(bytes: ByteArray) {
    val decoded = remember(bytes) {
        try {
            Result.success(decodePreferencesDataStore(bytes))
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }
    decoded.fold(
        onSuccess = { KeyValueTable(entries = it, onRemove = null) },
        onFailure = { JwEmptyState(title = "Not a Preferences DataStore file", description = it.message) },
    )
}

@Composable
private fun ImagePreview(bytes: ByteArray) {
    val bitmap: ImageBitmap? = remember(bytes) {
        // Skia throws for a format it cannot read, including an image cut short by the preview size.
        try {
            SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        } catch (_: IllegalArgumentException) {
            null
        }
    }
    if (bitmap == null) {
        JwEmptyState(title = "Cannot draw this image", description = "The format is not one Skia reads, or the preview cut it short.")
        return
    }
    Column {
        Image(bitmap = bitmap, contentDescription = "Image preview", contentScale = ContentScale.Fit, modifier = Modifier.weight(1f, fill = false))
        JwText(text = "${bitmap.width}×${bitmap.height}", style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary)
    }
}

@Composable
private fun ScrollingCode(text: String) {
    JwCodeBlock(text = text, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
}

private fun formatByteSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
}
