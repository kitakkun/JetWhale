package com.kitakkun.jetwhale.plugins.actions.host

import com.kitakkun.jetwhale.plugins.actions.protocol.ParameterType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class ArgumentFormTest {
    private val parameters = listOf(
        parameter("email", ParameterType.STRING, optional = false, nullable = false),
        parameter("count", ParameterType.INTEGER, optional = true, nullable = false),
        parameter("ratio", ParameterType.NUMBER, optional = false, nullable = true),
        parameter("enabled", ParameterType.BOOLEAN, optional = false, nullable = false),
        parameter("tier", ParameterType.ENUM, optional = false, nullable = false),
        parameter("tags", ParameterType.JSON, optional = false, nullable = false),
    )

    @Test
    fun `filled fields become typed JSON values`() {
        val built = buildArguments(parameters, mapOf("email" to "qa@example.com", "count" to "3", "ratio" to "0.5", "enabled" to "true", "tier" to "PRO", "tags" to """["a"]"""))

        assertEquals(
            FormArguments.Valid(
                JsonObject(
                    mapOf(
                        "email" to JsonPrimitive("qa@example.com"),
                        "count" to JsonPrimitive(3L),
                        "ratio" to JsonPrimitive(0.5),
                        "enabled" to JsonPrimitive(true),
                        "tier" to JsonPrimitive("PRO"),
                        "tags" to JsonArray(listOf(JsonPrimitive("a"))),
                    ),
                ),
            ),
            built,
        )
    }

    @Test
    fun `a blank optional field is left out and a blank nullable one is null`() {
        val built = buildArguments(parameters, mapOf("email" to "a", "enabled" to "false", "tier" to "FREE", "tags" to "[]"))

        val arguments = (built as FormArguments.Valid).arguments
        assertEquals(false, "count" in arguments)
        assertEquals(JsonNull, arguments["ratio"])
    }

    @Test
    fun `a blank required string is reported as missing rather than sent empty`() {
        val built = buildArguments(parameters, mapOf("email" to " ", "enabled" to "false", "tier" to "FREE", "tags" to "[]"))

        assertEquals(mapOf("email" to "required"), (built as FormArguments.Invalid).errors)
    }

    @Test
    fun `malformed text is reported per field instead of being sent`() {
        val built = buildArguments(parameters, mapOf("email" to "a", "count" to "three", "enabled" to "false", "tier" to "GOLD", "tags" to "[unclosed"))

        assertEquals(setOf("count", "tier", "tags"), (built as FormArguments.Invalid).errors.keys)
    }

    @Test
    fun `the form starts from the arguments the action last ran with`() {
        val remembered = JsonObject(mapOf("email" to JsonPrimitive("qa@example.com"), "tags" to JsonArray(listOf(JsonPrimitive("a")))))

        val values = initialFormValues(parameters, remembered)

        assertEquals("qa@example.com", values["email"])
        assertEquals("""["a"]""", values["tags"])
        assertEquals("false", values["enabled"])
    }
}
