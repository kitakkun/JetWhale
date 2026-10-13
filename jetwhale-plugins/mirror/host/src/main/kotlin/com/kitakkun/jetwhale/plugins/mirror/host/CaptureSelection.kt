package com.kitakkun.jetwhale.plugins.mirror.host

import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

/** A capture on AWT's clipboard: its file, and for a screenshot also its image. */
internal class CaptureSelection private constructor(private val file: File, private val image: Image?) : Transferable {
    private val flavors = listOfNotNull(DataFlavor.imageFlavor.takeIf { image != null }, DataFlavor.javaFileListFlavor)

    override fun getTransferDataFlavors(): Array<DataFlavor> = flavors.toTypedArray()

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor in flavors

    override fun getTransferData(flavor: DataFlavor): Any = when {
        flavor == DataFlavor.javaFileListFlavor -> listOf(file)
        flavor == DataFlavor.imageFlavor && image != null -> image
        else -> throw UnsupportedFlavorException(flavor)
    }

    companion object {
        /** Reads a screenshot's image; a recording goes on the clipboard as its file alone. */
        fun of(capture: Capture): CaptureSelection {
            val image = when (capture.info.kind) {
                CaptureKind.Screenshot -> ImageIO.read(capture.file) ?: throw IOException("it is not an image")
                CaptureKind.Recording -> null
            }
            return CaptureSelection(capture.file, image)
        }
    }
}
