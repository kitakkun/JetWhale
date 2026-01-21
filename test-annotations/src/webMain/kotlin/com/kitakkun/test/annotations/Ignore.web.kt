package com.kitakkun.test.annotations

actual typealias IgnoreWeb = kotlin.test.Ignore

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
actual annotation class IgnoreNative
