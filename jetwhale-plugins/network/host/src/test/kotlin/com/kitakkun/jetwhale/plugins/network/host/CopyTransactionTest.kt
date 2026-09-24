package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.plugins.network.protocol.BodyEncoding
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CopyTransactionTest {

    @Test
    fun `leaves the method implicit for a GET without a body`() {
        val command = buildCurlCommand(request())
        assertFalse("-X" in command)
        assertTrue(command.startsWith("curl --globoff"))
    }

    private fun request(
        method: String = "GET",
        url: String = "https://example.com/items",
        headers: Map<String, List<String>> = emptyMap(),
        body: String? = null,
        bodyTruncated: Boolean = false,
        bodyEncoding: BodyEncoding = BodyEncoding.TEXT,
    ) = CapturedHttpRequest(
        txId = "tx",
        method = method,
        url = url,
        headers = headers,
        body = body,
        bodyTruncated = bodyTruncated,
        bodyEncoding = bodyEncoding,
        timestampMs = 0L,
    )

    @Test
    fun `states the method explicitly for a GET that carries a body`() {
        val command = buildCurlCommand(request(body = """{"q":1}"""))
        assertTrue("-X GET" in command)
    }

    @Test
    fun `omits a binary body and says so in a leading note`() {
        val command = buildCurlCommand(
            request(
                method = "POST",
                headers = mapOf("Content-Type" to listOf("image/png")),
                body = "iVBORw0KGgo=",
                bodyEncoding = BodyEncoding.BASE64,
            ),
        )
        // The Base64 capture must never reach the command line: it is neither the original bytes
        // nor something a shell can send.
        assertFalse("iVBORw0KGgo=" in command)
        assertFalse("--data-raw" in command)
        assertTrue(command.startsWith("# NOTE: request body was captured as binary (image/png)"))
    }

    @Test
    fun `sends the body with data-raw so curl does not expand it`() {
        val command = buildCurlCommand(request(method = "POST", body = "@payload.json"))
        assertTrue("--data-raw '@payload.json'" in command)
        assertFalse("--data '" in command)
    }

    @Test
    fun `uppercases a non-GET method and states it explicitly`() {
        val command = buildCurlCommand(request(method = "delete"))
        assertTrue("-X DELETE" in command)
    }

    @Test
    fun `drops the Content-Length header and keeps the others`() {
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
    fun `escapes single quotes inside the body`() {
        val command = buildCurlCommand(request(method = "POST", body = "it's"))
        assertTrue("""--data-raw 'it'\''s'""" in command)
    }

    @Test
    fun `omits an uncaptured body and says so in a leading note`() {
        val command = buildCurlCommand(request(method = "POST", body = "<streaming request body>"))
        assertFalse("--data-raw" in command)
        assertTrue("-X POST" in command)
        assertTrue(command.startsWith("# NOTE: request body was not captured (<streaming request body>); the command omits it\n"))
    }

    @Test
    fun `treats a content-type placeholder as an uncaptured body`() {
        val command = buildCurlCommand(request(method = "GET", body = "<application/json>"))
        assertFalse("--data-raw" in command)
        assertFalse("-X" in command)
        assertTrue(command.startsWith("# NOTE: request body was not captured"))
    }

    @Test
    fun `sends an XML body instead of mistaking it for a placeholder`() {
        val command = buildCurlCommand(request(method = "POST", body = "<a><b/></a>"))
        assertTrue("--data-raw '<a><b/></a>'" in command)
        assertFalse("# NOTE" in command)
    }

    @Test
    fun `flags a truncated body with a leading note`() {
        val command = buildCurlCommand(request(method = "POST", body = "partial", bodyTruncated = true))
        assertTrue(command.startsWith("# NOTE: request body was truncated at capture time\n"))
    }

    @Test
    fun `joins the lines as shell continuations`() {
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
