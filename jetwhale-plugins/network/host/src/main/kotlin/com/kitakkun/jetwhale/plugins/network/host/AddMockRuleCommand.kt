package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpParameterDescriptor
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatchType
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.util.UUID

@OptIn(ExperimentalJetWhaleApi::class)
internal class AddMockRuleCommand(
    private val mockRules: () -> List<MockRule>,
    private val syncMockRules: suspend (List<MockRule>) -> JetWhaleMessagingException?,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.addMockRule"
    override val description =
        "Adds a mock rule: requests matching the URL pattern (and optional method) receive the canned response instead of hitting the network. Returns the created rule."
    override val parameters = mapOf(
        "urlPattern" to JetWhaleMcpParameterDescriptor(
            type = "string",
            description = "URL pattern to match, interpreted per matchType.",
        ),
        "matchType" to JetWhaleMcpParameterDescriptor(
            type = "string",
            description = "How urlPattern is compared: CONTAINS, EXACT, or REGEX. Defaults to CONTAINS.",
            required = false,
        ),
        "method" to JetWhaleMcpParameterDescriptor(
            type = "string",
            description = "HTTP method to match (case-insensitive). Matches any method if omitted.",
            required = false,
        ),
        "name" to JetWhaleMcpParameterDescriptor(
            type = "string",
            description = "Human-readable rule name shown in the UI.",
            required = false,
        ),
        "statusCode" to JetWhaleMcpParameterDescriptor(
            type = "integer",
            description = "Status code of the mocked response. Defaults to 200.",
            required = false,
        ),
        "body" to JetWhaleMcpParameterDescriptor(
            type = "string",
            description = "Body of the mocked response. Defaults to empty.",
            required = false,
        ),
        "delayMs" to JetWhaleMcpParameterDescriptor(
            type = "integer",
            description = "Artificial delay before the mocked response is delivered, in milliseconds. Defaults to 0.",
            required = false,
        ),
    )

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val rule = MockRule(
            id = UUID.randomUUID().toString(),
            name = arguments.optionalString("name") ?: "",
            enabled = true,
            matcher = MockMatcher(
                method = arguments.optionalString("method"),
                urlPattern = arguments.requireString("urlPattern"),
                matchType = arguments.optionalEnum("matchType", MockMatchType.entries) ?: MockMatchType.CONTAINS,
            ),
            response = MockResponseSpec(
                statusCode = arguments.optionalInt("statusCode") ?: 200,
                body = arguments.optionalString("body") ?: "",
                delayMs = arguments.optionalLong("delayMs") ?: 0L,
            ),
        )
        return when (val failure = syncMockRules(mockRules() + rule)) {
            null -> Json.encodeToJsonElement(rule).toString()
            else -> syncErrorJson(failure)
        }
    }
}
