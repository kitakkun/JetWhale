package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File
import javax.swing.SwingUtilities
import kotlin.io.encoding.Base64

/** Decodes a Base64 body captured by the agent. Throws when the body is not valid Base64. */
internal fun base64Decode(body: String): ByteArray = Base64.decode(body)

/** Puts the decoded image on the system clipboard; returns false when the clipboard refused it. */
internal fun copyImageToClipboard(bitmap: ImageBitmap): Boolean = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransferable(bitmap.toAwtImage()), null)
}.isSuccess

/**
 * Asks for a destination and writes the image's original bytes there, reporting the outcome
 * through [onResult].
 *
 * The dialog runs on the AWT event thread rather than the caller's, which is not guaranteed to be
 * it — a plugin renders into a nested Compose scene the host may drive from elsewhere.
 */
internal fun saveImageToFile(bytes: ByteArray, suggestedFileName: String, onResult: (String) -> Unit) {
    SwingUtilities.invokeLater {
        val dialog = FileDialog(null as Frame?, "Save image", FileDialog.SAVE)
        dialog.file = suggestedFileName
        dialog.isVisible = true
        val directory = dialog.directory
        val fileName = dialog.file
        // Both are null when the dialog was cancelled; stay silent rather than report a failure.
        if (directory == null || fileName == null) return@invokeLater
        val target = File(directory, fileName)
        onResult(
            runCatching { target.writeBytes(bytes) }
                .fold({ "Saved to ${target.absolutePath}" }, { "Could not save the image: ${it.message}" }),
        )
    }
}

/** Offers a decoded image to the clipboard as an image rather than as bytes or a file path. */
private class ImageTransferable(private val image: BufferedImage) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        if (flavor != DataFlavor.imageFlavor) throw UnsupportedFlavorException(flavor)
        return image
    }
}
