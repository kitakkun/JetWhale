package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpParameterDescriptor
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpToolDescriptor
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class McpToolSchemaTest {

    private val descriptor = JetWhaleMcpToolDescriptor(
        name = "example.tool",
        description = "An example.",
        parameters = mapOf(
            "required1" to JetWhaleMcpParameterDescriptor(
                schema = buildJsonObject { put("type", "string") },
                description = "A required parameter.",
            ),
            "optional1" to JetWhaleMcpParameterDescriptor(
                schema = buildJsonObject { put("type", "integer") },
                description = "An optional parameter.",
                required = false,
            ),
        ),
    )

    @Test
    fun `toToolSchema merges each parameter's description into its schema`() {
        val properties = requireNotNull(descriptor.toToolSchema().properties)

        assertEquals("string", properties.getValue("required1").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("A required parameter.", properties.getValue("required1").jsonObject.getValue("description").jsonPrimitive.content)
        assertEquals("An optional parameter.", properties.getValue("optional1").jsonObject.getValue("description").jsonPrimitive.content)
    }

    @Test
    fun `toToolSchema requires only the parameters that are declared required`() {
        assertEquals(listOf("required1"), descriptor.toToolSchema().required)
    }

    /** A host-scoped plugin's tools route on the tool name alone, so no session may leak into them. */
    @Test
    fun `toToolSchema without leading properties advertises no sessionId`() {
        val schema = descriptor.toToolSchema()

        assertFalse("sessionId" in requireNotNull(schema.properties).keys)
        assertFalse("sessionId" in schema.required.orEmpty())
    }

    @Test
    fun `toToolSchema lists leading properties first and marks them required`() {
        val schema = descriptor.toToolSchema(
            leadingProperties = mapOf("sessionId" to stringProperty("The session.")),
        )

        assertEquals(listOf("sessionId", "required1", "optional1"), requireNotNull(schema.properties).keys.toList())
        assertEquals(listOf("sessionId", "required1"), schema.required)
    }
}
