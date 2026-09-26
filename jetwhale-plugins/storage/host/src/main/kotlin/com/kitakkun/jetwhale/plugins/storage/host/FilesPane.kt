package com.kitakkun.jetwhale.plugins.storage.host

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwCodeBlock
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
import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryMeasurement
import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.FileRootInfo
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities
import org.jetbrains.skia.Image as SkiaImage

/** The tree is a column of names; the preview beside it needs the room. */
private const val TREE_FRACTION = 0.35f

/** A hex dump of more than this many bytes is too long to scroll through usefully. */
private const val HEX_PREVIEW_BYTES = 16 * 1024

/** Facts whose value is a path or a digest: set in code, on one line that scrolls sideways. */
private val MONOSPACE_FACTS = setOf("Path", "Link", "SHA-256")

@Composable
internal fun FilesPane(
    treeRows: List<FileTreeRow>,
    fileRoots: List<FileRootInfo>,
    selectedRow: FileTreeRow?,
    loadedFile: LoadedFile?,
    directoryMeasurement: DirectoryMeasurement?,
    fileSha256: String?,
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
        FileTreeSplit(
            treeRows = treeRows,
            fileRoots = fileRoots,
            selectedRow = selectedRow,
            loadedFile = loadedFile,
            directoryMeasurement = directoryMeasurement,
            fileSha256 = fileSha256,
            actions = actions,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FileTreeSplit(
    treeRows: List<FileTreeRow>,
    fileRoots: List<FileRootInfo>,
    selectedRow: FileTreeRow?,
    loadedFile: LoadedFile?,
    directoryMeasurement: DirectoryMeasurement?,
    fileSha256: String?,
    actions: StorageInspectorActions,
) {
    JwSplitPane(
        state = rememberJwSplitPaneState(TREE_FRACTION),
        first = {
            LazyColumn(Modifier.fillMaxSize()) {
                items(treeRows, key = FileTreeRow::location) { row ->
                    // JwTreeRow reports a click without the keys held for it, so Alt is read from
                    // the press that starts the click: Alt-click opens or closes the whole subtree.
                    var altPressed by remember { mutableStateOf(false) }
                    JwTreeRow(
                        text = row.location.name,
                        depth = row.depth,
                        expandable = row.isDirectory,
                        expanded = row.expanded,
                        selected = row.location == selectedRow?.location,
                        onClick = { if (altPressed && row.isDirectory) actions.toggleSubtree(row.location) else actions.select(row) },
                        onToggleExpanded = { if (altPressed) actions.toggleSubtree(row.location) else actions.toggleDirectory(row.location) },
                        modifier = Modifier.onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) {
                            altPressed = it.keyboardModifiers.isAltPressed
                        },
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
                    facts = EntryFacts(
                        row = selectedRow,
                        root = fileRoots.firstOrNull { it.name == selectedRow.location.rootName },
                        loadedFile = loadedFile?.takeIf { it.location == selectedRow.location },
                        directoryMeasurement = directoryMeasurement,
                        fileSha256 = fileSha256,
                    ),
                    actions = actions,
                )
            }
        },
    )
}

@Composable
private fun EntryDetail(
    row: FileTreeRow,
    facts: EntryFacts,
    actions: StorageInspectorActions,
) {
    var confirmingDelete by remember(row.location) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(JwSpacing.large), verticalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
            JwText(text = row.location.name, style = JwTheme.textStyles.title, modifier = Modifier.weight(1f))
            if (row.isDirectory) {
                JwButton(text = "Calculate size", onClick = { actions.measureDirectory(row.location) })
                // A ZIP never follows a link, so a linked directory has nothing of its own to archive.
                if (row.entry?.isSymbolicLink != true) {
                    JwButton(text = "Download as ZIP…", onClick = { chooseSaveTarget("${row.location.name}.zip") { actions.requestZipDownload(row.location, it) } })
                }
                JwButton(text = "Upload…", onClick = { chooseUploadSource("Upload into ${row.location.name}") { actions.requestUpload(row.location.child(it.name), it) } })
            } else {
                JwButton(text = "Compute SHA-256", onClick = { actions.computeSha256(row.location) })
                JwButton(text = "Save…", onClick = { chooseSaveTarget(row.location.name) { actions.saveFile(row.location, it) } })
                JwButton(text = "Replace…", onClick = { chooseUploadSource("Replace ${row.location.name}") { actions.requestUpload(row.location, it) } })
            }
            // A root is where the app keeps things, not a thing it keeps: there is nothing to delete.
            if (row.location.path.isNotEmpty()) {
                JwButton(text = "Delete…", onClick = { confirmingDelete = true }, tone = JwTone.Error)
            }
        }
        facts.rows.forEach { (key, value) -> JwKeyValueRow(key = key, value = value, monospace = key in MONOSPACE_FACTS, wrap = key !in MONOSPACE_FACTS) }
        facts.loadedFile?.takeUnless { row.isDirectory }?.let { FilePreview(it, Modifier.weight(1f)) }
    }
    if (confirmingDelete) {
        ConfirmDialog(
            title = "Delete ${row.location.name}?",
            message = if (row.isDirectory) {
                "The directory and everything in it is removed from the app's storage. This cannot be undone."
            } else {
                "The file is removed from the app's storage. This cannot be undone."
            },
            confirmLabel = "Delete",
            confirmTone = JwTone.Error,
            onConfirm = {
                confirmingDelete = false
                actions.delete(row.location)
            },
            onDismiss = { confirmingDelete = false },
        )
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
        onSuccess = { KeyValueTable(entries = it, selectedKey = null, onSelect = null) },
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
    var scale by remember(bytes) { mutableStateOf(ImageScale.Fit) }
    Column(verticalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
            JwSegmentedButtons(options = ImageScale.entries, selected = scale, onSelect = { scale = it }, label = ImageScale::label)
            JwText(text = "${bitmap.width}×${bitmap.height}", style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary)
        }
        when (scale) {
            // Nearest-neighbour keeps a small icon's pixels sharp when Fit enlarges it; a photo shrunk
            // to fit still reads well without smoothing.
            ImageScale.Fit -> Image(
                bitmap = bitmap,
                contentDescription = "Image preview",
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize(),
            )

            ImageScale.ActualSize -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                Image(bitmap = bitmap, contentDescription = "Image preview")
            }
        }
    }
}

