package com.kitakkun.jetwhale.plugins.mirror.host

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CaptureLibraryTest {
    private val root: File = Files.createTempDirectory("mirror-captures").toFile()
    private val library = CaptureLibrary(root, ZoneOffset.UTC)
    private val pixel = DeviceListing(id = "emulator-5554", name = "Pixel 9 / Pro", kind = DeviceKind.AndroidEmulator, osVersion = null)
    private val iphone = DeviceListing(id = "00008110-0016", name = "iPhone 13", kind = DeviceKind.IosDevice, osVersion = "iOS 26.6.1")
    private val noon = Instant.parse("2026-09-25T12:30:05Z")

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `a capture goes under its device folder and the day it was taken`() {
        val file = library.newFile(pixel, CaptureKind.Screenshot, noon)

        assertEquals("2026-09-25/123005-screenshot.png", file.relativeTo(library.deviceFolder(pixel)).path)
        assertTrue(library.deviceFolder(pixel).name.startsWith("Pixel-9-Pro-"))
    }

    @Test
    fun `two devices of one name get their own folders`() {
        val twin = pixel.copy(id = "emulator-5556")

        assertNotEquals(library.deviceFolder(pixel), library.deviceFolder(twin))
    }

    @Test
    fun `a renamed device's earlier captures are still listed as its own`() {
        val before = save(pixel, CaptureKind.Screenshot, noon)
        val after = save(pixel.copy(name = "Work phone"), CaptureKind.Screenshot, noon.plusSeconds(60))

        assertEquals(listOf(after, before), library.list(deviceId = pixel.id, kind = null, sinceEpochMillis = null))
    }

    @Test
    fun `two captures in the same second do not overwrite each other`() {
        val first = library.newFile(pixel, CaptureKind.Screenshot, noon).apply { writeText("a") }
        val second = library.newFile(pixel, CaptureKind.Screenshot, noon)

        assertNotEquals(first, second)
    }

    @Test
    fun `captures requested at once in the same second each get their own file`() {
        val start = CountDownLatch(1)
        val files = ConcurrentLinkedQueue<File>()
        val requests = List(SIMULTANEOUS_CAPTURES) {
            thread {
                start.await()
                files += library.newFile(pixel, CaptureKind.Screenshot, noon)
            }
        }

        start.countDown()
        requests.forEach(Thread::join)

        assertEquals(SIMULTANEOUS_CAPTURES, files.toSet().size)
    }

    @Test
    fun `a recorded capture is listed back with everything its sidecar says`() {
        val info = infoOf(iphone, CaptureKind.Recording, noon, durationMillis = 12_400)
        val file = library.newFile(iphone, CaptureKind.Recording, noon).apply { writeText("video") }

        library.record(file, info)

        assertEquals(listOf(Capture(file, info)), library.list(deviceId = null, kind = null, sinceEpochMillis = null))
    }

    @Test
    fun `listing narrows by device, kind and time, newest first`() {
        val older = save(pixel, CaptureKind.Screenshot, noon)
        val newer = save(pixel, CaptureKind.Screenshot, noon.plusSeconds(60))
        save(pixel, CaptureKind.Recording, noon.plusSeconds(30))
        save(iphone, CaptureKind.Screenshot, noon.plusSeconds(90))

        assertEquals(listOf(newer, older), library.list(deviceId = pixel.id, kind = CaptureKind.Screenshot, sinceEpochMillis = null))
        assertEquals(listOf(newer), library.list(deviceId = pixel.id, kind = CaptureKind.Screenshot, sinceEpochMillis = noon.plusSeconds(1).toEpochMilli()))
        assertEquals(4, library.list(deviceId = null, kind = null, sinceEpochMillis = null).size)
    }

    @Test
    fun `a capture whose file was removed by hand is not listed`() {
        save(pixel, CaptureKind.Screenshot, noon).file.delete()

        assertEquals(emptyList(), library.list(deviceId = null, kind = null, sinceEpochMillis = null))
    }

    @Test
    fun `deleting a capture removes the file, its sidecar and its thumbnail`() {
        val capture = save(pixel, CaptureKind.Screenshot, noon, content = smallPng())
        val thumbnail = thumbnailOf(library, capture)

        library.delete(capture)

        assertFalse(capture.file.exists())
        assertFalse(File(capture.file.path + ".json").exists())
        assertFalse(thumbnail?.exists() ?: true)
    }

    @Test
    fun `a thumbnail is made once at thumbnail height and read from its cache after that`() {
        val capture = save(pixel, CaptureKind.Screenshot, noon, content = smallPng())

        val first = thumbnailOf(library, capture)
        val madeAt = first?.lastModified()
        val second = thumbnailOf(library, capture)

        assertEquals(first, second)
        assertEquals(madeAt, second?.lastModified())
        val height = Image.makeFromEncoded(second?.readBytes() ?: ByteArray(0)).use(Image::height)
        assertEquals(THUMBNAIL_HEIGHT, height)
    }

    private fun save(device: DeviceListing, kind: CaptureKind, at: Instant, content: ByteArray = byteArrayOf(1)): Capture {
        val file = library.newFile(device, kind, at).apply { writeBytes(content) }
        return library.record(file, infoOf(device, kind, at, durationMillis = null))
    }
}

private const val SIMULTANEOUS_CAPTURES = 8

private fun infoOf(device: DeviceListing, kind: CaptureKind, at: Instant, durationMillis: Long?) = CaptureInfo(
    deviceId = device.id,
    deviceName = device.name,
    platform = device.kind.platform.label,
    deviceKind = device.kind.label,
    osVersion = device.osVersion,
    kind = kind,
    widthPx = 1080,
    heightPx = 2400,
    capturedAtEpochMillis = at.toEpochMilli(),
    durationMillis = durationMillis,
)

private fun smallPng(): ByteArray = Surface.makeRasterN32Premul(108, 480).use { surface ->
    surface.makeImageSnapshot().use { it.encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0) }
}
