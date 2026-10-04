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
    PathMatchKind.Pattern -> matchesGlobPattern(matcher.value, path)
    PathMatchKind.AdvancedPattern -> matchesAdvancedPattern(matcher.value, path)
}

/**
 * Android's `pathPattern`, ported from `PatternMatcher.matchGlobPattern` (android-36): `.` is any
 * character, a `*` repeats the character before it, and `\` escapes the next one. A repetition never
 * gives back what it took: `.*` stops at the first occurrence of the character after it, so
 * `.*\.pdf` does not match `report.v2.pdf`, and `c*` takes every `c` in a row.
 */
private fun matchesGlobPattern(pattern: String, match: String): Boolean {
    if (pattern.isEmpty()) return match.isEmpty()
    var ip = 0
    var im = 0
    var nextChar = pattern[0]
    while (ip < pattern.length && im < match.length) {
        var c = nextChar
        ip++
        nextChar = pattern.getOrElse(ip) { PATTERN_END }
        val escaped = c == '\\'
        if (escaped) {
            c = nextChar
            ip++
            nextChar = pattern.getOrElse(ip) { PATTERN_END }
        }
        if (nextChar == '*') {
            if (!escaped && c == '.') {
                if (ip >= pattern.length - 1) return true
                ip++
                nextChar = pattern[ip]
                if (nextChar == '\\') {
                    ip++
                    nextChar = pattern.getOrElse(ip) { PATTERN_END }
                }
                while (im < match.length && match[im] != nextChar) im++
                if (im == match.length) return false
                ip++
                nextChar = pattern.getOrElse(ip) { PATTERN_END }
                im++
            } else {
                while (im < match.length && match[im] == c) im++
                ip++
                nextChar = pattern.getOrElse(ip) { PATTERN_END }
            }
        } else {
            // Android does not consult the escape here either, so `\.` matches any character.
            if (c != '.' && match[im] != c) return false
            im++
        }
    }
    if (ip >= pattern.length && im >= match.length) return true
    return ip == pattern.length - 2 && pattern[ip] == '.' && pattern[ip + 1] == '*'
}

private const val PATTERN_END = '\u0000'

/**
 * Android's `pathAdvancedPattern`, ported from `PatternMatcher` (android-36): `.` is any character,
 * `[...]` a set with `a-z` ranges and `[^...]` its complement, `\` escapes the next character, and `*`,
 * `+`, `{n}`, `{n,}` and `{n,m}` repeat the token before them. Anything else, `(`, `|` and `?`
 * included, is literal. Each token takes as many characters as it can and never gives one back, so
 * `.*\.pdf` matches nothing. False for a pattern Android refuses, which no installed app declares.
 */
private fun matchesAdvancedPattern(pattern: String, match: String): Boolean {
    val tokens = AdvancedPatternParser(pattern).parse() ?: return false
    var im = 0
    for (token in tokens) {
        val repetition = token.repetition ?: 1..1
        var matched = 0
        while (matched < repetition.last && im + matched < match.length && token.matches(match[im + matched])) matched++
        if (matched < repetition.first) return false
        im += matched
    }
    return im >= match.length
}

/** @property repetition Null when no modifier follows the token, which then matches once. */
private class AdvancedToken(val repetition: IntRange?, val matches: (Char) -> Boolean)

/** Reads a `pathAdvancedPattern` into tokens the way `PatternMatcher.parseAndVerifyAdvancedPattern` does. */
private class AdvancedPatternParser(private val pattern: String) {
    private val tokens = mutableListOf<AdvancedToken>()
    private var ip = 0

    /** Null where Android refuses the pattern. */
    fun parse(): List<AdvancedToken>? {
        while (ip < pattern.length) {
            val accepted = when (val c = pattern[ip]) {
                '[' -> addSet()

                '{' -> repeatLast(countedRepetition() ?: return null)

                '*' -> repeatLast(0..Int.MAX_VALUE)

                '+' -> repeatLast(1..Int.MAX_VALUE)

                // A `}` that closes no range is dropped, not matched.
                '}' -> true

                '.' -> tokens.add(AdvancedToken(repetition = null) { true })

                '\\' -> addLiteral(pattern.getOrNull(++ip) ?: return null)

                else -> addLiteral(c)
            }
            if (!accepted) return null
            ip++
        }
        return tokens
    }

    private fun addLiteral(literal: Char): Boolean = tokens.add(AdvancedToken(repetition = null) { it == literal })

    /** Reads the set from its `[` to its `]`, with `a-z` ranges and a leading `^` for its complement. */
    private fun addSet(): Boolean {
        val inverse = (pattern.getOrNull(ip + 1) ?: return false) == '^'
        ip += if (inverse) 2 else 1
        val ranges = mutableListOf<CharRange>()
        while (pattern.getOrNull(ip) != ']') {
            val lower = setMember() ?: return false
            val isRange = ip + 2 < pattern.length && pattern[ip + 1] == '-' && pattern[ip + 2] != ']'
            if (isRange) ip += 2
            val upper = if (isRange) setMember() ?: return false else lower
            ranges += lower..upper
            ip++
        }
        if (ranges.isEmpty()) return false
        val matches: (Char) -> Boolean = if (inverse) { char -> ranges.none { char in it } } else { char -> ranges.any { char in it } }
        return tokens.add(AdvancedToken(repetition = null, matches = matches))
    }

    /** The set member at [ip] with its `\` escape removed, leaving [ip] on its last character. */
    private fun setMember(): Char? {
        val c = pattern.getOrNull(ip) ?: return null
        return if (c == '\\') pattern.getOrNull(++ip) else c
    }

    /** The `{n}`, `{n,}` or `{n,m}` at [ip], leaving [ip] on its `}`. */
    private fun countedRepetition(): IntRange? {
        val end = pattern.indexOf('}', ip + 1).takeIf { it >= 0 } ?: return null
        val bounds = pattern.substring(ip + 1, end)
        val comma = bounds.indexOf(',')
        val min = (if (comma < 0) bounds else bounds.substring(0, comma)).toIntOrNull() ?: return null
        val max = when {
            comma < 0 -> min
            comma == bounds.lastIndex -> Int.MAX_VALUE
            else -> bounds.substring(comma + 1).toIntOrNull() ?: return null
        }
        ip = end
        return min..max
    }

    /** False when there is no token to repeat, or a modifier already follows it. */
    private fun repeatLast(repetition: IntRange): Boolean {
        val last = tokens.lastOrNull()?.takeIf { it.repetition == null } ?: return false
        tokens[tokens.lastIndex] = AdvancedToken(repetition, last.matches)
        return true
    }
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
 * A path to try against the `pathPattern` [pattern]: every repeated character is taken zero times,
 * every other `.` becomes a letter, and literals are kept. Some patterns, like `a*a`, match no path at
 * all, so the caller checks it.
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
