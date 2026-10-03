package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorType
import kotlin.test.Test
import kotlin.test.assertEquals

class MirrorVideoTest {
    @Test
    fun `a new frame redraws the video and nothing around it`() {
        VideoScene().use { video ->
            repeat(FRAMES) { frame -> video.show(colorOf(frame)) }

            assertEquals(0, video.siblingDrawsSinceFirstRender)
        }
    }

    @Test
    fun `every render shows the newest frame`() {
        VideoScene().use { video ->
            val sent = List(FRAMES, ::colorOf)

            assertEquals(sent, sent.map(video::show))
        }
    }
}

/**
 * [MirrorVideo] under a sibling that counts its draws, inside a clip, which gives the content a
 * layer of its own as the plugin's surface does in the host.
 */
@OptIn(ExperimentalComposeUiApi::class)
private class VideoScene : AutoCloseable {
    private val surface = MirrorSurface()
    private var siblingDraws = 0
    private val scene = ImageComposeScene(width = SCENE_WIDTH, height = SCENE_HEIGHT, density = Density(1f)) {
        Box(Modifier.fillMaxSize().clip(RectangleShape)) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(SIBLING_HEIGHT.dp).drawBehind { siblingDraws++ })
                MirrorVideo(surface, deviceId = "pixel", interactive = false, onTap = { _, _ -> }, onSwipe = { _, _, _, _ -> }, modifier = Modifier.fillMaxSize())
            }
        }
    }
    private val frames = surface.run {
        switchTo("pixel")
        startStream()
    }
    private var renders = 0L
    private val siblingDrawsAtFirstRender = render().let { siblingDraws }

    val siblingDrawsSinceFirstRender: Int get() = siblingDraws - siblingDrawsAtFirstRender

    /** Sends a frame of [color] and renders; returns the color the video shows. */
    fun show(color: Int): Int {
        frames.writeFrame(width = FRAME_WIDTH, height = FRAME_HEIGHT, colorType = ColorType.BGRA_8888) {
            it.erase(color)
            true
        }
        Snapshot.sendApplyNotifications()
        return render()
    }

    private fun render(): Int = scene.render(renders++ * FRAME_NANOS).use { image ->
        Bitmap.makeFromImage(image).use { it.getColor(SCENE_WIDTH / 2, SIBLING_HEIGHT + (SCENE_HEIGHT - SIBLING_HEIGHT) / 2) }
    }

    override fun close() {
        scene.close()
        surface.close()
    }
}

private fun colorOf(frame: Int): Int = (0xFF000000 or (frame + 1L) * 0x030201).toInt()

private const val SCENE_WIDTH = 200
private const val SCENE_HEIGHT = 440
private const val SIBLING_HEIGHT = 40
private const val FRAME_WIDTH = 100
private const val FRAME_HEIGHT = 200
private const val FRAMES = 20
private const val FRAME_NANOS = 16_000_000L
