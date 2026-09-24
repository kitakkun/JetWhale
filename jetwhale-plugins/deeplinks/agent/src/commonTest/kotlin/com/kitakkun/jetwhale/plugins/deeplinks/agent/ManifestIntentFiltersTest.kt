package com.kitakkun.jetwhale.plugins.deeplinks.agent

import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeclaredDeepLink
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkHost
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.PathMatchKind
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.PathMatcher
import kotlin.test.Test
import kotlin.test.assertEquals

class ManifestIntentFiltersTest {
    @Test
    fun `a view filter becomes a declaration with every data element merged`() {
        val events = manifest {
            element("activity", "name" to ".MainActivity") {
                element("intent-filter", "autoVerify" to "true") {
                    element("action", "name" to "android.intent.action.VIEW")
                    element("category", "name" to "android.intent.category.DEFAULT")
                    element("category", "name" to "android.intent.category.BROWSABLE")
                    element("data", "scheme" to "https")
                    element("data", "scheme" to "http", "host" to "example.com")
                    element("data", "pathPrefix" to "/item/")
                    element("data", "pathPattern" to "/shop/.*")
                }
            }
        }

        val links = declaredDeepLinksOf("com.example.app", events, verificationOf = { if (it == "example.com") "verified" else null })

        assertEquals(
            listOf(
                DeclaredDeepLink(
                    handler = "com.example.app.MainActivity",
                    schemes = listOf("https", "http"),
                    hosts = listOf(DeepLinkHost(host = "example.com", port = null, verification = "verified")),
                    paths = listOf(PathMatcher(PathMatchKind.Prefix, "/item/"), PathMatcher(PathMatchKind.Pattern, "/shop/.*")),
                    browsable = true,
                    autoVerify = true,
                ),
            ),
            links,
        )
    }

    @Test
    fun `a filter without the view action or without a scheme is not a deep link`() {
        val events = manifest {
            element("activity", "name" to "com.example.app.Launcher") {
                element("intent-filter") {
                    element("action", "name" to "android.intent.action.MAIN")
                    element("category", "name" to "android.intent.category.LAUNCHER")
                }
                element("intent-filter") {
                    element("action", "name" to "android.intent.action.VIEW")
                    element("data", "mimeType" to "image/*")
                }
            }
        }

        assertEquals(emptyList(), declaredDeepLinksOf("com.example.app", events, verificationOf = { null }))
    }

    @Test
    fun `each filter belongs to the activity or alias around it`() {
        val events = manifest {
            element("activity", "name" to "Main") {
                viewFilter("app")
            }
            element("activity-alias", "name" to ".Alias") {
                viewFilter("alias")
            }
        }

        val links = declaredDeepLinksOf("com.example.app", events, verificationOf = { null })

        assertEquals(listOf("com.example.app.Main" to "app", "com.example.app.Alias" to "alias"), links.map { it.handler to it.schemes.single() })
    }

    @Test
    fun `a filter that is not browsable is reported as such`() {
        val events = manifest {
            element("activity", "name" to ".Main") { viewFilter("internal") }
        }

        assertEquals(false, declaredDeepLinksOf("com.example.app", events, verificationOf = { null }).single().browsable)
    }
}

private class ManifestBuilder {
    val events = mutableListOf<ManifestEvent>()

    fun element(tag: String, vararg attributes: Pair<String, String>, children: ManifestBuilder.() -> Unit = {}) {
        events += ManifestEvent.Start(tag, attributes.toMap())
        children()
        events += ManifestEvent.End(tag)
    }

    fun viewFilter(scheme: String) = element("intent-filter") {
        element("action", "name" to "android.intent.action.VIEW")
        element("data", "scheme" to scheme)
    }
}

private fun manifest(build: ManifestBuilder.() -> Unit): Sequence<ManifestEvent> = ManifestBuilder().apply {
    element("manifest") { element("application", children = build) }
}.events.asSequence()
