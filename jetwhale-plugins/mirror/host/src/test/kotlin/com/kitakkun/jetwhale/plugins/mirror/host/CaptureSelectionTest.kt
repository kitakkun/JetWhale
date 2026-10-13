package com.kitakkun.jetwhale.plugins.mirror.host

import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class CaptureSelectionTest {
    private val folder: File = Files.createTempDirectory("mirror-selection").toFile()

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `a screenshot goes on the clipboard as its image and as its file`() {
        val png = File(folder, "125424-screenshot.png").also { ImageIO.write(BufferedImage(3, 5, BufferedImage.TYPE_INT_ARGB), "png", it) }

        val selection = CaptureSelection.of(Capture(png, info(CaptureKind.Screenshot)))

        assertEquals(listOf(DataFlavor.imageFlavor, DataFlavor.javaFileListFlavor), selection.transferDataFlavors.toList())
        val image = selection.getTransferData(DataFlavor.imageFlavor) as Image
        assertEquals(3 to 5, image.getWidth(null) to image.getHeight(null))
        assertEquals(listOf(png), selection.getTransferData(DataFlavor.javaFileListFlavor))
    }

    @Test
    fun `a recording goes on the clipboard as its file alone`() {
        val mp4 = File(folder, "123412-recording.mp4").apply { writeBytes(byteArrayOf(0, 0, 0, 0x18)) }

        val selection = CaptureSelection.of(Capture(mp4, info(CaptureKind.Recording)))

        assertEquals(listOf(DataFlavor.javaFileListFlavor), selection.transferDataFlavors.toList())
        assertEquals(listOf(mp4), selection.getTransferData(DataFlavor.javaFileListFlavor))
        assertFalse(selection.isDataFlavorSupported(DataFlavor.imageFlavor))
        assertFailsWith<UnsupportedFlavorException> { selection.getTransferData(DataFlavor.imageFlavor) }
    }

    @Test
    fun `a screenshot that is not an image cannot be copied`() {
        val broken = File(folder, "125424-screenshot.png").apply { writeText("not a png") }

        assertFailsWith<IOException> { CaptureSelection.of(Capture(broken, info(CaptureKind.Screenshot))) }
    }
}

private fun info(kind: CaptureKind) = CaptureInfo(
    deviceId = "emulator-5554",
    deviceName = "Pixel 9",
    platform = "Android",
    deviceKind = "Emulator",
    osVersion = null,
    kind = kind,
    widthPx = null,
    heightPx = null,
    capturedAtEpochMillis = 0,
    durationMillis = null,
)
