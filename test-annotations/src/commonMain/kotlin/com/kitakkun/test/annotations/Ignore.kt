@file:Suppress("UNUSED")

package com.kitakkun.test.annotations

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
expect annotation class IgnoreWeb()

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
expect annotation class IgnoreNative()
