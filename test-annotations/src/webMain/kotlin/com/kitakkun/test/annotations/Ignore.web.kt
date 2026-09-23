package com.kitakkun.test.annotations

import kotlin.test.Ignore

actual typealias IgnoreWeb = Ignore

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
actual annotation class IgnoreNative
