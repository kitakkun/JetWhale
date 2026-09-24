package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import com.kitakkun.jetwhale.host.sdk.get
import com.kitakkun.jetwhale.host.sdk.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/** How many thumbnails stay decoded; the rest are read again from their cache files when shown. */
private const val THUMBNAIL_CACHE_SIZE = 120

private const val ROOT_KEY = "capturesRoot"

/** What the captures panel can ask for. */
internal interface CapturesActions {
    fun showAllDevices(all: Boolean)

    fun filterKind(kind: CaptureKind?)

    fun filterDay(day: String?)

    fun select(capture: Capture?)

    fun open(capture: Capture)

    fun reveal(capture: Capture)

    fun copyImage(capture: Capture)

    fun copyPath(capture: Capture)

    fun delete(capture: Capture)

    fun openDeviceFolder()

    fun chooseFolder()
}

/**
 * The captures of the mirrored devices: where they are kept, which of them the panel lists, and
 * the small thumbnails it shows. Full-size images are never kept; a thumbnail is decoded from its
 * cache file when first shown and dropped when [THUMBNAIL_CACHE_SIZE] newer ones are in use.
 */
@Stable
internal class MirrorCaptures(
    defaultRoot: File,
    private val storage: JetWhalePluginStorage?,
    private val scope: CoroutineScope,
    private val zone: ZoneId,
) : CapturesActions,
    ThumbnailSource {
    var library: CaptureLibrary by mutableStateOf(CaptureLibrary(defaultRoot, zone))
        private set

    var captures: List<Capture> by mutableStateOf(emptyList())
        private set

    var allDevices: Boolean by mutableStateOf(false)
        private set

    var kind: CaptureKind? by mutableStateOf(null)
        private set

    var day: String? by mutableStateOf(null)
        private set

    var selected: Capture? by mutableStateOf(null)
        private set

    var status: MirrorStatus? by mutableStateOf(null)
        private set

    private var device: DeviceListing? = null

    private val thumbnails = object : LinkedHashMap<File, ImageBitmap>(THUMBNAIL_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<File, ImageBitmap>): Boolean = size > THUMBNAIL_CACHE_SIZE
    }

    /** Reads the folder the user picked last time, if any. */
    suspend fun restoreFolder() {
        val saved = storage?.get<String>(ROOT_KEY) ?: return
        library = CaptureLibrary(File(saved), zone)
    }

    /** Lists the captures of [device], or of every device when [allDevices]. */
    fun showDevice(device: DeviceListing?) {
        this.device = device
        refresh()
    }

    /** Saves a screenshot of [device] and puts it at the top of the list. */
    suspend fun addScreenshot(device: DeviceListing, png: ByteArray): Capture = withContext(Dispatchers.IO) {
        val at = Instant.now()
        val file = library.newFile(device, CaptureKind.Screenshot, at).apply { writeBytes(png) }
        val size = Image.makeFromEncoded(png).use { IntSize(it.width, it.height) }
        library.record(file, captureInfo(device, CaptureKind.Screenshot, size, at, durationMillis = null)).also(::added)
    }

    /** A new, empty file for a recording of [device] that starts now. */
    fun recordingFile(device: DeviceListing): File = library.newFile(device, CaptureKind.Recording, Instant.now())

    /** Completes a recording [file] of [device] that ran from [startedAt] until now. */
    suspend fun addRecording(device: DeviceListing, file: File, startedAt: Instant, size: IntSize?): Capture = withContext(Dispatchers.IO) {
        val duration = Instant.now().toEpochMilli() - startedAt.toEpochMilli()
        library.record(file, captureInfo(device, CaptureKind.Recording, size, startedAt, duration)).also(::added)
    }

    /** The decoded thumbnail of [capture] if it is cached; null means [loadThumbnail] it. */
    override fun cachedThumbnail(capture: Capture): ImageBitmap? = synchronized(thumbnails) { thumbnails[capture.file] }

    override suspend fun loadThumbnail(capture: Capture): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = thumbnailOf(library, capture) ?: return@withContext null
        val bitmap = Image.makeFromEncoded(file.readBytes()).use(Image::toComposeImageBitmap)
        synchronized(thumbnails) { thumbnails[capture.file] = bitmap }
        bitmap
    }

    override fun showAllDevices(all: Boolean) {
        allDevices = all
        refresh()
    }

    override fun filterKind(kind: CaptureKind?) {
        this.kind = kind
        refresh()
    }

    override fun filterDay(day: String?) {
        this.day = day
    }

    override fun select(capture: Capture?) {
        selected = capture
    }

    override fun open(capture: Capture) = desktop { it.open(capture.file) }

    // Finder and Explorer can select the file in its folder; elsewhere the folder opens.
    override fun reveal(capture: Capture) = desktop { desktop ->
        if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) desktop.browseFileDirectory(capture.file) else desktop.open(capture.file.parentFile)
    }

    override fun copyImage(capture: Capture) {
        scope.launch(Dispatchers.IO) {
            val image = ImageIO.read(capture.file) ?: return@launch
            Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageSelection(image), null)
            status = MirrorStatus("Copied ${capture.file.name} to the clipboard", isError = false)
        }
    }

    override fun copyPath(capture: Capture) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(capture.file.absolutePath), null)
        status = MirrorStatus("Copied the path of ${capture.file.name}", isError = false)
    }

    override fun delete(capture: Capture) {
        scope.launch(Dispatchers.IO) {
            library.delete(capture)
            synchronized(thumbnails) { thumbnails.remove(capture.file) }
            captures = captures - capture
            if (selected == capture) selected = null
        }
    }

    override fun openDeviceFolder() {
        val folder = device?.takeUnless { allDevices }?.let(library::deviceFolder) ?: library.root
        folder.mkdirs()
        desktop { it.open(folder) }
    }

    override fun chooseFolder() {
        SwingUtilities.invokeLater {
            val chooser = JFileChooser(library.root).apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@invokeLater
            val folder = chooser.selectedFile ?: return@invokeLater
            library = CaptureLibrary(folder, zone)
            scope.launch { storage?.put(ROOT_KEY, folder.absolutePath) }
            refresh()
        }
    }

    private fun refresh() {
        val deviceId = device?.id?.takeUnless { allDevices }
        scope.launch(Dispatchers.IO) { captures = library.list(deviceId, kind, sinceEpochMillis = null) }
    }

    private fun added(capture: Capture) {
        val shown = (allDevices || capture.info.deviceId == device?.id) && (kind == null || kind == capture.info.kind)
        if (shown) captures = listOf(capture) + captures
    }

    private fun desktop(action: (Desktop) -> Unit) {
        try {
            action(Desktop.getDesktop())
        } catch (e: IOException) {
            status = MirrorStatus("Could not open it: ${e.message}", isError = true)
        } catch (e: UnsupportedOperationException) {
            status = MirrorStatus("This desktop cannot open files from here: ${e.message}", isError = true)
        }
    }
}

private fun captureInfo(device: DeviceListing, kind: CaptureKind, size: IntSize?, at: Instant, durationMillis: Long?) = CaptureInfo(
    deviceId = device.id,
    deviceName = device.name,
    platform = device.kind.platform.label,
    deviceKind = device.kind.label,
    osVersion = device.osVersion,
    kind = kind,
    widthPx = size?.width,
    heightPx = size?.height,
    capturedAtEpochMillis = at.toEpochMilli(),
    durationMillis = durationMillis,
)

/** Offers an image to the clipboard as an image, which AWT has no ready-made class for. */
private class ImageSelection(private val image: java.awt.Image) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        if (flavor != DataFlavor.imageFlavor) throw UnsupportedFlavorException(flavor)
        return image
    }
}
