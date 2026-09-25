package com.kitakkun.jetwhale.plugins.deeplinks.host

import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeclaredDeepLink
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkHost
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.PathMatchKind
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.PathMatcher
import java.net.URI
import java.net.URISyntaxException

/**
 * The declarations [url] matches, following Android's rules: the scheme must be declared; when the
 * declaration names hosts the host must be one of them (a leading `*` matches subdomains, not the
 * domain itself) on its port when one is declared; when it names paths, one of them must match the
 * decoded path.
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
    // Android matches paths after decoding, so /item/%66oo is /item/foo.
    val path = uri.path.orEmpty()
    return declared.filter { link ->
        scheme in link.schemes.map(String::lowercase) &&
            (link.hosts.isEmpty() || (host != null && link.hosts.any { hostMatches(it.host.lowercase(), host) && (it.port == null || it.port == uri.port.toString()) })) &&
            (link.paths.isEmpty() || link.paths.any { pathMatches(it, path) })
    }
}

private fun hostMatches(declared: String, host: String): Boolean = when {
    declared == "*" -> true

    // As Android's AuthorityEntry: the text after `*` must end the host, so `*.example.com` does not
    // match example.com itself.
    declared.startsWith("*") -> host.endsWith(declared.removePrefix("*"))

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
 * A link that [link] matches, to start editing from: its first scheme, a concrete host for its
 * first host, and a path for the first path matcher a sample can be built for. Null when no
 * matching sample can be built, e.g. for an advanced pattern that starts with a character class.
 */
internal fun sampleUrlOf(link: DeclaredDeepLink): String? {
    val scheme = link.schemes.first()
    // A declaration without hosts accepts any, but a link still needs one to be a link.
    val host = link.hosts.firstOrNull()?.let { it.concreteHost() + (it.port?.let { port -> ":$port" } ?: "") } ?: "example"
    val paths = if (link.paths.isEmpty()) listOf("") else link.paths.mapNotNull(::samplePathOf)
    // Every candidate is checked against the declaration itself, so a sample is never shown that
    // the declaration rejects.
    return paths.map { "$scheme://$host$it" }.firstOrNull { candidate ->
        try {
            link in declarationsMatching(candidate, listOf(link))
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}

// `*` matches any host and `*.example.com` only its subdomains, so neither can stand for itself.
private fun DeepLinkHost.concreteHost(): String = when {
    host == "*" -> "example.com"
    host.startsWith("*.") -> "www" + host.removePrefix("*")
    host.startsWith("*") -> host.removePrefix("*")
    else -> host
}

private fun samplePathOf(matcher: PathMatcher): String? = when (matcher.kind) {
    PathMatchKind.Exact, PathMatchKind.Prefix -> matcher.value

    PathMatchKind.Suffix -> "/" + matcher.value.removePrefix("/")

    PathMatchKind.Pattern -> sampleOfGlob(matcher.value)

    // A regular expression has no general sample; its literal start is tried and kept only if it matches.
    PathMatchKind.AdvancedPattern -> matcher.value.takeWhile { it.isLetterOrDigit() || it in "/-_~" }.takeIf { pathMatches(matcher, it) }
}

/**
 * A path that Android's `pathPattern` [pattern] matches: every repeated character is taken zero
 * times, every other `.` becomes a letter, and literals are kept.
 */
private fun sampleOfGlob(pattern: String): String {
    val sample = StringBuilder()
    var index = 0
    while (index < pattern.length) {
        val char = pattern[index]
        val escaped = char == '\\' && index + 1 < pattern.length
        val literal = if (escaped) pattern[index + 1] else char
        val width = if (escaped) 2 else 1
        when {
            pattern.getOrNull(index + width) == '*' -> index += width + 1
            !escaped && char == '.' -> sample.append('x').also { index++ }
            else -> sample.append(literal).also { index += width }
        }
    }
    return sample.toString()
}
