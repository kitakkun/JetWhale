package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock

/**
 * What a grid tile shows of one device: its last screenshot, when that was taken
 * ([updatedAtMillis], null before the first), and what stood in the way of a newer one.
 */
internal data class DeviceThumbnail(
    val image: ImageBitmap?,
    val updatedAtMillis: Long?,
    val state: ThumbnailState,
)

internal sealed interface ThumbnailState {
    /** No screenshot has arrived yet. */
    data object Loading : ThumbnailState

    /** The image is at most one refresh old. */
    data object Live : ThumbnailState

    /** The screen is off, so the image is the last one taken while it was on. */
    data object ScreenOff : ThumbnailState

    /** The last attempt failed; the image, if any, is older. */
    data class Failed(val reason: String) : ThumbnailState
}

/**
 * Screenshots of every device, shrunk for the grid, refreshed while their tiles are on screen.
 *
 * A tile runs [keepFresh] for as long as it is composed, so only visible devices are captured and
 * leaving the grid stops all of it. Captures go through the device's ordinary screenshot path, never
 * its video stream, so the grid does not compete with the single-device mirror for it. At most
 * [maxConcurrentCaptures] run at once across all devices, and each device has at most one.
 *
 * A tile may still be drawing the previous image when a new one arrives, so each device keeps its
 * current and previous image, and an image is closed only once two newer ones have replaced it.
 */
@Stable
internal class DeviceThumbnails(
    private val refreshIntervalMillis: Long,
    maxConcurrentCaptures: Int,
    private val decodeDispatcher: CoroutineDispatcher,
    private val clock: Clock,
) : AutoCloseable {
    private val permits = Semaphore(maxConcurrentCaptures)

    // One capture per device at a time, whoever asks: a tile and Screenshot all never overlap on it.
    // Kept for as long as the grid is: a removed lock that a capture still held would let the next
    // capture of a rediscovered device take a fresh one and overlap it, and a lock per device ever
    // seen costs next to nothing.
    private val deviceLocks = ConcurrentHashMap<String, Mutex>()
    private val lock = Any()
    private val previous = HashMap<String, ImageBitmap>()
    private var closed = false

    // The devices still listed; a capture that finishes for one removed meanwhile is dropped.
    private var retained: Set<String>? = null

    private val thumbnails = mutableStateMapOf<String, DeviceThumbnail>()

    fun thumbnailOf(deviceId: String): DeviceThumbnail = thumbnails[deviceId] ?: DeviceThumbnail(image = null, updatedAtMillis = null, state = ThumbnailState.Loading)

    /**
     * Captures [device] every [refreshIntervalMillis] until cancelled or until [device] returns
     * null, shrinking each screenshot to [heightPx]. [device] is read before each capture, so a
     * device the list refreshed is followed and one that disappeared is left alone.
     */
    suspend fun keepFresh(heightPx: Int, device: () -> MirrorDevice?) {
        while (coroutineContext.isActive) {
            val current = device() ?: return
            withCapturePermit(current.id) { refresh(current, heightPx) }
            delay(refreshIntervalMillis)
        }
    }

    /**
     * Runs [capture] of [deviceId] under the thumbnails' limits: at most one capture of the device
     * at a time, and no more captures across devices than the global limit. Any other screenshot
     * taken while the grid is shown goes through here too.
     */
    suspend fun <T> withCapturePermit(deviceId: String, capture: suspend () -> T): T = deviceLocks.getOrPut(deviceId, ::Mutex).withLock { permits.withPermit { capture() } }

    /** Drops the images of devices that are gone. */
    fun retainOnly(deviceIds: Set<String>) {
        val gone = synchronized(lock) {
            retained = deviceIds
            val ids = thumbnails.keys - deviceIds
            ids.flatMap { id -> listOfNotNull(thumbnails.remove(id)?.image, previous.remove(id)) }
        }
        gone.forEach(::closeImage)
    }

    override fun close() {
        val all = synchronized(lock) {
            closed = true
            val images = thumbnails.values.mapNotNull(DeviceThumbnail::image) + previous.values
            thumbnails.clear()
            previous.clear()
            images
        }
        all.forEach(::closeImage)
    }

    private suspend fun refresh(device: MirrorDevice, heightPx: Int) {
        val state = try {
            // A screenshot of a sleeping device is black; a locked one still shows its lock screen.
            if (device.controller.capabilities.screenPower && !device.controller.screenPower().awake) {
                ThumbnailState.ScreenOff
            } else {
                val png = device.controller.captureScreenshot()
                val image = shrink(png, heightPx)
                if (image == null) {
                    ThumbnailState.Failed("the screenshot could not be read as an image")
                } else {
                    publish(device.id, image)
                    ThumbnailState.Live
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: DeviceControlException) {
            ThumbnailState.Failed(e.message ?: "the screenshot failed")
        }
        synchronized(lock) {
            if (!accepts(device.id)) return
            val shown = thumbnails[device.id]
            thumbnails[device.id] = DeviceThumbnail(image = shown?.image, updatedAtMillis = shown?.updatedAtMillis, state = state)
        }
    }

    private fun publish(deviceId: String, image: ImageBitmap) {
        val dropped = synchronized(lock) {
            if (!accepts(deviceId)) return closeImage(image)
            val shown = thumbnails[deviceId]?.image
            thumbnails[deviceId] = DeviceThumbnail(image = image, updatedAtMillis = clock.now().toEpochMilliseconds(), state = ThumbnailState.Live)
            if (shown != null) previous.put(deviceId, shown) else null
        }
        dropped?.let(::closeImage)
    }

    // Called with [lock] held.
    private fun accepts(deviceId: String): Boolean = !closed && retained?.contains(deviceId) != false

    private suspend fun shrink(png: ByteArray, heightPx: Int): ImageBitmap? = withContext(decodeDispatcher) {
        val decoded = try {
            Image.makeFromEncoded(png)
        } catch (_: IllegalArgumentException) {
            return@withContext null
        }
        decoded.use { image ->
            val height = heightPx.coerceIn(1, image.height)
            val width = maxOf(1, image.width * height / image.height)
            Surface.makeRasterN32Premul(width, height).use { surface ->
                surface.canvas.drawImageRect(
                    image,
                    Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                    Rect.makeWH(width.toFloat(), height.toFloat()),
                    FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
                    null,
                    true,
                )
                surface.makeImageSnapshot().use(Image::toComposeImageBitmap)
            }
        }
    }
}

// Skia frees a bitmap's pixels only when it is closed or after a garbage collection, which a
// heap of small wrappers rarely prompts; closing keeps a long-open grid from growing native memory.
private fun closeImage(image: ImageBitmap) {
    image.asSkiaBitmap().close()
}
