package com.kitakkun.jetwhale.protocol.gradle

import kotlin.test.Test
import kotlin.test.assertEquals

class WireSchemaClassifierTest {
    @Test
    fun `adding an optional field is additive`() {
        assertVerdicts(
            BASE.replace("  url: string\n", "  url: string\n  userAgent: string? = …\n"),
            additive("sample/request_sent.request.userAgent"),
        )
    }

    @Test
    fun `adding a required field is breaking`() {
        assertVerdicts(
            BASE.replace("  url: string\n", "  url: string\n  userAgent: string\n"),
            breaking("sample/request_sent.request.userAgent"),
        )
    }

    @Test
    fun `removing an optional field is additive`() {
        assertVerdicts(
            BASE.replace("  body: string? = …\n", ""),
            additive("sample/request_sent.request.body"),
        )
    }

    @Test
    fun `removing a required field is breaking`() {
        assertVerdicts(
            BASE.replace("  method: string\n", ""),
            breaking("sample/request_sent.request.method"),
        )
    }

    @Test
    fun `making a required field optional is additive`() {
        assertVerdicts(
            BASE.replace("  method: string\n", "  method: string = …\n"),
            additive("sample/request_sent.request.method"),
        )
    }

    @Test
    fun `making a required field optional under EncodeDefault NEVER is breaking`() {
        assertVerdicts(
            BASE.replace("  method: string\n", "  method: string = …, omitted at default\n"),
            breaking("sample/request_sent.request.method"),
        )
    }

    @Test
    fun `making an optional field required is breaking`() {
        assertVerdicts(
            BASE.replace("  encoding: sample.Encoding = …\n", "  encoding: sample.Encoding\n"),
            breaking("sample/request_sent.request.encoding"),
        )
    }

    @Test
    fun `changing a field's type is breaking`() {
        assertVerdicts(
            BASE.replace("  enabled: boolean\n", "  enabled: string\n"),
            breaking("sample/get_config (reply).enabled"),
        )
    }

    @Test
    fun `changing a type argument is breaking`() {
        assertVerdicts(
            BASE.replace("  headers: map<string, list<string>> = …\n", "  headers: map<string, list<int>> = …\n"),
            breaking("sample/request_sent.request.headers{value}[]"),
        )
    }

    @Test
    fun `making a field nullable is breaking`() {
        assertVerdicts(
            BASE.replace("  url: string\n", "  url: string?\n"),
            breaking("sample/request_sent.request.url"),
        )
    }

    @Test
    fun `making a field non-null is breaking`() {
        assertVerdicts(
            BASE.replace("  body: string? = …\n", "  body: string = …\n"),
            breaking("sample/request_sent.request.body"),
        )
    }

    @Test
    fun `renaming a required field's serial name is breaking`() {
        assertVerdicts(
            BASE.replace("  url: string\n", "  uri: string\n"),
            breaking("sample/request_sent.request.uri"),
            breaking("sample/request_sent.request.url"),
        )
    }

    @Test
    fun `renaming a required field's serial name stays breaking with JsonNames for the old one`() {
        assertVerdicts(
            BASE.replace("  url: string\n", "  uri: string | also read as url\n"),
            breaking("sample/request_sent.request.uri"),
            breaking("sample/request_sent.request.url"),
        )
    }

    @Test
    fun `renaming an optional field's serial name is additive`() {
        assertVerdicts(
            BASE.replace("  body: string? = …\n", "  content: string? = …\n"),
            additive("sample/request_sent.request.body"),
            additive("sample/request_sent.request.content"),
        )
    }

    @Test
    fun `adding an enum entry is breaking`() {
        assertVerdicts(
            BASE.replace("  BASE64\n", "  BASE64\n  HEX\n"),
            breaking("sample/request_sent.request.encoding"),
        )
    }

    @Test
    fun `removing an enum entry is breaking`() {
        assertVerdicts(
            BASE.replace("  BASE64\n", ""),
            breaking("sample/request_sent.request.encoding"),
        )
    }

    @Test
    fun `adding a sealed subtype is breaking`() {
        assertVerdicts(
            BASE.replace("  sample.Fixed\n", "  sample.Fixed\n  sample.Random\n") + "\nobject sample.Random\n",
            breaking("sample/set_delay.delay"),
        )
    }

    @Test
    fun `removing a sealed subtype is breaking`() {
        assertVerdicts(
            BASE.replace("  sample.None\n", "").replace("\nobject sample.None\n", ""),
            breaking("sample/set_delay.delay"),
        )
    }

    @Test
    fun `changing the class discriminator is breaking`() {
        assertVerdicts(
            BASE.replace("sealed sample.Delay by type\n", "sealed sample.Delay by kind\n"),
            breaking("sample/set_delay.delay"),
        )
    }

    @Test
    fun `a change inside a sealed subtype is classified at the subtype's path`() {
        assertVerdicts(
            BASE.replace("  millis: long\n", "  millis: int\n"),
            breaking("sample/set_delay.delay<sample.Fixed>.millis"),
        )
    }

    @Test
    fun `adding an event is additive`() {
        assertVerdicts(
            BASE.replace("event sample/request_sent\n", "event sample/request_sent\nevent sample/request_started\n") +
                "\nobject sample/request_started\n",
            additive("sample/request_started"),
        )
    }

