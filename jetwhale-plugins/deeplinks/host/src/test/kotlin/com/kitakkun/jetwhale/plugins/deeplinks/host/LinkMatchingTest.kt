package com.kitakkun.jetwhale.plugins.deeplinks.host

import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeclaredDeepLink
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkHost
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.PathMatchKind
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.PathMatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkMatchingTest {
    private val itemLink = declared(schemes = listOf("https"), hosts = listOf("example.com"), paths = listOf(PathMatcher(PathMatchKind.Prefix, "/item/")))
    private val customScheme = declared(schemes = listOf("demo"), hosts = emptyList(), paths = emptyList())

    @Test
    fun `a link matches when its scheme host and path are all declared`() {
        assertEquals(listOf(itemLink), declarationsMatching("https://example.com/item/42", listOf(itemLink, customScheme)))
    }

    @Test
    fun `a link on another host or path matches nothing`() {
        assertEquals(emptyList(), declarationsMatching("https://example.org/item/42", listOf(itemLink)))
        assertEquals(emptyList(), declarationsMatching("https://example.com/cart", listOf(itemLink)))
    }

    @Test
    fun `a declaration without hosts or paths accepts any`() {
        assertEquals(listOf(customScheme), declarationsMatching("demo://item/42?highlight=true", listOf(customScheme)))
    }

    @Test
    fun `a wildcard host matches its subdomains`() {
        val wildcard = declared(schemes = listOf("https"), hosts = listOf("*.example.com"), paths = emptyList())

        assertEquals(listOf(wildcard), declarationsMatching("https://shop.example.com/", listOf(wildcard)))
        assertEquals(emptyList(), declarationsMatching("https://example.org/", listOf(wildcard)))
        assertEquals(emptyList(), declarationsMatching("https://example.com/", listOf(wildcard)))
    }

    @Test
    fun `a declared port has to match`() {
        val onPort = DeclaredDeepLink(
            handler = "com.example.app.MainActivity",
            schemes = listOf("https"),
            hosts = listOf(DeepLinkHost(host = "example.com", port = "8443", verification = null)),
            paths = emptyList(),
            browsable = true,
            autoVerify = false,
        )

        assertEquals(listOf(onPort), declarationsMatching("https://example.com:8443/", listOf(onPort)))
        assertEquals(emptyList(), declarationsMatching("https://example.com/", listOf(onPort)))
    }

    @Test
    fun `paths are matched after decoding`() {
        assertEquals(listOf(itemLink), declarationsMatching("https://example.com/%69tem/42", listOf(itemLink)))
    }

    @Test
    fun `a sample link from an advanced pattern is a valid link`() {
        val advanced = declared(schemes = listOf("https"), hosts = listOf("example.com"), paths = listOf(PathMatcher(PathMatchKind.AdvancedPattern, "/item/[0-9]+")))

        assertEquals("https://example.com/item/", sampleUrlOf(advanced))
        declarationsMatching(sampleUrlOf(advanced), listOf(advanced))
    }

    @Test
    fun `a pathPattern follows Android's glob rules`() {
        val pattern = PathMatcher(PathMatchKind.Pattern, "/shop/.*/detail")

        assertTrue(pathMatches(pattern, "/shop/shoes/detail"))
        assertFalse(pathMatches(pattern, "/shop/shoes/list"))
        // `*` repeats only the character before it, so `a*` is not "a followed by anything".
        assertTrue(pathMatches(PathMatcher(PathMatchKind.Pattern, "/a*b"), "/aaab"))
        assertFalse(pathMatches(PathMatcher(PathMatchKind.Pattern, "/a*b"), "/axb"))
    }

    @Test
    fun `a link without a scheme is refused`() {
        assertFailsWith<IllegalArgumentException> { declarationsMatching("example.com/item", listOf(itemLink)) }
    }

    @Test
    fun `a sample link starts from the first scheme host and path`() {
        assertEquals("https://example.com/item/", sampleUrlOf(itemLink))
        assertEquals("demo://", sampleUrlOf(customScheme))
    }
}

private fun declared(schemes: List<String>, hosts: List<String>, paths: List<PathMatcher>): DeclaredDeepLink = DeclaredDeepLink(
    handler = "com.example.app.MainActivity",
    schemes = schemes,
    hosts = hosts.map { DeepLinkHost(host = it, port = null, verification = null) },
    paths = paths,
    browsable = true,
    autoVerify = false,
)
