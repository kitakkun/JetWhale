package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
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

    private val urlPattern by string("URL pattern to match, interpreted per matchType.")
    private val matchType by enumOrNull(
        "How urlPattern is compared: CONTAINS, EXACT, or REGEX. Defaults to CONTAINS.",
        MockMatchType.entries,
    )
    private val method by stringOrNull("HTTP method to match (case-insensitive). Matches any method if omitted.")

    // The property cannot be called `name` (that is the tool name), so the wire name is overridden.
    private val ruleName by stringOrNull("Human-readable rule name shown in the UI.", name = "name")
    private val statusCode by intOrNull("Status code of the mocked response. Defaults to 200.")
    private val body by stringOrNull("Body of the mocked response. Defaults to empty.")
    private val delayMs by longOrNull("Artificial delay before the mocked response is delivered, in milliseconds. Defaults to 0.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val rule = MockRule(
            id = UUID.randomUUID().toString(),
            name = arguments[ruleName] ?: "",
            enabled = true,
            matcher = MockMatcher(
                method = arguments[method],
                urlPattern = arguments[urlPattern],
                matchType = arguments[matchType] ?: MockMatchType.CONTAINS,
            ),
            response = MockResponseSpec(
                statusCode = arguments[statusCode] ?: 200,
                body = arguments[body] ?: "",
                delayMs = arguments[delayMs] ?: 0L,
            ),
        )
        return when (val failure = syncMockRules(mockRules() + rule)) {
            null -> Json.encodeToJsonElement(rule).toString()
            else -> syncErrorJson(failure)
        }
    }
}
