package com.kitakkun.jetwhale.plugins.actions.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpContent
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionOutcome
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionParameter
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionResult
import com.kitakkun.jetwhale.plugins.actions.protocol.ParameterType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalJetWhaleApi::class)
class ActionsMcpCommandsTest {
    private val loginAs = action("Account / Log in as", destructive = false, parameters = listOf(parameter("email", ParameterType.STRING, optional = false, nullable = false)))
    private val wipe = action("Wipe local data", destructive = true, parameters = emptyList())
    private val client = FakeActionsClient(
        actions = listOf(loginAs, wipe),
        options = mapOf((loginAs.id to "email") to listOf("qa@example.com")),
        result = succeeded,
    )
    private val browser = ActionsBrowser(client, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `listActions gives each action an argument schema and marks destructive ones`() {
        val emailOptions = client.actions.first().copy(parameters = listOf(loginAs.parameters.single().copy(hasOptions = true)))
        client.actions = listOf(emailOptions, wipe)

        val listed = ListActionsCommand(browser).structuredAnswer().getValue("actions").jsonArray.map(JsonElement::jsonObject)

        val login = listed.first { it.getValue("id").jsonPrimitive.content == loginAs.id }
        assertEquals("string", login.getValue("argumentsSchema").jsonObject.getValue("properties").jsonObject.getValue("email").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals(listOf("email"), login.getValue("argumentsSchema").jsonObject.getValue("required").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(false, login.getValue("argumentsSchema").jsonObject.getValue("additionalProperties").jsonPrimitive.boolean)
        assertEquals("qa@example.com", login.getValue("suggestedValues").jsonObject.getValue("email").jsonArray.single().jsonPrimitive.content)
        assertEquals(true, listed.first { it.getValue("id").jsonPrimitive.content == wipe.id }.getValue("destructive").jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `a nullable enum lists null among its allowed values`() {
        val nullableTier = ActionParameter("tier", ParameterType.ENUM, optional = false, nullable = true, description = null, enumValues = listOf("FREE", "PRO"), hasOptions = false)
        client.actions = listOf(action("Set tier", destructive = false, parameters = listOf(nullableTier)))

        val tier = ListActionsCommand(browser).structuredAnswer().getValue("actions").jsonArray.single().jsonObject
            .getValue("argumentsSchema").jsonObject.getValue("properties").jsonObject.getValue("tier").jsonObject

        assertEquals(listOf(JsonPrimitive("FREE"), JsonPrimitive("PRO"), JsonNull), tier.getValue("enum").jsonArray.toList())
    }

    @Test
    fun `the confirmation of a destructive run reaches the app`() {
        RunActionCommand(browser).structuredAnswer(
            buildJsonObject {
                put("id", wipe.id)
                put("confirmDestructive", true)
            },
        )
        RunActionCommand(browser).structuredAnswer(
            buildJsonObject {
                put("id", loginAs.id)
                putJsonObject("arguments") { put("email", "a") }
            },
        )

        assertEquals(listOf(true, false), client.confirmations)
    }

    @Test
    fun `runAction passes the arguments and returns the outcome`() {
        val result = RunActionCommand(browser).structuredAnswer(
            buildJsonObject {
                put("id", loginAs.id)
                putJsonObject("arguments") { put("email", "qa@example.com") }
            },
        )

        assertEquals("SUCCESS", result.getValue("outcome").jsonPrimitive.content)
        assertEquals(loginAs.id to JsonObject(mapOf("email" to JsonPrimitive("qa@example.com"))), client.runs.single())
        assertEquals(RunOrigin.AI_AGENT, browser.history.single().origin)
    }

    @Test
    fun `a run that does not succeed is a failed call that keeps its error and stack trace`() {
        val failing = FakeActionsClient(
            actions = listOf(loginAs),
            options = emptyMap(),
            result = ActionResult(ActionOutcome.FAILURE, text = null, json = null, error = "no such user", stackTrace = "at LoginAs.run", durationMillis = 3),
        )
        val command = RunActionCommand(ActionsBrowser(failing, CoroutineScope(Dispatchers.Unconfined)))

        val result = runBlocking {
            command.run(
                JetWhaleMcpArguments(
                    buildJsonObject {
                        put("id", loginAs.id)
                        putJsonObject("arguments") { put("email", "nobody@example.com") }
                    },
                ),
            )
        }

        assertTrue(result.isError)
        assertEquals("'Log in as' ended with FAILURE after 3 ms: no such user\nat LoginAs.run", (result.content.single() as JetWhaleMcpContent.Text).text)
    }

    @Test
    fun `a destructive action is refused without explicit confirmation`() {
        assertFailsWith<JetWhaleMcpArgumentException> { RunActionCommand(browser).structuredAnswer(buildJsonObject { put("id", wipe.id) }) }
        assertTrue(client.runs.isEmpty())

        RunActionCommand(browser).structuredAnswer(
            buildJsonObject {
                put("id", wipe.id)
                put("confirmDestructive", true)
            },
        )
        assertEquals(wipe.id, client.runs.single().first)
    }

    @Test
    fun `an unknown id is an argument error naming listActions`() {
        val failure = assertFailsWith<JetWhaleMcpArgumentException> { RunActionCommand(browser).structuredAnswer(buildJsonObject { put("id", "nope") }) }

        assertTrue(failure.message.orEmpty().contains("listActions"))
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpCommand.structuredAnswer(arguments: JsonObject = buildJsonObject { }): JsonObject = runBlocking {
    checkNotNull(run(JetWhaleMcpArguments(arguments)).structuredContent)
}
