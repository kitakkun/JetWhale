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
 * with the VIEW action, at least one scheme and no MIME type. Android matches a filter that names a
 * MIME type only against an intent that carries one, and a link carries none.
 *
 * The `<data>` elements of a `<uri-relative-filter-group>` are not the filter's own: Android 14 and
 * lower skip the group, and Android 15 matches it as an alternative to the filter's paths, with query
 * and fragment conditions that a declaration does not carry. They are left out.
 *
 * @param apiLevel The Android API level the manifest is read on. Path attributes newer than it are
 *   left out, as the platform ignores them.
 * @param verificationOf The platform's App Links verification state for a host, or null.
 */
internal fun declaredDeepLinksOf(
    packageName: String,
    events: Sequence<ManifestEvent>,
    apiLevel: Int,
    verificationOf: (host: String) -> String?,
): List<DeclaredDeepLink> {
    val pathAttributes = PATH_ATTRIBUTES.filter { it.sinceApiLevel <= apiLevel }
    val links = mutableListOf<DeclaredDeepLink>()
    var component: String? = null
    var filter: FilterBuilder? = null
    for (event in events) {
        when (event) {
            is ManifestEvent.Start -> when (event.tag) {
                "activity", "activity-alias" -> component = event.attributes["name"]?.let { componentName(packageName, it) }
                "intent-filter" -> filter = component?.let { FilterBuilder(handler = it, autoVerify = event.attributes["autoVerify"] == "true", pathAttributes = pathAttributes) }
                "action" -> event.attributes["name"]?.let { filter?.actions?.add(it) }
                "category" -> event.attributes["name"]?.let { filter?.categories?.add(it) }
                "uri-relative-filter-group" -> filter?.inRelativeFilterGroup = true
                "data" -> filter?.addData(event.attributes)
            }

            is ManifestEvent.End -> when (event.tag) {
                "activity", "activity-alias" -> component = null

                "intent-filter" -> {
                    filter?.takeIf(FilterBuilder::isDeepLink)?.let { links += it.build(verificationOf) }
                    filter = null
                }

                "uri-relative-filter-group" -> filter?.inRelativeFilterGroup = false
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

private class FilterBuilder(
    private val handler: String,
    private val autoVerify: Boolean,
    private val pathAttributes: List<PathAttribute>,
) {
    val actions = mutableListOf<String>()
    val categories = mutableListOf<String>()
    private val schemes = mutableListOf<String>()
    private val hosts = mutableListOf<Pair<String, String?>>()
    private val paths = mutableListOf<PathMatcher>()
    private var declaresMimeType = false
    var inRelativeFilterGroup = false

    fun addData(attributes: Map<String, String>) {
        if (inRelativeFilterGroup) return
        attributes["scheme"]?.let(schemes::add)
        attributes["host"]?.let { hosts += it to attributes["port"] }
        pathAttributes.forEach { attribute -> attributes[attribute.name]?.let { paths += PathMatcher(attribute.kind, it) } }
        if ("mimeType" in attributes) declaresMimeType = true
    }

    val isDeepLink: Boolean get() = ACTION_VIEW in actions && schemes.isNotEmpty() && !declaresMimeType

    fun build(verificationOf: (String) -> String?): DeclaredDeepLink = DeclaredDeepLink(
        handler = handler,
        schemes = schemes.distinct(),
        hosts = hosts.distinct().map { (host, port) -> DeepLinkHost(host = host, port = port, verification = verificationOf(host)) },
        paths = paths.distinct(),
        browsable = CATEGORY_BROWSABLE in categories,
        autoVerify = autoVerify,
    )
}

private class PathAttribute(val name: String, val kind: PathMatchKind, val sinceApiLevel: Int)

private val PATH_ATTRIBUTES: List<PathAttribute> = listOf(
    PathAttribute("path", PathMatchKind.Exact, sinceApiLevel = 1),
    PathAttribute("pathPrefix", PathMatchKind.Prefix, sinceApiLevel = 1),
    PathAttribute("pathSuffix", PathMatchKind.Suffix, sinceApiLevel = 31),
    PathAttribute("pathPattern", PathMatchKind.Pattern, sinceApiLevel = 1),
    PathAttribute("pathAdvancedPattern", PathMatchKind.AdvancedPattern, sinceApiLevel = 31),
)
