package com.kitakkun.jetwhale.plugins.mainthread.agent

/** A lambda's hidden class carries its address, which differs every run and says nothing. */
private val HIDDEN_CLASS_ADDRESS = Regex("""/0x[0-9a-f]+""")

/**
 * A frame as `Class.method(File.kt:12)`. `StackTraceElement.toString` prefixes JDK frames with
 * their class loader and module (`java.base/java.lang.Thread.sleep`), which would keep them from
 * being recognized as platform frames, and a lambda's address would split one call site into a
 * hotspot per run.
 */
internal fun frameText(element: StackTraceElement): String = "${withoutHiddenClassAddress(element.className)}.${element.methodName}(${element.fileName}:${element.lineNumber})"

internal fun withoutHiddenClassAddress(className: String): String = className.replace(HIDDEN_CLASS_ADDRESS, "")
