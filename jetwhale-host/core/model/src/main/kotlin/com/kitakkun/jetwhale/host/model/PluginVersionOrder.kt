package com.kitakkun.jetwhale.host.model

/**
 * Orders plugin versions from oldest to newest.
 *
 * - The release part, before any `-`, compares numerically dot by dot (`1.10.0` after `1.9.2`). A
 *   component that is not a number counts as 0, and a missing one as 0.
 * - A release comes after its pre-releases (`1.0.0-alpha02` before `1.0.0`).
 * - Pre-releases compare their numbers as numbers (`alpha9` before `alpha10`) and their words
 *   alphabetically, ignoring case (`alpha10` before `beta1`).
 * - Versions that still rank the same, such as `1.2` and `1.2.0`, are ordered by their text, so the
 *   newest of a set never depends on the order the set was built in.
 */
object PluginVersionOrder : Comparator<String> {
    private val preReleaseSegment = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        val byRelease = compareReleases(a, b)
        if (byRelease != 0) return byRelease
        val byPreRelease = comparePreReleases(a.preRelease(), b.preRelease())
        if (byPreRelease != 0) return byPreRelease
        return a.compareTo(b)
    }

    /**
     * Compares the release parts alone: `1.3.0-alpha01`, `1.3` and `1.3.0` all rank the same. An
     * `agentVersionRange` bound is checked this way.
     */
    internal fun compareReleases(a: String, b: String): Int {
        val left = a.releaseParts()
        val right = b.releaseParts()
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    private fun comparePreReleases(a: String?, b: String?): Int = when {
        a == null && b == null -> 0
        a == null -> 1
        b == null -> -1
        else -> compareSegments(a.segments(), b.segments())
    }

    private fun compareSegments(left: List<String>, right: List<String>): Int {
        for (i in 0 until minOf(left.size, right.size)) {
            val leftNumeric = left[i].first().isDigit()
            val rightNumeric = right[i].first().isDigit()
            val diff = when {
                leftNumeric && rightNumeric -> left[i].toBigInteger().compareTo(right[i].toBigInteger())
                leftNumeric -> -1
                rightNumeric -> 1
                else -> left[i].compareTo(right[i], ignoreCase = true)
            }
            if (diff != 0) return diff
        }
        return left.size.compareTo(right.size)
    }

    private fun String.releaseParts(): List<Int> = substringBefore('-').split(".").map { it.toIntOrNull() ?: 0 }

    private fun String.preRelease(): String? = substringAfter('-', missingDelimiterValue = "").ifEmpty { null }

    private fun String.segments(): List<String> = preReleaseSegment.findAll(this).map(MatchResult::value).toList()
}
