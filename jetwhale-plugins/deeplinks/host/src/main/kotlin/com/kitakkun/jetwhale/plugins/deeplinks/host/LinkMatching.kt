package com.kitakkun.jetwhale.plugins.deeplinks.host

import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeclaredDeepLink
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.PathMatchKind
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.PathMatcher
import java.net.URI
import java.net.URISyntaxException

/**
 * The declarations [url] matches, following Android's rules: the scheme must be declared; when the
 * declaration names hosts the host must be one of them (a leading `*.` matches subdomains); when it
 * names paths, one of them must match.
 *
 * @throws IllegalArgumentException when [url] is not a URL with a scheme.
 */
internal fun declarationsMatching(url: String, declared: List<DeclaredDeepLink>): List<DeclaredDeepLink> {
    val uri = try {
        URI(url)
    } catch (e: URISyntaxException) {
        throw IllegalArgumentException("'$url' is not a valid URL: ${e.reason}", e)
    }
    val scheme = requireNotNull(uri.scheme) { "'$url' has no scheme" }.lowercase()
    val host = uri.host?.lowercase()
    // A custom scheme URL like myapp://item/42 parses its first segment as the host, as Android does.
    val path = uri.rawPath.orEmpty()
    return declared.filter { link ->
        scheme in link.schemes.map(String::lowercase) &&
            (link.hosts.isEmpty() || (host != null && link.hosts.any { hostMatches(it.host.lowercase(), host) })) &&
            (link.paths.isEmpty() || link.paths.any { pathMatches(it, path) })
    }
}

private fun hostMatches(declared: String, host: String): Boolean = when {
    declared == "*" -> true
    declared.startsWith("*.") -> host.endsWith(declared.removePrefix("*")) || host == declared.removePrefix("*.")
    else -> declared == host
}

internal fun pathMatches(matcher: PathMatcher, path: String): Boolean = when (matcher.kind) {
    PathMatchKind.Exact -> path == matcher.value
    PathMatchKind.Prefix -> path.startsWith(matcher.value)
    PathMatchKind.Suffix -> path.endsWith(matcher.value)
    PathMatchKind.Pattern -> simpleGlobToRegex(matcher.value).matches(path)
    // Close enough to a regular expression for checking a draft; the device has the final word.
    PathMatchKind.AdvancedPattern -> runCatching { Regex(matcher.value).matches(path) }.getOrDefault(false)
}

/**
 * Android's `pathPattern`: `.` is any character, `*` repeats the character before it zero or more
 * times, and `\` escapes the next character. Everything else is literal.
 */
private fun simpleGlobToRegex(pattern: String): Regex {
    val regex = StringBuilder()
    var index = 0
    while (index < pattern.length) {
        val char = pattern[index]
        when {
            char == '\\' && index + 1 < pattern.length -> {
                index++
                regex.appendLiteral(pattern[index])
            }

            char == '.' -> regex.append('.')

            char == '*' -> regex.append('*')

            else -> regex.appendLiteral(char)
        }
        index++
    }
    return Regex(regex.toString())
}

// One character at a time rather than Regex.escape's \Q...\E, so a following `*` repeats exactly it.
private fun StringBuilder.appendLiteral(char: Char) {
    if (!char.isLetterOrDigit()) append('\\')
    append(char)
}

/**
 * A link to start editing from for [link]: its first scheme, host and path, with a pattern's
 * wildcard left for the user to fill in.
 */
internal fun sampleUrlOf(link: DeclaredDeepLink): String {
    val scheme = link.schemes.first()
    val host = link.hosts.firstOrNull()?.let { host -> host.host.removePrefix("*.") + (host.port?.let { ":$it" } ?: "") }.orEmpty()
    val path = link.paths.firstOrNull()?.let { matcher ->
        when (matcher.kind) {
            PathMatchKind.Exact, PathMatchKind.Prefix -> matcher.value
            PathMatchKind.Suffix -> "/" + matcher.value.removePrefix("/")
            PathMatchKind.Pattern, PathMatchKind.AdvancedPattern -> matcher.value.substringBefore('.').substringBefore('*').ifEmpty { "/" }
        }
    }.orEmpty()
    return "$scheme://$host$path"
}
