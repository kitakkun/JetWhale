package com.kitakkun.jetwhale.plugins.permissions.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
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
class PermissionsMcpCommandsTest {
    private val client = FakePermissionsClient()

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
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpCommand.run(arguments: JsonObject): JsonObject = runBlocking {
    Json.parseToJsonElement(execute(JetWhaleMcpArguments(arguments))).jsonObject
}
