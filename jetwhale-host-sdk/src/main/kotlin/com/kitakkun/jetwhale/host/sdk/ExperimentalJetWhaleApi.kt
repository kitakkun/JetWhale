package com.kitakkun.jetwhale.host.sdk

/**
 * Marks an API as experimental. Experimental APIs may change or be removed without notice.
 * Opt in with [OptIn] to suppress the warning.
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
