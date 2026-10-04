package com.kitakkun.jetwhale.plugins.deeplinks.host

import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeclaredDeepLink
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkHost
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.PathMatchKind
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.PathMatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
        val onPort = httpsOn(host = "example.com", port = "8443")

        assertEquals(listOf(onPort), declarationsMatching("https://example.com:8443/", listOf(onPort)))
        assertEquals(listOf(onPort), declarationsMatching("https://user:secret@example.com:8443/", listOf(onPort)))
        assertEquals(emptyList(), declarationsMatching("https://example.com/", listOf(onPort)))
        assertEquals(emptyList(), declarationsMatching("https://example.com:443/", listOf(onPort)))
    }

    @Test
    fun `the colons of an IPv6 host are not taken for a port`() {
        val loopback = declared(schemes = listOf("https"), hosts = listOf("[::1]"), paths = emptyList())
        val loopbackOnPort = httpsOn(host = "[::1]", port = "8443")

        assertEquals(listOf(loopback), declarationsMatching("https://[::1]/", listOf(loopback, loopbackOnPort)))
        assertEquals(listOf(loopback, loopbackOnPort), declarationsMatching("https://[::1]:8443/", listOf(loopback, loopbackOnPort)))
    }

    @Test
    fun `a host with an underscore or a numeric last label is matched as Android reads it`() {
        val itemDetail = declared(schemes = listOf("myapp"), hosts = listOf("item_detail"), paths = emptyList())
        val numbered = declared(schemes = listOf("myapp"), hosts = listOf("item.42"), paths = emptyList())

        assertEquals(listOf(itemDetail), declarationsMatching("myapp://item_detail/42", listOf(itemDetail, numbered)))
        assertEquals(listOf(itemDetail), declarationsMatching("myapp://item%5Fdetail/42", listOf(itemDetail, numbered)))
        assertEquals(listOf(numbered), declarationsMatching("myapp://item.42", listOf(itemDetail, numbered)))
        assertEquals("myapp://item_detail", sampleUrlOf(itemDetail))
    }

    @Test
    fun `spaces braces quotes and pipes in a link do not stop it from matching`() {
        val search = declared(schemes = listOf("myapp"), hosts = listOf("search"), paths = emptyList())
        val pipePath = declared(schemes = listOf("myapp"), hosts = listOf("search"), paths = listOf(PathMatcher(PathMatchKind.Exact, "/red shoes|boots")))

        assertEquals(listOf(search), declarationsMatching("myapp://search?q=red shoes", listOf(search)))
        assertEquals(listOf(search), declarationsMatching("myapp://search?data={\"id\":1}", listOf(search)))
        assertEquals(listOf(search, pipePath), declarationsMatching("myapp://search/red shoes|boots?sort=price#top", listOf(search, pipePath)))
    }

    @Test
    fun `a scheme is compared case-sensitively`() {
        val mixedCase = declared(schemes = listOf("MyApp"), hosts = emptyList(), paths = emptyList())

        assertEquals(emptyList(), declarationsMatching("HTTPS://example.com/item/42", listOf(itemLink)))
        assertEquals(listOf(mixedCase), declarationsMatching("MyApp://item/42", listOf(mixedCase)))
        assertEquals(emptyList(), declarationsMatching("myapp://item/42", listOf(mixedCase)))
    }

    @Test
    fun `a declaration without hosts ignores its paths`() {
        val pathWithoutHost = declared(schemes = listOf("demo"), hosts = emptyList(), paths = listOf(PathMatcher(PathMatchKind.Exact, "/about")))

        assertEquals(listOf(pathWithoutHost), declarationsMatching("demo://item/42", listOf(pathWithoutHost)))
    }

    @Test
    fun `a link without an authority matches only declarations without hosts`() {
        assertEquals(listOf(customScheme), declarationsMatching("demo:item/42", listOf(customScheme)))
        assertEquals(emptyList(), declarationsMatching("https:example.com/item/42", listOf(itemLink)))
    }

    @Test
    fun `paths are matched after decoding`() {
        val nonAscii = declared(schemes = listOf("https"), hosts = listOf("example.com"), paths = listOf(PathMatcher(PathMatchKind.Prefix, "/商品/")))
        val plus = declared(schemes = listOf("https"), hosts = listOf("example.com"), paths = listOf(PathMatcher(PathMatchKind.Exact, "/a+b")))

        assertEquals(listOf(itemLink), declarationsMatching("https://example.com/%69tem/42", listOf(itemLink)))
        assertEquals(listOf(nonAscii), declarationsMatching("https://example.com/%E5%95%86%E5%93%81/1", listOf(nonAscii)))
        assertEquals(listOf(plus), declarationsMatching("https://example.com/a+b", listOf(plus)))
        assertEquals(listOf(plus), declarationsMatching("https://example.com/%61+b", listOf(plus)))
    }

    @Test
    fun `every sample link is matched by its own declaration`() {
        val hosts = listOf(emptyList(), listOf("example.com"), listOf("*.example.com"), listOf("*"))
        val paths = listOf(
            emptyList(),
            listOf(PathMatcher(PathMatchKind.Exact, "/about")),
            listOf(PathMatcher(PathMatchKind.Prefix, "/item/")),
            listOf(PathMatcher(PathMatchKind.Suffix, ".json")),
            listOf(PathMatcher(PathMatchKind.Pattern, "/shop/.*/detail")),
            listOf(PathMatcher(PathMatchKind.Pattern, "/a*b")),
            listOf(PathMatcher(PathMatchKind.Pattern, "/file\\.txt")),
            listOf(PathMatcher(PathMatchKind.AdvancedPattern, "/user/me")),
        )
        hosts.forEach { hostList ->
            paths.forEach { pathList ->
                val link = declared(schemes = listOf("https"), hosts = hostList, paths = pathList)
                val sample = assertNotNull(sampleUrlOf(link), "no sample for $link")
                assertEquals(listOf(link), declarationsMatching(sample, listOf(link)), "sample $sample for $link")
            }
        }
    }

    @Test
    fun `a wildcard host gets a concrete subdomain in its sample`() {
        val wildcard = declared(schemes = listOf("https"), hosts = listOf("*.example.com"), paths = emptyList())

        assertEquals("https://www.example.com", sampleUrlOf(wildcard))
    }

    @Test
    fun `a pattern sample takes repeated characters zero times`() {
        assertEquals("https://example.com/b", sampleUrlOf(declared(schemes = listOf("https"), hosts = listOf("example.com"), paths = listOf(PathMatcher(PathMatchKind.Pattern, "/a*b")))))
    }

    @Test
    fun `an advanced pattern with no literal match has no sample`() {
        val advanced = declared(schemes = listOf("https"), hosts = listOf("example.com"), paths = listOf(PathMatcher(PathMatchKind.AdvancedPattern, "/item/[0-9]+")))

        assertEquals(null, sampleUrlOf(advanced))
    }

    @Test
    fun `a pathPattern follows Android's glob rules`() {
        val pattern = PathMatcher(PathMatchKind.Pattern, "/shop/.*/detail")

        assertTrue(pathMatches(pattern, "/shop/shoes/detail"))
        assertFalse(pathMatches(pattern, "/shop/shoes/list"))
        assertTrue(pathMatches(PathMatcher(PathMatchKind.Pattern, "/a*b"), "/aaab"))
        assertFalse(pathMatches(PathMatcher(PathMatchKind.Pattern, "/a*b"), "/axb"))
    }

    @Test
    fun `a star that follows no character is literal in a pathPattern`() {
        val leadingStar = PathMatcher(PathMatchKind.Pattern, "*.pdf")

        assertTrue(pathMatches(leadingStar, "*.pdf"))
        assertFalse(pathMatches(leadingStar, "/doc.pdf"))
        assertTrue(pathMatches(PathMatcher(PathMatchKind.Pattern, "/a**"), "/aa*"))
        assertFalse(pathMatches(PathMatcher(PathMatchKind.Pattern, "/a**"), "/aa"))
    }

    @Test
    fun `a link without a scheme is refused`() {
        assertFailsWith<IllegalArgumentException> { declarationsMatching("example.com/item", listOf(itemLink)) }
    }

    @Test
    fun `a sample link starts from the first scheme host and path`() {
        assertEquals("https://example.com/item/", sampleUrlOf(itemLink))
        assertEquals("demo://example", sampleUrlOf(customScheme))
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

private fun httpsOn(host: String, port: String): DeclaredDeepLink = DeclaredDeepLink(
    handler = "com.example.app.MainActivity",
    schemes = listOf("https"),
    hosts = listOf(DeepLinkHost(host = host, port = port, verification = null)),
    paths = emptyList(),
    browsable = true,
    autoVerify = false,
)
