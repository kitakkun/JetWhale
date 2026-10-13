package com.kitakkun.jetwhale.plugins.nav3.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.nav3.protocol.MutationResult
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackOperation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalJetWhaleApi::class)
class Nav3McpCommandsTest {
    @Test
    fun `getBackStack numbers the entries and marks the current one`() {
        val controller = FakeNav3BackStackController(listOf(snapshot("main", "Home", "Detail")))

        val result = GetBackStackCommand(controller).runCommand()

        val entries = result.getValue("stacks").jsonArray.single().jsonObject.getValue("entries").jsonArray
        assertEquals(listOf(0, 1), entries.map { it.jsonObject.getValue("index").jsonPrimitive.content.toInt() })
        assertEquals(listOf(false, true), entries.map { it.jsonObject.getValue("isCurrent").jsonPrimitive.content.toBoolean() })
    }

    @Test
    fun `getBackStack explains itself when the app registered nothing`() {
        val result = GetBackStackCommand(FakeNav3BackStackController(emptyList())).runCommand()

        assertTrue(result.getValue("stacks").jsonArray.isEmpty())
        assertTrue(result.containsKey("note"))
    }

    @Test
    fun `pushNavKey targets the app's only stack without being told`() {
        val controller = FakeNav3BackStackController(listOf(snapshot("main", "Home")))

        PushNavKeyCommand(controller).runCommand(buildJsonObject { put("key", navKey("Detail", id = "42")) })

        val (stackId, operations) = controller.requests.single()
        assertEquals("main", stackId)
        assertEquals(listOf(NavBackStackOperation.Push(key = navKey("Detail", id = "42"), index = null)), operations)
    }

    @Test
    fun `pushNavKey asks which stack when the app has more than one`() {
        val controller = FakeNav3BackStackController(listOf(snapshot("main", "Home"), snapshot("sheet", "Filters")))

        val failure = assertFailsWith<JetWhaleMcpArgumentException> {
            PushNavKeyCommand(controller).runCommand(buildJsonObject { put("key", navKey("Detail")) })
        }

        assertEquals("the app has 2 back stack(s): main, sheet; pass stackId to say which one", failure.message)
        assertTrue(controller.requests.isEmpty())
    }

    @Test
    fun `a stackId the app does not have is refused before anything is sent`() {
        val controller = FakeNav3BackStackController(listOf(snapshot("main", "Home")))

        assertFailsWith<JetWhaleMcpArgumentException> {
            PushNavKeyCommand(controller).runCommand(
                buildJsonObject {
                    put("key", navKey("Detail"))
                    put("stackId", "sheet")
                },
            )
        }

        assertTrue(controller.requests.isEmpty())
    }

    @Test
    fun `popBackStack pops one entry by default`() {
        val controller = FakeNav3BackStackController(listOf(snapshot("main", "Home", "Detail")))

        PopBackStackCommand(controller).runCommand()

        assertEquals(listOf(NavBackStackOperation.Pop(count = 1)), controller.requests.single().second)
    }

    @Test
    fun `popBackStack pops to toIndex and ignores count when both are given`() {
        val controller = FakeNav3BackStackController(listOf(snapshot("main", "Home", "List", "Detail")))

        PopBackStackCommand(controller).runCommand(
            buildJsonObject {
                put("toIndex", 1)
                put("inclusive", true)
                put("count", 5)
            },
        )

        assertEquals(
            listOf(NavBackStackOperation.PopTo(index = 1, inclusive = true)),
            controller.requests.single().second,
        )
    }

    @Test
    fun `replaceBackStack refuses an empty stack instead of crashing the app`() {
        val controller = FakeNav3BackStackController(listOf(snapshot("main", "Home")))

        val failure = assertFailsWith<JetWhaleMcpArgumentException> {
            ReplaceBackStackCommand(controller).runCommand(buildJsonObject { put("keys", buildJsonArray { }) })
        }

        assertEquals("keys must not be empty: Navigation 3 cannot render an empty back stack", failure.message)
        assertTrue(controller.requests.isEmpty())
    }

    @Test
    fun `a refusal from the app is reported as a failed mutation, not as a crash`() {
        val controller = FakeNav3BackStackController(
            stacks = listOf(snapshot("main", "Home")),
            result = MutationResult(error = "removeAt index 9 is out of range (0..0)", snapshot = snapshot("main", "Home")),
        )

        val result = RemoveNavKeyCommand(controller).runCommand(buildJsonObject { put("index", 9) })

        assertEquals(false, result.getValue("applied").jsonPrimitive.content.toBoolean())
        assertEquals("removeAt index 9 is out of range (0..0)", result.getValue("error").jsonPrimitive.content)
        assertEquals(1, result.getValue("stack").jsonObject.getValue("size").jsonPrimitive.content.toInt())
    }

    @Test
    fun `listNavKeyTypes says so when the app exposed no key types`() {
        val result = ListNavKeyTypesCommand(FakeNav3BackStackController(listOf(snapshot("main", "Home")))).runCommand()

        assertTrue(result.getValue("keyTypes").jsonArray.isEmpty())
        assertTrue(result.containsKey("note"))
    }

    @Test
    fun `listNavKeyTypes lists every key type when no query is given`() {
        val result = ListNavKeyTypesCommand(controllerWithKeyTypes()).runCommand()

        assertEquals(listOf("com.example.navigation.ProfileKey", "com.example.settings.SettingsKey", "Detail"), result.serialNames())
        assertFalse(result.containsKey("note"))
    }

    @Test
    fun `listNavKeyTypes lists only the key types whose serial name contains the query`() {
        val result = ListNavKeyTypesCommand(controllerWithKeyTypes()).runCommand(buildJsonObject { put("query", "settings") })

        assertEquals(listOf("com.example.settings.SettingsKey"), result.serialNames())
        assertFalse(result.containsKey("note"))
    }

    @Test
    fun `listNavKeyTypes names the query when no key type matches it`() {
        val result = ListNavKeyTypesCommand(controllerWithKeyTypes()).runCommand(buildJsonObject { put("query", "Checkout") })

        assertTrue(result.getValue("keyTypes").jsonArray.isEmpty())
        assertEquals(
            "No key type matches \"Checkout\". The app has 3; call listNavKeyTypes without query to list them all.",
            result.getValue("note").jsonPrimitive.content,
        )
    }

    private fun controllerWithKeyTypes() = FakeNav3BackStackController(
        stacks = listOf(snapshot("main", "Home")),
        keyTypes = listOf(keyType("com.example.navigation.ProfileKey"), keyType("com.example.settings.SettingsKey"), keyType("Detail")),
    )

    private fun JsonObject.serialNames(): List<String> = getValue("keyTypes").jsonArray.map { it.jsonObject.getValue("serialName").jsonPrimitive.content }
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpCommand.runCommand(arguments: JsonObject = buildJsonObject { }): JsonObject = runBlocking {
    Json.parseToJsonElement(execute(JetWhaleMcpArguments(arguments))).jsonObject
}
