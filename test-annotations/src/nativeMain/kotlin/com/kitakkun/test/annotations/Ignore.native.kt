package com.kitakkun.test.annotations

import kotlin.test.Ignore

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
actual annotation class IgnoreWeb

actual typealias IgnoreNative = Ignore