@Composable
private fun ScrollingCode(text: String) {
    JwCodeBlock(text = text, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
}

private enum class ImageScale(val label: String) {
    Fit("Fit"),

    /** One image pixel per screen pixel. */
    ActualSize("Actual size"),
}

/**
 * Asks where to save a file named [suggestedName] and hands the choice to [onChosen]; a cancelled
 * dialog calls nothing. The dialog runs on the AWT event thread, which the plugin's Compose scene
 * is not guaranteed to be on.
 */
private fun chooseSaveTarget(suggestedName: String, onChosen: (File) -> Unit) {
    SwingUtilities.invokeLater {
        val dialog = FileDialog(null as Frame?, "Save $suggestedName", FileDialog.SAVE)
        dialog.file = suggestedName
        dialog.isVisible = true
        val directory = dialog.directory ?: return@invokeLater
        val fileName = dialog.file ?: return@invokeLater
        onChosen(File(directory, fileName))
    }
}

/** Asks which local file to send to the app and hands it to [onChosen]; a cancelled dialog calls nothing. */
private fun chooseUploadSource(title: String, onChosen: (File) -> Unit) {
    SwingUtilities.invokeLater {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.isVisible = true
        val directory = dialog.directory ?: return@invokeLater
        val fileName = dialog.file ?: return@invokeLater
        onChosen(File(directory, fileName))
    }
}