    @Test
    fun `adding a request is breaking`() {
        assertVerdicts(
            BASE.replace("event sample/request_sent\n", "event sample/request_sent\nrequest sample/reset -> sample/config\n") +
                "\nobject sample/reset\n",
            breaking("sample/reset"),
        )
    }

    @Test
    fun `removing an event is additive`() {
        assertVerdicts(
            BASE.replace("event sample/request_sent\n", ""),
            additive("sample/request_sent"),
        )
    }

    @Test
    fun `changing an event's serial name is additive`() {
        assertVerdicts(
            BASE.replace("sample/request_sent", "sample/request_done"),
            additive("sample/request_done"),
            additive("sample/request_sent"),
        )
    }

    @Test
    fun `removing a request is breaking`() {
        assertVerdicts(
            BASE.replace("request sample/get_config -> sample/config\n", ""),
            breaking("sample/get_config"),
        )
    }

    @Test
    fun `changing a request's serial name is breaking`() {
        assertVerdicts(
            BASE.replace("sample/get_config", "sample/fetch_config"),
            breaking("sample/fetch_config"),
            breaking("sample/get_config"),
        )
    }

    @Test
    fun `turning an event into a request is breaking`() {
        assertVerdicts(
            BASE.replace("event sample/request_sent\n", "request sample/request_sent -> sample/config\n"),
            breaking("sample/request_sent"),
        )
    }

    @Test
    fun `turning a request into an event is breaking`() {
        assertVerdicts(
            BASE.replace("request sample/get_config -> sample/config\n", "event sample/get_config\n"),
            breaking("sample/get_config"),
        )
    }

    @Test
    fun `changing a reply type is breaking`() {
        assertVerdicts(
            BASE.replace("request sample/get_config -> sample/config\n", "request sample/get_config -> boolean\n"),
            breaking("sample/get_config (reply)"),
        )
    }

    @Test
    fun `renaming a class the wire does not name changes the dump but not the wire`() {
        val next = BASE.replace("sample.HttpRequest", "sample.CapturedHttpRequest")
        assertEquals(emptyList(), WireSchemaClassifier(WireSchema.parse(BASE), WireSchema.parse(next)).classifyChanges())
        assertEquals(false, WireSchema.parse(BASE) == WireSchema.parse(next))
    }

    @Test
    fun `reordering fields leaves the schema as it was`() {
        val reordered = BASE.replace("  method: string\n  url: string\n", "  url: string\n  method: string\n")
        assertEquals(WireSchema.parse(BASE), WireSchema.parse(reordered))
        assertEquals(WireSchema.parse(BASE).render(), WireSchema.parse(reordered).render())
    }

    @Test
    fun `network from 1_0_0-alpha11 to 1_0_0-alpha12 breaks at the RedactionTarget rename`() {
        val alpha11 = WireSchema.parse(resourceText("history/network-1.0.0-alpha11.wire"))
        val alpha12 = WireSchema.parse(resourceText("history/network-1.0.0-alpha12.wire"))

        val changes = WireSchemaClassifier(alpha11, alpha12).classifyChanges()

        assertEquals(
            listOf(
                "network/get_redaction_config (reply).mcpOnlyRules[].target: enum entry BODY_FIELD added; an older peer cannot decode it",
                "network/get_redaction_config (reply).mcpOnlyRules[].target: enum entry BODY_JSON_FIELD removed; an older peer may still send it",
            ),
            changes.filter { it.isBreaking }.map { "${it.path}: ${it.description}" },
        )
        assertEquals(
            listOf(
                "network/get_mock_config (reply).rules[].response.bodyEncoding",
                "network/request_sent.request.bodyEncoding",
                "network/response_received.response.bodyEncoding",
            ),
            changes.filterNot { it.isBreaking }.map { it.path },
        )
    }

    private fun assertVerdicts(next: String, vararg expected: Pair<Boolean, String>) {
        val changes = WireSchemaClassifier(WireSchema.parse(BASE), WireSchema.parse(next)).classifyChanges()
        assertEquals(expected.toList(), changes.map { it.isBreaking to it.path }, changes.joinToString("\n"))
    }

    private fun breaking(path: String): Pair<Boolean, String> = true to path

    private fun additive(path: String): Pair<Boolean, String> = false to path

    private fun resourceText(path: String): String = checkNotNull(javaClass.classLoader.getResource(path)) { path }.readText()

    private companion object {
        val BASE = """
            revision 1

            request sample/get_config -> sample/config
            event sample/request_sent
            request sample/set_delay -> sample/config

            sealed sample.Delay by type
              sample.Fixed
              sample.None

            enum sample.Encoding
              BASE64
              TEXT

            class sample.Fixed
              millis: long

            class sample.HttpRequest
              body: string? = …
              encoding: sample.Encoding = …
              headers: map<string, list<string>> = …
              method: string
              url: string

            object sample.None

            class sample/config
              enabled: boolean

            object sample/get_config

            class sample/request_sent
              request: sample.HttpRequest

            class sample/set_delay
              delay: sample.Delay

        """.trimIndent()
    }
}
