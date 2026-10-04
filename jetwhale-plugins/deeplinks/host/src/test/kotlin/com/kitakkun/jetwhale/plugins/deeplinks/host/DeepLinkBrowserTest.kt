package com.kitakkun.jetwhale.plugins.deeplinks.host

import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeclaredDeepLink
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkBrowserTest {
    private val demoLink = DeclaredDeepLink(
        handler = "com.example.app.MainActivity",
        schemes = listOf("demo"),
        hosts = emptyList(),
        paths = emptyList(),
        browsable = true,
        autoVerify = false,
    )
    private val itemTemplate = DeepLinkTemplate(name = "Item", template = "demo://item/{id}", description = null)
    private val client = FakeDeepLinkClient(declared = listOf(demoLink), templates = listOf(itemTemplate))

    // Unconfined runs a launched call in place until it suspends, and the fake never suspends, so
    // every call has finished by the time launch returns.
    private val browser = DeepLinkBrowser(client, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `a template draft becomes a link once its parameters are filled`() {
        runBlocking { browser.load() }
        browser.startFrom(itemTemplate)
        assertNull(browser.draft.url)

        browser.editParameter("id", "42")

        assertEquals("demo://item/42", browser.draft.url)
        assertEquals(listOf(demoLink), browser.draft.matches)
    }

    @Test
    fun `editing the link by hand leaves the template`() {
        runBlocking { browser.load() }
        browser.startFrom(itemTemplate)

        browser.editUrl("demo://settings")

        assertNull(browser.template)
        assertEquals("demo://settings", browser.draft.url)
    }

    @Test
    fun `opened links go to the front of the history`() {
        runBlocking { browser.load() }

        browser.open("demo://a")
        browser.open("https://elsewhere.example/")

        assertEquals(listOf("https://elsewhere.example/" to false, "demo://a" to true), browser.history.map { it.url to it.result.opened })
    }
}
