package com.kitakkun.test.annotations

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
actual annotation class IgnoreWeb

actual typealias IgnoreNative = kotlin.test.Ignore
