package com.kitakkun.jetwhale.plugins.deeplinks.protocol

import kotlinx.serialization.Serializable

/**
 * One declaration of links the app handles. Every scheme combines with every host and every path
 * matcher, the way Android combines the `<data>` elements of one intent filter.
 *
 * @property handler What receives the link: an Android activity's class name, or the name of an iOS
 *   URL type.
 * @property hosts Empty when the declaration matches any host (or the scheme has none, like a custom
 *   scheme on iOS).
 * @property paths Empty when the declaration matches any path.
 * @property browsable Whether a browser or another app may follow the link (Android's BROWSABLE
 *   category); a link that is not browsable only opens from inside the app.
 * @property autoVerify Whether the declaration asks for Android App Links verification.
 */
@Serializable
data class DeclaredDeepLink(
    val handler: String,
    val schemes: List<String>,
    val hosts: List<DeepLinkHost>,
    val paths: List<PathMatcher>,
    val browsable: Boolean,
    val autoVerify: Boolean,
)

/**
 * @property port Null when the declaration names no port.
 * @property verification The platform's App Links verification state for this host, when it reports
 *   one (Android 12+), e.g. "verified" or "none".
 */
@Serializable
data class DeepLinkHost(
    val host: String,
    val port: String?,
    val verification: String?,
)

/** A path condition of a declaration, in the platform's own syntax. */
@Serializable
data class PathMatcher(
    val kind: PathMatchKind,
    val value: String,
)

@Serializable
enum class PathMatchKind {
    /** `android:path`: the whole path. */
    Exact,

    /** `android:pathPrefix`. */
    Prefix,

    /** `android:pathSuffix` (Android 12+). */
    Suffix,

    /** `android:pathPattern`: Android's simple glob, where `.` is any character and `*` repeats the one before. */
    Pattern,

    /** `android:pathAdvancedPattern` (Android 12+): a regular-expression-like pattern. */
    AdvancedPattern,
}

/**
 * An example link the app registered, so it can be opened without knowing its exact shape.
 *
 * @property template The link with `{name}` placeholders, e.g. `myapp://item/{id}`.
 */
@Serializable
data class DeepLinkTemplate(
    val name: String,
    val template: String,
    val description: String?,
)
