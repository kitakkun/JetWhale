package com.kitakkun.jetwhale.plugins.deeplinks.host

import androidx.annotation.VisibleForTesting
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeclaredDeepLink
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkHost
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.PathMatchKind
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.PathMatcher
import java.io.ByteArrayOutputStream

/**
 * The declarations [url] matches, following Android's rules: the scheme must be declared, compared
 * case-sensitively; when the declaration names hosts the host must be one of them (a leading `*`
 * matches subdomains, not the domain itself) on its port when one is declared, and one of its paths,
 * if it names any, must match the decoded path. A declaration without hosts matches on the scheme
 * alone: Android ignores its paths.
 *
 * @throws IllegalArgumentException when [url] has no scheme.
 */
internal fun declarationsMatching(url: String, declared: List<DeclaredDeepLink>): List<DeclaredDeepLink> {
    val link = splitLink(url)
    return declared.filter(link::isMatchedBy)
}

/**
 * The parts of a link that intent filters match, with host and path decoded.
 *
 * @property host Null when the link has no `//` authority.
 * @property port -1 when the link names none.
 */
private class SplitLink(val scheme: String, val host: String?, val port: Int, val path: String) {
    fun isMatchedBy(declaration: DeclaredDeepLink): Boolean {
        if (scheme !in declaration.schemes) return false
        if (declaration.hosts.isEmpty()) return true
        val host = host ?: return false
        if (declaration.hosts.none { matchesHost(it, host) }) return false
        return declaration.paths.isEmpty() || declaration.paths.any { pathMatches(it, path) }
    }

    private fun matchesHost(declared: DeepLinkHost, host: String): Boolean {
        val nameMatches = if (declared.host.startsWith("*")) {
            host.endsWith(declared.host.removePrefix("*"), ignoreCase = true)
        } else {
            host.equals(declared.host, ignoreCase = true)
        }
        val declaredPort = declared.port ?: return nameMatches
        return nameMatches && declaredPort.toIntOrNull() == port
    }
}

/**
 * Splits [url] the way `android.net.Uri.parse` does, which takes any character and any host a manifest
 * can declare. `java.net.URI` gives no host for one like `item_detail` and rejects a space or `{` in a
 * query, both of which Android routes.
 */
private fun splitLink(url: String): SplitLink {
    val schemeEnd = url.indexOf(':')
    require(schemeEnd > 0) { "'$url' has no scheme" }
    val scheme = url.substring(0, schemeEnd)
    val hierarchicalPart = url.substring(schemeEnd + 1).takeIf { it.startsWith("//") }?.substring(2)
        ?: return SplitLink(scheme = scheme, host = null, port = -1, path = "")
    val authorityEnd = hierarchicalPart.indexOfFirst { it in "/\\?#" }.takeIf { it >= 0 } ?: hierarchicalPart.length
    val hostAndPort = hierarchicalPart.substring(0, authorityEnd).substringAfterLast('@')
    val lastNonDigit = hostAndPort.indexOfLast { it !in '0'..'9' }
    val hasPort = lastNonDigit >= 0 && hostAndPort[lastNonDigit] == ':'
    return SplitLink(
        scheme = scheme,
        host = percentDecoded(if (hasPort) hostAndPort.substring(0, lastNonDigit) else hostAndPort),
        port = if (hasPort) hostAndPort.substring(lastNonDigit + 1).toIntOrNull() ?: -1 else -1,
        path = percentDecoded(hierarchicalPart.substring(authorityEnd).takeWhile { it != '?' && it != '#' }),
    )
}

/** Decodes `%XX` escapes as UTF-8 and leaves `+` and a malformed escape as they are. */
private fun percentDecoded(encoded: String): String {
    if ('%' !in encoded) return encoded
    val decoded = StringBuilder()
    val escapedBytes = ByteArrayOutputStream()
    var index = 0
    while (index < encoded.length) {
        val high = encoded.getOrNull(index + 1)?.digitToIntOrNull(16)
        val low = encoded.getOrNull(index + 2)?.digitToIntOrNull(16)
        if (encoded[index] == '%' && high != null && low != null) {
            escapedBytes.write(high * 16 + low)
            index += 3
        } else {
            decoded.append(escapedBytes.toByteArray().decodeToString())
            escapedBytes.reset()
            decoded.append(encoded[index])
            index++
        }
    }
    return decoded.append(escapedBytes.toByteArray().decodeToString()).toString()
}

@VisibleForTesting
internal fun pathMatches(matcher: PathMatcher, path: String): Boolean = when (matcher.kind) {
    PathMatchKind.Exact -> path == matcher.value
    PathMatchKind.Prefix -> path.startsWith(matcher.value)
    PathMatchKind.Suffix -> path.endsWith(matcher.value)
    PathMatchKind.Pattern -> simpleGlobToRegex(matcher.value).matches(path)
    PathMatchKind.AdvancedPattern -> runCatching { Regex(matcher.value).matches(path) }.getOrDefault(false)
}

/**
 * Android's `pathPattern`: `.` is any character, a `*` after a character repeats it zero or more
 * times, and `\` escapes the next character. Everything else, including a `*` that follows no
 * character (a leading one, or the second of `**`), is literal.
 */
private fun simpleGlobToRegex(pattern: String): Regex {
    val regex = StringBuilder()
    var index = 0
    while (index < pattern.length) {
        val escaped = pattern[index] == '\\' && index + 1 < pattern.length
        val char = if (escaped) pattern[index + 1] else pattern[index]
        if (!escaped && char == '.') regex.append('.') else regex.appendLiteral(char)
        index += if (escaped) 2 else 1
        if (pattern.getOrNull(index) == '*') {
            regex.append('*')
            index++
        }
    }
    return Regex(regex.toString())
}

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
    val host = link.hosts.firstOrNull()?.let { it.concreteHost() + (it.port?.let { port -> ":$port" } ?: "") } ?: "example"
    val paths = if (link.paths.isEmpty()) listOf("") else link.paths.mapNotNull(::samplePathOf)
    return paths.map { "$scheme://$host$it" }.firstOrNull { candidate ->
        try {
            link in declarationsMatching(candidate, listOf(link))
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}

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
