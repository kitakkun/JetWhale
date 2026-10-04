package com.kitakkun.jetwhale.plugins.permissions.host

import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionChange
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionReport
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionStatus
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PermissionsBoardTest {
    private val client = FakePermissionsClient()

    // Dispatchers.Unconfined runs a launch in place until its first suspension; the fake never
    // suspends, so each launched call has finished when launch returns.
    private val board = PermissionsBoard(client, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `a refused request lands in the status as an error`() {
        client.nextError = "no activity of the app is in the foreground"

        board.request("android.permission.CAMERA")

        assertEquals(PermissionsStatus("no activity of the app is in the foreground", isError = true), board.status)
    }

    @Test
    fun `a first load that cannot reach the app lands in the status as an error`() {
        val unreachable = object : PermissionsClient by client {
            override suspend fun report(): PermissionReport = throw JetWhaleMessagingException("timed out")
        }
        val failingBoard = PermissionsBoard(unreachable, CoroutineScope(Dispatchers.Unconfined))

        runBlocking { failingBoard.prepare() }

        assertNull(failingBoard.report)
        assertEquals(PermissionsStatus("Failed to reach the app: timed out", isError = true), failingBoard.status)
    }

    @Test
    fun `pushed changes go on top of the timeline and reload the report`() {
        runBlocking { board.load() }
        client.cameraStatus = PermissionStatus.Granted

        board.onChanged(listOf(change("first"), change("second")))
        board.onChanged(listOf(change("third")))

        assertEquals(listOf("third", "second", "first"), board.timeline.map(PermissionChange::id))
        assertEquals(PermissionStatus.Granted, board.report?.permissions?.single()?.status)
    }

    @Test
    fun `a denial that leaves the status at Denied is recorded with its note`() {
        val denial = PermissionChange("camera", "camera", PermissionStatus.Denied, PermissionStatus.Denied, "Denied once; a request shows the dialog again.", observedAtEpochMillis = 0)

        board.onChanged(listOf(denial))

        assertEquals(listOf(denial), board.timeline)
    }

    @Test
    fun `a slow reload finishing after a newer one does not overwrite it`() {
        val firstRead = CompletableDeferred<Unit>()
        var reads = 0
        val slowFirst = object : PermissionsClient by client {
            override suspend fun report(): PermissionReport {
                val read = ++reads
                if (read == 1) {
                    firstRead.await()
                    return client.report().copy(platform = "stale")
                }
                return client.report().copy(platform = "fresh")
            }
        }
        val slowBoard = PermissionsBoard(slowFirst, CoroutineScope(Dispatchers.Unconfined))

        slowBoard.onChanged(listOf(change("first")))
        slowBoard.onChanged(listOf(change("second")))
        firstRead.complete(Unit)

        assertEquals("fresh", slowBoard.report?.platform)
    }

    private fun change(id: String) = PermissionChange(id = id, label = id, from = PermissionStatus.Denied, to = PermissionStatus.Granted, note = null, observedAtEpochMillis = 0)
}
