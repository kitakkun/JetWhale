package com.kitakkun.jetwhale.plugins.network.agent

import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.HttpRequestFailure
import com.kitakkun.jetwhale.plugins.network.protocol.REDACTED_PLACEHOLDER
import com.kitakkun.jetwhale.plugins.network.protocol.RedactionScope
import com.kitakkun.jetwhale.plugins.network.protocol.RedactionStrategy
import com.kitakkun.jetwhale.plugins.network.protocol.RedactionTarget
import com.kitakkun.jetwhale.plugins.network.protocol.redact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NetworkRedactionRulesTest {
    private val formHeaders = mapOf("Content-Type" to listOf("application/x-www-form-urlencoded"))

    @Test
    fun `header rule redacts values case-insensitively and keeps other headers`() {
        val rules = NetworkRedactionRules { header("Authorization") }
        val redacted = rules.redactAtCapture(
            request(headers = mapOf("authorization" to listOf("Bearer secret"), "Accept" to listOf("application/json"))),
        )
        assertEquals(listOf(REDACTED_PLACEHOLDER), redacted.headers["authorization"])
        assertEquals(listOf("application/json"), redacted.headers["Accept"])
    }

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
        bodyTruncated = false,
        timestampMs = 0L,
    )

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
            bodyField("secret", strategy = RedactionStrategy.MASK)
        }
        val redacted = rules.redactAtCapture(
            request(
                headers = mapOf("Authorization" to listOf("🔑ab🐳")),
                body = """{"secret":"🐳あ🐳"}""",
            ),
        )
        assertEquals(listOf("****"), redacted.headers["Authorization"])
        assertEquals("""{"secret":"***"}""", redacted.body)
    }

    @Test
    fun `mask strategy masks zwj-combined and modified emoji per code point`() {
        val rules = NetworkRedactionRules { header("Authorization", strategy = RedactionStrategy.MASK) }
        val redacted = rules.redactAtCapture(
            request(
                headers = mapOf(
                    // "👨‍👩‍👧" is three surrogate-pair emoji joined by two ZWJs: 5 code points.
                    // "👍🏻" is an emoji plus a skin-tone modifier, both surrogate pairs: 2 code points.
                    "Authorization" to listOf("👨‍👩‍👧", "👍🏻"),
                ),
            ),
        )
        assertEquals(listOf("*****", "**"), redacted.headers["Authorization"])
    }

    @Test
    fun `query param rule redacts only matching params and preserves fragment`() {
        val rules = NetworkRedactionRules { urlQueryParam("token") }
        val redacted = rules.redactAtCapture(request(url = "https://api.example.com/a?token=abc&page=2#frag"))
        assertEquals("https://api.example.com/a?token=$REDACTED_PLACEHOLDER&page=2#frag", redacted.url)
    }

    @Test
    fun `body field rule redacts nested json fields and array elements`() {
        val rules = NetworkRedactionRules { bodyField("password") }
        val redacted = rules.redactAtCapture(request(body = """{"user":{"password":"pw"},"items":[{"password":"pw2","id":1}]}"""))
        assertEquals(
            """{"user":{"password":"$REDACTED_PLACEHOLDER"},"items":[{"password":"$REDACTED_PLACEHOLDER","id":1}]}""",
            redacted.body,
        )
    }

    @Test
    fun `masked body field preserves json string length and hides non-string shape`() {
        val rules = NetworkRedactionRules { bodyField("secret", strategy = RedactionStrategy.MASK) }
        val redacted = rules.redactAtCapture(request(body = """{"secret":"abcd","nested":{"secret":1234567}}"""))
        assertEquals("""{"secret":"****","nested":{"secret":"***"}}""", redacted.body)
    }

    @Test
    fun `body without a structured content type is forwarded unchanged`() {
        val rules = NetworkRedactionRules { bodyField("password") }
        assertEquals("password: pw", rules.redactAtCapture(request(body = "password: pw")).body)
    }

    @Test
    fun `form body rule redacts only matching params`() {
        val rules = NetworkRedactionRules { bodyField("password") }
        val redacted = rules.redactAtCapture(
            request(headers = formHeaders, body = "PassWord=pw&user=alice&flag"),
        )
        assertEquals("PassWord=$REDACTED_PLACEHOLDER&user=alice&flag", redacted.body)
    }

    @Test
    fun `form body rule matches percent-encoded names and masks the decoded value`() {
        val rules = NetworkRedactionRules { bodyField("user password", strategy = RedactionStrategy.MASK) }
        val redacted = rules.redactAtCapture(
            // "%E3%81%82" is "\u3042", a single character encoded as three UTF-8 bytes.
            request(headers = formHeaders, body = "user+password=ab%E3%81%82"),
        )
        assertEquals("user+password=***", redacted.body)
    }

    @Test
    fun `form body rule leaves a malformed percent escape as written`() {
        val rules = NetworkRedactionRules { bodyField("a%-1b", "c%zzd") }
        val redacted = rules.redactAtCapture(request(headers = formHeaders, body = "a%-1b=x&c%zzd=y"))
        assertEquals("a%-1b=$REDACTED_PLACEHOLDER&c%zzd=$REDACTED_PLACEHOLDER", redacted.body)
    }

    @Test
    fun `form body rule redacts every occurrence of a repeated parameter`() {
        val rules = NetworkRedactionRules { bodyField("password") }
        val redacted = rules.redactAtCapture(request(headers = formHeaders, body = "password=a&password=b"))
        assertEquals("password=$REDACTED_PLACEHOLDER&password=$REDACTED_PLACEHOLDER", redacted.body)
    }

    @Test
    fun `form body rule redacts a value that itself contains an equals sign`() {
        val rules = NetworkRedactionRules { bodyField("token") }
        val redacted = rules.redactAtCapture(request(headers = formHeaders, body = "token=a=b=c&x=1"))
        assertEquals("token=$REDACTED_PLACEHOLDER&x=1", redacted.body)
    }

    @Test
    fun `form content type with a charset parameter is still treated as a form body`() {
        val rules = NetworkRedactionRules { bodyField("password") }
        val redacted = rules.redactAtCapture(
            request(
                headers = mapOf("content-type" to listOf("application/x-www-form-urlencoded; charset=UTF-8")),
                body = "password=pw",
            ),
        )
        assertEquals("password=$REDACTED_PLACEHOLDER", redacted.body)
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
            bodyField("access_token")
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
    fun `a truncated JSON body that names a redacted field never carries its value`() {
        val rules = NetworkRedactionRules { bodyField("password") }
        val redacted = rules.redactAtCapture(
            request(body = """{"user":"alice","password":"hunter2","items":[1,2,3"""),
        )
        assertFalse("hunter2" in redacted.body.orEmpty(), redacted.body)
    }

    @Test
    fun `a body of several JSON documents that names a redacted field never carries its value`() {
        val rules = NetworkRedactionRules { bodyField("password") }
        val redacted = rules.redactAtCapture(request(body = "{\"password\":\"first-secret\"}\n{\"password\":\"second-secret\"}"))
        assertFalse("secret" in redacted.body.orEmpty(), redacted.body)
    }

    @Test
    fun `a field name spelled with JSON escapes in a truncated body is still caught`() {
        val rules = NetworkRedactionRules { bodyField("password") }
        val redacted = rules.redactAtCapture(
            request(body = """{"pass\u0077ord":"hunter2","items":["""),
        )
        assertFalse("hunter2" in redacted.body.orEmpty(), redacted.body)
    }

    @Test
    fun `a field name holding a control-character escape in a truncated body is still caught`() {
        val rules = NetworkRedactionRules { bodyField("pass\tword") }
        val redacted = rules.redactAtCapture(
            request(body = """{"pass\tword":"hunter2","items":["""),
        )
        assertFalse("hunter2" in redacted.body.orEmpty(), redacted.body)
    }

    @Test
    fun `a malformed JSON body with a stray quote before a redacted field never carries its value`() {
        val rules = NetworkRedactionRules { bodyField("password") }
        val redacted = rules.redactAtCapture(request(body = """{"name":"5" screen","password":"hunter2"}"""))
        assertFalse("hunter2" in redacted.body.orEmpty(), redacted.body)
    }

    @Test
    fun `a truncated JSON body that names no redacted field is kept as captured`() {
        val rules = NetworkRedactionRules { bodyField("password") }
        val body = """{"user":"alice","items":[1,2,3"""
        assertEquals(body, rules.redactAtCapture(request(body = body)).body)
    }

    @Test
    fun `a markup body that only mentions a redacted field name is kept`() {
        val rules = NetworkRedactionRules { bodyField("password") }
        val body = """<form><input type="password" name="pw"></form>"""
        assertEquals(body, rules.redactAtCapture(request(body = body)).body)
    }

    @Test
    fun `a failure message quoting the request URL has its redacted query values hidden`() {
        val rules = NetworkRedactionRules { urlQueryParam("token") }
        val failure = HttpRequestFailure(txId = "tx-1", message = "Request timeout has expired [url=https://api.example.com/a?token=abc&page=2, request_timeout=1000 ms]", durationMs = 1000L)
        val redacted = rules.redactAtCapture(failure)
        assertFalse("abc" in redacted.message, redacted.message)
        assertTrue("page=2" in redacted.message, redacted.message)
    }

    @Test
    fun `a failure message hides a redacted query value whatever the scheme of the URL`() {
        val rules = NetworkRedactionRules { urlQueryParam("token") }
        listOf("ws", "wss", "HTTPS").forEach { scheme ->
            val message = "Connect timeout has expired [url=$scheme://api.example.com/socket?token=abc&page=2, connect_timeout=unknown ms]"
            val redacted = rules.redactAtCapture(HttpRequestFailure(txId = "tx-1", message = message, durationMs = 1000L))
            assertFalse("abc" in redacted.message, redacted.message)
            assertTrue("page=2" in redacted.message, redacted.message)
        }
    }

    @Test
    fun `a failure message hides a redacted query value in a URL quoted right after another`() {
        val rules = NetworkRedactionRules { urlQueryParam("token") }
        val message = """{"origin":"https://api.example.com/a?page=2","url":"https://api.example.com/a?token=abc"}"""
        val redacted = rules.redactAtCapture(HttpRequestFailure(txId = "tx-1", message = message, durationMs = 1000L))
        assertFalse("abc" in redacted.message, redacted.message)
        assertTrue("page=2" in redacted.message, redacted.message)
    }

    @Test
    fun `a redacted query value in a failure message that runs into another redacted name is hidden whole`() {
        val rules = NetworkRedactionRules { urlQueryParam("token", "sig") }
        val message = "Connect timeout has expired [url=wss://api.example.com/socket?token=abc?sig=def&page=2, connect_timeout=unknown ms]"
        val redacted = rules.redactAtCapture(HttpRequestFailure(txId = "tx-1", message = message, durationMs = 1000L))
        assertFalse("abc" in redacted.message || "def" in redacted.message, redacted.message)
        assertTrue("page=2" in redacted.message, redacted.message)
    }

    @Test
    fun `a failure message hides a redacted query value whose name the client percent-encoded`() {
        val rules = NetworkRedactionRules { urlQueryParam("user[api_key]") }
        val message = "Request timeout has expired [url=https://api.example.com/a?user%5Bapi_key%5D=secret-value&page=2, request_timeout=1000 ms]"
        val redacted = rules.redactAtCapture(HttpRequestFailure(txId = "tx-1", message = message, durationMs = 1000L))
        assertFalse("secret-value" in redacted.message, redacted.message)
        assertTrue("page=2" in redacted.message, redacted.message)
    }

    @Test
    fun `a query parameter whose name is percent-encoded is still redacted`() {
        val rules = NetworkRedactionRules { urlQueryParam("token") }
        val redacted = rules.redactAtCapture(request(url = "https://api.example.com/a?tok%65n=abc&page=2"))
        assertFalse("abc" in redacted.url, redacted.url)
    }

    @Test
    fun `empty rules return the instance unchanged`() {
        val original = request(headers = mapOf("Authorization" to listOf("Bearer secret")))
        assertSame(original, NetworkRedactionRules.None.redactAtCapture(original))
    }
}
