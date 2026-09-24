package com.kitakkun.jetwhale.plugins.permissions.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionActionResult
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionCategory
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionChange
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionReport
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionState
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalJetWhaleApi::class)
class PermissionsHostTest {
    private val client = FakePermissionsClient()

    // The fake answers without suspending, so every launched call has finished by the time launch returns.
    private val board = PermissionsBoard(client, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `listPermissions returns the app's report`() {
        val result = ListPermissionsCommand(client).run(buildJsonObject { })

        val ids = result.getValue("permissions").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals(listOf("android.permission.CAMERA"), ids)
    }

    @Test
    fun `requestPermission passes the id through and reports what was started`() {
        val result = RequestPermissionCommand(client).run(buildJsonObject { put("id", "android.permission.CAMERA") })

        assertEquals(listOf("android.permission.CAMERA"), client.requests)
        assertEquals("asked", result.getValue("message").jsonPrimitive.content)
    }

    @Test
    fun `a refused request lands in the status as an error`() {
        client.nextError = "no activity of the app is in the foreground"

        board.request("android.permission.CAMERA")

        assertEquals(PermissionsStatus("no activity of the app is in the foreground", isError = true), board.status)
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

    private fun change(id: String) = PermissionChange(id = id, label = id, from = PermissionStatus.Denied, to = PermissionStatus.Granted, observedAtEpochMillis = 0)
}

private class FakePermissionsClient : PermissionsClient {
    var cameraStatus = PermissionStatus.Denied
    var nextError: String? = null
    val requests = mutableListOf<String>()

    override suspend fun report() = PermissionReport(
        platform = "Android",
        unsupportedReason = null,
        permissions = listOf(
            PermissionState("android.permission.CAMERA", "CAMERA", PermissionCategory.Runtime, "dangerous", cameraStatus, requestable = true, note = null),
        ),
    )

    override suspend fun request(id: String): PermissionActionResult {
        requests += id
        return PermissionActionResult(message = "asked", error = nextError)
    }

    override suspend fun openAppSettings() = PermissionActionResult(message = "opened", error = null)
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpCommand.run(arguments: JsonObject): JsonObject = runBlocking {
    Json.parseToJsonElement(execute(JetWhaleMcpArguments(arguments))).jsonObject
}
