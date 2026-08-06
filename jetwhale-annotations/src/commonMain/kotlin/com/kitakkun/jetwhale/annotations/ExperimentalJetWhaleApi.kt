package com.kitakkun.jetwhale.annotations

/**
 * Marks an API that may change or be withdrawn in a later release.
 *
 * Distinct from [InternalJetWhaleApi], which is not meant to be called at all. This one is meant to
 * be called — it simply has not settled, so opting in is an acknowledgement that a future upgrade
 * may ask you to change the call.
 *
 * A warning rather than an error: the point is to be noticed, not to stop a build.
 *
 * There is deliberately one of these for the whole project. A per-module copy would share this
 * simple name, and `@OptIn(ExperimentalJetWhaleApi::class)` would then opt into whichever happened
 * to be imported — silently, since both compile.
 */
@RequiresOptIn(
    message = "This JetWhale API is experimental and may change or be removed without notice.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
public annotation class ExperimentalJetWhaleApi
