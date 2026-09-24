package com.kitakkun.jetwhale.plugins.actions.agent

import com.kitakkun.jetwhale.annotations.McpDescription
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionParameter
import com.kitakkun.jetwhale.plugins.actions.protocol.ParameterType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.jvm.JvmInline
import kotlin.test.Test
import kotlin.test.assertEquals

class ActionParametersTest {
    @Test
    fun `each property becomes a field typed by how it is entered`() {
        val parameters = Everything.serializer().descriptor.toActionParameters(parametersWithOptions = setOf("email"))

        assertEquals(
            listOf(
                ActionParameter("email", ParameterType.STRING, optional = false, nullable = false, description = "The account to use", enumValues = emptyList(), hasOptions = true),
                ActionParameter("count", ParameterType.INTEGER, optional = true, nullable = false, description = null, enumValues = emptyList(), hasOptions = false),
                ActionParameter("ratio", ParameterType.NUMBER, optional = false, nullable = true, description = null, enumValues = emptyList(), hasOptions = false),
                ActionParameter("enabled", ParameterType.BOOLEAN, optional = false, nullable = false, description = null, enumValues = emptyList(), hasOptions = false),
                ActionParameter("tier", ParameterType.ENUM, optional = false, nullable = false, description = null, enumValues = listOf("FREE", "PRO"), hasOptions = false),
                ActionParameter("tags", ParameterType.JSON, optional = false, nullable = false, description = null, enumValues = emptyList(), hasOptions = false),
                ActionParameter("userId", ParameterType.INTEGER, optional = false, nullable = false, description = null, enumValues = emptyList(), hasOptions = false),
            ),
            parameters,
        )
    }

    @Test
    fun `an action without arguments has no fields`() {
        assertEquals(emptyList(), Unit.serializer().descriptor.toActionParameters(parametersWithOptions = emptySet()))
    }
}

@Serializable
private enum class Tier { FREE, PRO }

@Serializable
@JvmInline
private value class UserId(val value: Long)

@Serializable
private data class Everything(
    @McpDescription("The account to use")
    val email: String,
    val count: Int = 1,
    val ratio: Double?,
    val enabled: Boolean,
    val tier: Tier,
    val tags: List<String>,
    val userId: UserId,
)
