package com.kitakkun.jetwhale.plugins.mirror.host

import java.awt.HeadlessException
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.io.FileNotFoundException
import java.io.IOException

/**
 * The system clipboard. A capture goes on it as its file, which Finder, chat apps and upload fields
 * take, and a screenshot also as its image.
 *
 * On macOS, [osascriptPath] writes the pasteboard: AWT puts a file list there only under types that
 * Finder and most apps do not read as a file. Elsewhere AWT's clipboard takes a [CaptureSelection].
 */
internal class CaptureClipboard(private val osascriptPath: String?) {
    /** Blocks until [capture] is on the clipboard, or throws [IOException] with the reason to show. */
    fun putCapture(capture: Capture) {
        if (!capture.file.isFile) throw FileNotFoundException("it is no longer in the captures folder")
        if (osascriptPath == null) {
            putContents(CaptureSelection.of(capture))
            return
        }
        val process = ProcessBuilder(osascriptPath, "-l", "JavaScript", "-e", PASTEBOARD_SCRIPT, capture.file.absolutePath, capture.info.kind.name)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (process.waitFor() != 0) throw IOException(output.trim())
    }

    fun putText(text: String) = putContents(StringSelection(text))

    // Another app using the clipboard at that moment makes setContents throw instead of wait.
    private fun putContents(contents: Transferable) {
        try {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(contents, null)
        } catch (e: IllegalStateException) {
            throw IOException("the clipboard is in use by another app", e)
        } catch (e: HeadlessException) {
            throw IOException("a host without a window has no clipboard", e)
        }
    }
}

// One pasteboard item holds the file's URL and, for a screenshot, the PNG as saved, so an app that
// pastes an image and one that takes a file both find what they read.
private val PASTEBOARD_SCRIPT = """
    ObjC.import("AppKit");
    function run(argv) {
        var item = $.NSPasteboardItem.alloc.init;
        item.setStringForType($.NSURL.fileURLWithPath(argv[0]).absoluteString, "public.file-url");
        if (argv[1] === "Screenshot") item.setDataForType($.NSData.dataWithContentsOfFile(argv[0]), "public.png");
        var pasteboard = $.NSPasteboard.generalPasteboard;
        pasteboard.clearContents;
        if (!pasteboard.writeObjects($.NSArray.arrayWithObject(item))) throw new Error("the clipboard did not take it");
    }
""".trimIndent()
