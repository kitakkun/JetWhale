package com.kitakkun.test.annotations

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
actual annotation class IgnoreWeb

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
actual annotation class IgnoreNative
