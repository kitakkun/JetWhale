package com.kitakkun.jetwhale.plugins.network.agent

import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.REDACTED_PLACEHOLDER
import com.kitakkun.jetwhale.plugins.network.protocol.RedactionScope
import com.kitakkun.jetwhale.plugins.network.protocol.RedactionStrategy
import com.kitakkun.jetwhale.plugins.network.protocol.RedactionTarget
import com.kitakkun.jetwhale.plugins.network.protocol.redact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class NetworkRedactionRulesTest {
    private fun request(
        url: String = "https://api.example.com/login",
        headers: Map<String, List<String>> = emptyMap(),
        body: String? = null,
    ) = CapturedHttpRequest(
        txId = "tx-1",
        method = "POST",
        url = url,
        headers = headers,
        body = body,
        timestampMs = 0L,
    )

    @Test
    fun `header rule redacts values case-insensitively and keeps other headers`() {
        val rules = NetworkRedactionRules { header("Authorization") }
        val redacted = rules.redactAtCapture(
            request(headers = mapOf("authorization" to listOf("Bearer secret"), "Accept" to listOf("application/json"))),
        )
        assertEquals(listOf(REDACTED_PLACEHOLDER), redacted.headers["authorization"])
        assertEquals(listOf("application/json"), redacted.headers["Accept"])
    }

    @Test
    fun `mask strategy replaces each character with an asterisk`() {
        val rules = NetworkRedactionRules { header("Authorization", strategy = RedactionStrategy.MASK) }
        val redacted = rules.redactAtCapture(request(headers = mapOf("Authorization" to listOf("secret"))))
        assertEquals(listOf("******"), redacted.headers["Authorization"])
    }

    @Test
    fun `mask strategy masks emoji as one asterisk per code point`() {
        val rules = NetworkRedactionRules {
            header("Authorization", strategy = RedactionStrategy.MASK)
            bodyJsonField("secret", strategy = RedactionStrategy.MASK)
        }
        val redacted = rules.redactAtCapture(
            request(
                // "🔑" and "🐳" are surrogate pairs (length 2 each); "あ" is a single UTF-16 unit.
                headers = mapOf("Authorization" to listOf("🔑ab🐳")),
                body = """{"secret":"🐳あ🐳"}""",
            ),
        )
        assertEquals(listOf("****"), redacted.headers["Authorization"])
        assertEquals("""{"secret":"***"}""", redacted.body)
    }

    @Test
    fun `query param rule redacts only matching params and preserves fragment`() {
        val rules = NetworkRedactionRules { urlQueryParam("token") }
        val redacted = rules.redactAtCapture(request(url = "https://x.dev/a?token=abc&page=2#frag"))
        assertEquals("https://x.dev/a?token=$REDACTED_PLACEHOLDER&page=2#frag", redacted.url)
    }

    @Test
    fun `body json field rule redacts nested fields and array elements`() {
        val rules = NetworkRedactionRules { bodyJsonField("password") }
        val redacted = rules.redactAtCapture(request(body = """{"user":{"password":"pw"},"items":[{"password":"pw2","id":1}]}"""))
        assertEquals(
            """{"user":{"password":"$REDACTED_PLACEHOLDER"},"items":[{"password":"$REDACTED_PLACEHOLDER","id":1}]}""",
            redacted.body,
        )
    }

    @Test
    fun `masked body json field preserves string length and hides non-string shape`() {
        val rules = NetworkRedactionRules { bodyJsonField("secret", strategy = RedactionStrategy.MASK) }
        val redacted = rules.redactAtCapture(request(body = """{"secret":"abcd","nested":{"secret":1234567}}"""))
        assertEquals("""{"secret":"****","nested":{"secret":"***"}}""", redacted.body)
    }

    @Test
    fun `non-json body is forwarded unchanged`() {
        val rules = NetworkRedactionRules { bodyJsonField("password") }
        assertEquals("password=pw&x=1", rules.redactAtCapture(request(body = "password=pw&x=1")).body)
    }

    @Test
    fun `mcp-only rules are excluded from capture and exposed for the host`() {
        val rules = NetworkRedactionRules {
            header("Authorization")
            header("X-Session-Id", scope = RedactionScope.MCP_ONLY)
        }
        val redacted = rules.redactAtCapture(
            request(headers = mapOf("Authorization" to listOf("Bearer secret"), "X-Session-Id" to listOf("session-1"))),
        )
        assertEquals(listOf("session-1"), redacted.headers["X-Session-Id"])
        assertEquals(listOf(REDACTED_PLACEHOLDER), redacted.headers["Authorization"])

        assertEquals(1, rules.mcpOnlyRules.size)
        val mcpRule = rules.mcpOnlyRules.single()
        assertEquals(RedactionTarget.HEADER, mcpRule.target)
        assertEquals("X-Session-Id", mcpRule.name)
        assertEquals(listOf(REDACTED_PLACEHOLDER), rules.mcpOnlyRules.redact(redacted).headers["X-Session-Id"])
    }

    @Test
    fun `response headers and body are redacted`() {
        val rules = NetworkRedactionRules {
            header("Set-Cookie")
            bodyJsonField("access_token")
        }
        val redacted = rules.redactAtCapture(
            CapturedHttpResponse(
                txId = "tx-1",
                statusCode = 200,
                headers = mapOf("Set-Cookie" to listOf("session=abc", "theme=dark")),
                body = """{"access_token":"jwt","expires_in":3600}""",
                durationMs = 10L,
            ),
        )
        assertEquals(listOf(REDACTED_PLACEHOLDER, REDACTED_PLACEHOLDER), redacted.headers["Set-Cookie"])
        assertEquals("""{"access_token":"$REDACTED_PLACEHOLDER","expires_in":3600}""", redacted.body)
    }

    @Test
    fun `empty rules return the instance unchanged`() {
        val original = request(headers = mapOf("Authorization" to listOf("Bearer secret")))
        assertSame(original, NetworkRedactionRules.None.redactAtCapture(original))
    }
}
