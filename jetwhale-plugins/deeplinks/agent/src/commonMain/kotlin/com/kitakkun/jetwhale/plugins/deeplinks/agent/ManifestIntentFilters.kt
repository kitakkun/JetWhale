package com.kitakkun.jetwhale.plugins.deeplinks.agent

import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeclaredDeepLink
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkHost
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.PathMatchKind
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.PathMatcher

/**
 * One element of an Android manifest as a parser walks it. The Android side reads the app's compiled
 * manifest into these; turning them into declarations needs nothing from Android, so it is tested on
 * every target.
 *
 * @property attributes The element's `android:` attributes by local name (`scheme`, `host`, ...), with
 *   resource references already resolved.
 */
internal sealed interface ManifestEvent {
    val tag: String

    data class Start(override val tag: String, val attributes: Map<String, String>) : ManifestEvent

    data class End(override val tag: String) : ManifestEvent
}

private const val ACTION_VIEW = "android.intent.action.VIEW"
private const val CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE"

/**
 * The deep links declared by the activities and activity aliases in [events]: every intent filter
 * with the VIEW action and at least one scheme.
 *
 * @param verificationOf The platform's App Links verification state for a host, or null.
 */
internal fun declaredDeepLinksOf(
    packageName: String,
    events: Sequence<ManifestEvent>,
    verificationOf: (host: String) -> String?,
): List<DeclaredDeepLink> {
    val links = mutableListOf<DeclaredDeepLink>()
    var component: String? = null
    var filter: FilterBuilder? = null
    for (event in events) {
        when (event) {
            is ManifestEvent.Start -> when (event.tag) {
                "activity", "activity-alias" -> component = event.attributes["name"]?.let { componentName(packageName, it) }
                "intent-filter" -> filter = component?.let { FilterBuilder(handler = it, autoVerify = event.attributes["autoVerify"] == "true") }
                "action" -> event.attributes["name"]?.let { filter?.actions?.add(it) }
                "category" -> event.attributes["name"]?.let { filter?.categories?.add(it) }
                "data" -> filter?.add(event.attributes)
            }

            is ManifestEvent.End -> when (event.tag) {
                "activity", "activity-alias" -> component = null

                "intent-filter" -> {
                    filter?.takeIf(FilterBuilder::isDeepLink)?.let { links += it.build(verificationOf) }
                    filter = null
                }
            }
        }
    }
    return links
}

/** Android's shorthand: `.Main` and `Main` are both relative to the package. */
private fun componentName(packageName: String, name: String): String = when {
    name.startsWith(".") -> packageName + name
    '.' !in name -> "$packageName.$name"
    else -> name
}

private class FilterBuilder(private val handler: String, private val autoVerify: Boolean) {
    val actions = mutableListOf<String>()
    val categories = mutableListOf<String>()
    private val schemes = mutableListOf<String>()
    private val hosts = mutableListOf<Pair<String, String?>>()
    private val paths = mutableListOf<PathMatcher>()

    // Android merges every <data> element of a filter, whichever attributes each one carries.
    fun add(attributes: Map<String, String>) {
        attributes["scheme"]?.let(schemes::add)
        attributes["host"]?.let { hosts += it to attributes["port"] }
        PATH_ATTRIBUTES.forEach { (attribute, kind) -> attributes[attribute]?.let { paths += PathMatcher(kind, it) } }
    }

    val isDeepLink: Boolean get() = ACTION_VIEW in actions && schemes.isNotEmpty()

    fun build(verificationOf: (String) -> String?): DeclaredDeepLink = DeclaredDeepLink(
        handler = handler,
        schemes = schemes.distinct(),
        hosts = hosts.distinct().map { (host, port) -> DeepLinkHost(host = host, port = port, verification = verificationOf(host)) },
        paths = paths.distinct(),
        browsable = CATEGORY_BROWSABLE in categories,
        autoVerify = autoVerify,
    )
}

private val PATH_ATTRIBUTES: List<Pair<String, PathMatchKind>> = listOf(
    "path" to PathMatchKind.Exact,
    "pathPrefix" to PathMatchKind.Prefix,
    "pathSuffix" to PathMatchKind.Suffix,
    "pathPattern" to PathMatchKind.Pattern,
    "pathAdvancedPattern" to PathMatchKind.AdvancedPattern,
)
