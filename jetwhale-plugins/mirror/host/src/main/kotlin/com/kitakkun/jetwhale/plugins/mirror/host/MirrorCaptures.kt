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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
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

    /** Puts [capture] on the clipboard: a screenshot as its image and its file, a recording as its file. */
    fun copy(capture: Capture)

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
    private val notices: MirrorNotices,
    private val ffmpegPath: Deferred<String?>,
    private val clipboard: CaptureClipboard,
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

    private var device: DeviceListing? = null

    private val captureListGeneration = AtomicLong()

    // One copy at a time, in the order they were asked for, so a slow copy cannot land on the
    // clipboard after a later one.
    private val clipboardRequests = Channel<ClipboardRequest>(Channel.UNLIMITED)

    private val thumbnails = object : LinkedHashMap<File, ImageBitmap>(THUMBNAIL_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<File, ImageBitmap>): Boolean = size > THUMBNAIL_CACHE_SIZE
    }

    init {
        scope.launch(Dispatchers.IO) {
            for ((capture, pathOnly) in clipboardRequests) {
                val what = if (pathOnly) "the path of ${capture.file.name}" else capture.file.name
                try {
                    if (pathOnly) clipboard.putText(capture.file.absolutePath) else clipboard.putCapture(capture)
                    notices.show(MirrorNotice.info("Copied $what"))
                } catch (e: IOException) {
                    notices.show(MirrorNotice.failure("Could not copy $what: ${e.message}", retry = null))
                }
            }
        }
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
        library.record(file, captureInfo(device, CaptureKind.Screenshot, size, at, durationMillis = null)).also(::prependToCaptureList)
    }

    /** A new, empty file for a recording of [device] that starts now. */
    fun recordingFile(device: DeviceListing): File = library.newFile(device, CaptureKind.Recording, Instant.now())

    /**
     * Completes a recording [file] of [device] that ran from [startedAt] until now. Its length is the
     * video's own, falling back to the time since [startedAt] when the file does not say.
     */
    suspend fun addRecording(device: DeviceListing, file: File, startedAt: Instant, size: IntSize?): Capture = withContext(Dispatchers.IO) {
        val duration = mp4DurationMillis(file) ?: (Instant.now().toEpochMilli() - startedAt.toEpochMilli())
        library.record(file, captureInfo(device, CaptureKind.Recording, size, startedAt, duration)).also(::prependToCaptureList)
    }

    /** The decoded thumbnail of [capture] if it is cached; null means [loadThumbnail] it. */
    override fun cachedThumbnail(capture: Capture): ImageBitmap? = synchronized(thumbnails) { thumbnails[capture.file] }

    override suspend fun loadThumbnail(capture: Capture): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = thumbnailOf(library, capture, ffmpegPath.await()) ?: return@withContext null
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
        // No refresh: the panel narrows the list to the day itself, and needs every day's captures
        // to offer the other dates.
        this.day = day
    }

    override fun select(capture: Capture?) {
        selected = capture
    }

    override fun open(capture: Capture) = desktop { it.open(capture.file) }

    override fun reveal(capture: Capture) = desktop { desktop ->
        if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) desktop.browseFileDirectory(capture.file) else desktop.open(capture.file.parentFile)
    }

    override fun copy(capture: Capture) {
        clipboardRequests.trySend(ClipboardRequest(capture, pathOnly = false))
    }

    override fun copyPath(capture: Capture) {
        clipboardRequests.trySend(ClipboardRequest(capture, pathOnly = true))
    }

    override fun delete(capture: Capture) {
        scope.launch(Dispatchers.IO) {
            library.delete(capture)
            synchronized(thumbnails) { thumbnails.remove(capture.file) }
            refresh()
            synchronized(captureListGeneration) { captures = captures - capture }
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
        val listing = captureListGeneration.incrementAndGet()
        scope.launch(Dispatchers.IO) {
            val listed = library.list(deviceId, kind, sinceEpochMillis = null)
            synchronized(captureListGeneration) { if (captureListGeneration.get() == listing) captures = listed }
        }
    }

    private fun prependToCaptureList(capture: Capture) {
        refresh()
        val shown = (allDevices || capture.info.deviceId == device?.id) && (kind == null || kind == capture.info.kind)
        if (shown) synchronized(captureListGeneration) { captures = listOf(capture) + captures }
    }

    private fun desktop(action: (Desktop) -> Unit) {
        try {
            action(Desktop.getDesktop())
        } catch (e: IOException) {
            notices.show(MirrorNotice.failure("Could not open it: ${e.message}", retry = null))
        } catch (e: UnsupportedOperationException) {
            notices.show(MirrorNotice.failure("This desktop cannot open files from here: ${e.message}", retry = null))
        }
    }

    private data class ClipboardRequest(val capture: Capture, val pathOnly: Boolean)
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
