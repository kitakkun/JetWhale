package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CopyTransactionTest {

    private fun request(
        method: String = "GET",
        url: String = "https://example.com/items",
        headers: Map<String, List<String>> = emptyMap(),
        body: String? = null,
        bodyTruncated: Boolean = false,
    ) = CapturedHttpRequest(
        txId = "tx",
        method = method,
        url = url,
        headers = headers,
        body = body,
        bodyTruncated = bodyTruncated,
        timestampMs = 0L,
    )

    @Test
    fun getWithoutBody_omitsExplicitMethod() {
        val command = buildCurlCommand(request())
        assertFalse("-X" in command)
        assertTrue(command.startsWith("curl --globoff"))
    }

    @Test
    fun getWithBody_keepsMethodExplicit() {
        val command = buildCurlCommand(request(body = """{"q":1}"""))
        assertTrue("-X GET" in command)
    }

    @Test
    fun bodyUsesDataRaw() {
        val command = buildCurlCommand(request(method = "POST", body = "@payload.json"))
        assertTrue("--data-raw '@payload.json'" in command)
        assertFalse("--data '" in command)
    }

    @Test
    fun nonGetMethodIsUppercasedAndExplicit() {
        val command = buildCurlCommand(request(method = "delete"))
        assertTrue("-X DELETE" in command)
    }

    @Test
    fun contentLengthHeaderIsDropped() {
        val command = buildCurlCommand(
            request(
                method = "POST",
                headers = mapOf("Content-Length" to listOf("42"), "Accept" to listOf("application/json")),
                body = "{}",
            ),
        )
        assertFalse("Content-Length" in command)
        assertTrue("-H 'Accept: application/json'" in command)
    }

    @Test
    fun singleQuotesAreEscaped() {
        val command = buildCurlCommand(request(method = "POST", body = "it's"))
        assertTrue("""--data-raw 'it'\''s'""" in command)
    }

    @Test
    fun truncatedBodyIsFlaggedWithLeadingComment() {
        val command = buildCurlCommand(request(method = "POST", body = "partial", bodyTruncated = true))
        assertTrue(command.startsWith("# NOTE: request body was truncated at capture time\n"))
    }

    @Test
    fun linesAreJoinedAsShellContinuations() {
        val command = buildCurlCommand(request(method = "POST", body = "{}"))
        assertEquals(
            """
            curl --globoff \
              -X POST \
              'https://example.com/items' \
              --data-raw '{}'
            """.trimIndent(),
            command,
        )
    }
}
