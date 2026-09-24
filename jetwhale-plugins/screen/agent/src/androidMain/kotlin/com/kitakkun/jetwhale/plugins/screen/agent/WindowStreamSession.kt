package com.kitakkun.jetwhale.plugins.screen.agent

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.ViewTreeObserver
import android.view.inspector.WindowInspector
import androidx.annotation.RequiresApi
import com.kitakkun.jetwhale.plugins.screen.protocol.FrameTiming
import com.kitakkun.jetwhale.plugins.screen.protocol.ScreenFrame
import com.kitakkun.jetwhale.plugins.screen.protocol.StartScreenStream
import java.io.ByteArrayOutputStream
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.encoding.Base64
import kotlin.math.max
import kotlin.math.roundToInt

/** How often the session looks for windows that opened since it last looked (a dialog, a popup). */
private const val WINDOW_RESCAN_INTERVAL_MILLIS = 500L

/**
 * One stream: watches every window of the app for redraws and, as the [FramePacer] allows, copies
 * them with PixelCopy, composites them where they sit on screen, masks, and encodes a JPEG.
 *
 * Confined to the main thread, except the copy results and the encoding, which run on [worker].
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class WindowStreamSession(
    private val settings: StartScreenStream,
    private val mainHandler: Handler,
    private val worker: Handler,
    private val onFrame: (ScreenFrame) -> Unit,
) {
    private val pacer = FramePacer(minIntervalMillis = 1000L / settings.maxFramesPerSecond.coerceAtLeast(1), initialCredits = settings.initialCredits)
    private val drawListeners = WeakHashMap<View, ViewTreeObserver.OnDrawListener>()
    private var sequence = 0L
    private var closed = false
    private var captureScheduled = false
    private var captureInFlight = false

    private val capture = Runnable(::captureNow)
    private val rescan = object : Runnable {
        override fun run() {
            if (closed) return
            attachToWindows()
            mainHandler.postDelayed(this, WINDOW_RESCAN_INTERVAL_MILLIS)
        }
    }

    fun open() {
        rescan.run()
        schedule()
    }

    fun grant(count: Int) {
        pacer.grant(count)
        schedule()
    }

    fun close() {
        closed = true
        mainHandler.removeCallbacks(capture)
        mainHandler.removeCallbacks(rescan)
        drawListeners.forEach { (view, listener) -> view.viewTreeObserver.takeIf(ViewTreeObserver::isAlive)?.removeOnDrawListener(listener) }
        drawListeners.clear()
    }

    private fun attachToWindows() {
        windows().filterNot(drawListeners::containsKey).forEach { root ->
            // A redraw only marks the picture stale; the capture itself is posted, because a draw
            // listener must not touch the view tree it is being called from.
            val listener = ViewTreeObserver.OnDrawListener {
                pacer.markDirty()
                schedule()
            }
            root.viewTreeObserver.addOnDrawListener(listener)
            drawListeners[root] = listener
        }
    }

    private fun schedule() {
        if (closed || captureScheduled || captureInFlight) return
        val delay = pacer.delayBeforeCapture(SystemClock.uptimeMillis()) ?: return
        captureScheduled = true
        mainHandler.postDelayed(capture, delay)
    }

    private fun captureNow() {
        captureScheduled = false
        if (closed) return
        val startedNanos = System.nanoTime()
        val roots = windows()
        if (roots.isEmpty()) return
        pacer.onCaptureStarted(SystemClock.uptimeMillis())
        captureInFlight = true
        val placements = roots.map(::placementOf)
        val frameWidth = max(1, (placements.maxOf(WindowPlacement::right) * settings.scale).roundToInt())
        val frameHeight = max(1, (placements.maxOf(WindowPlacement::bottom) * settings.scale).roundToInt())
        val copies = FrameCopies(
            captureStartedEpochMillis = System.currentTimeMillis(),
            requestedAtMillis = SystemClock.uptimeMillis(),
            placements = placements,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            // Read with the pixels, on the thread layout runs on: masks read later, at encode time,
            // could already have moved with a scroll while the copied pixels had not.
            masks = ScreenMasks.current().mapNotNull { it.toFrame(settings.scale, frameWidth, frameHeight) },
        )
        placements.forEach(copies::request)
        copies.onMainThreadDone(mainThreadMicros = (System.nanoTime() - startedNanos) / 1000)
    }

    private fun placementOf(root: View): WindowPlacement {
        val location = IntArray(2)
        root.getLocationOnScreen(location)
        return WindowPlacement(root, left = location[0], top = location[1], width = root.width, height = root.height)
    }

    // WindowInspector lists windows in the order they were added, which is the order they stack in.
    private fun windows(): List<View> = WindowInspector.getGlobalWindowViews()
        .filter { it.isAttachedToWindow && it.visibility == View.VISIBLE && it.width > 0 && it.height > 0 }

    /** The copies of one frame, collected on the worker as they arrive. */
    private inner class FrameCopies(
        val captureStartedEpochMillis: Long,
        val requestedAtMillis: Long,
        val placements: List<WindowPlacement>,
        val frameWidth: Int,
        val frameHeight: Int,
        val masks: List<FrameRect>,
    ) {
        @Volatile
        private var mainThreadMicros = 0L

        // One count per window copy, and one for the main thread finishing its part: encoding waits
        // for all of them, so it always sees the main thread's timing.
        private val pending = AtomicInteger(placements.size + 1)
        private val copied = arrayOfNulls<Bitmap>(placements.size)

        fun onMainThreadDone(mainThreadMicros: Long) {
            this.mainThreadMicros = mainThreadMicros
            arrive()
        }

        private fun arrive() {
            if (pending.decrementAndGet() == 0) worker.post(::encode)
        }

        fun request(placement: WindowPlacement) {
            val index = placements.indexOf(placement)
            val bitmap = Bitmap.createBitmap(
                max(1, (placement.width * settings.scale).roundToInt()),
                max(1, (placement.height * settings.scale).roundToInt()),
                Bitmap.Config.ARGB_8888,
            )
            val onCopied = { succeeded: Boolean ->
                // A secure window, or one that went away mid-copy, stays black in the frame.
                if (succeeded) copied[index] = bitmap else bitmap.recycle()
                arrive()
            }
            requestPixelCopy(placement.root, bitmap, onCopied)
        }

        private fun encode() {
            val pixelCopyMillis = SystemClock.uptimeMillis() - requestedAtMillis
            val composeStarted = SystemClock.uptimeMillis()
            val frame = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frame)
            canvas.drawColor(Color.BLACK)
            placements.forEachIndexed { index, placement ->
                val bitmap = copied[index] ?: return@forEachIndexed
                canvas.drawBitmap(bitmap, placement.left * settings.scale, placement.top * settings.scale, null)
                bitmap.recycle()
            }
            val maskPaint = Paint().apply { color = Color.BLACK }
            masks.forEach { rect ->
                canvas.drawRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), maskPaint)
            }
            val compressStarted = SystemClock.uptimeMillis()
            val jpeg = ByteArrayOutputStream().also { frame.compress(Bitmap.CompressFormat.JPEG, settings.jpegQuality, it) }.toByteArray()
            frame.recycle()
            val base64Started = SystemClock.uptimeMillis()
            val jpegBase64 = Base64.encode(jpeg)
            val base64Done = SystemClock.uptimeMillis()
            onFrame(
                ScreenFrame(
                    sequence = sequence++,
                    widthPx = frameWidth,
                    heightPx = frameHeight,
                    jpegBase64 = jpegBase64,
                    timing = FrameTiming(
                        captureStartedEpochMillis = captureStartedEpochMillis,
                        mainThreadMicros = mainThreadMicros,
                        pixelCopyMillis = pixelCopyMillis,
                        composeMillis = compressStarted - composeStarted,
                        compressMillis = base64Started - compressStarted,
                        base64Millis = base64Done - base64Started,
                        sentEpochMillis = System.currentTimeMillis(),
                    ),
                ),
            )
            mainHandler.post {
                captureInFlight = false
                schedule()
            }
        }
    }

    /**
     * Copies [root]'s window into [bitmap], scaled to fit it. From API 34 any window can be copied
     * by one of its views; before that only an activity's own window can, so a dialog stays black.
     */
    private fun requestPixelCopy(root: View, bitmap: Bitmap, onCopied: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val request = PixelCopy.Request.Builder.ofWindow(root).setDestinationBitmap(bitmap).build()
            PixelCopy.request(request, worker::post) { result -> onCopied(result.status == PixelCopy.SUCCESS) }
            return
        }
        val window = root.context.findActivity()?.window?.takeIf { it.decorView === root }
        if (window == null) {
            onCopied(false)
            return
        }
        PixelCopy.request(window, bitmap, { status -> onCopied(status == PixelCopy.SUCCESS) }, worker)
    }
}

private class WindowPlacement(val root: View, val left: Int, val top: Int, val width: Int, val height: Int) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
