package com.kitakkun.jetwhale.host.data.crash

import com.kitakkun.jetwhale.host.model.JvmCrashLog
import java.io.File

/** How many Java frames of the crashing thread are kept; the innermost ones are the ones that matter. */
private const val MAX_JAVA_FRAMES = 20

/**
 * The fatal error log HotSpot wrote for the process [pid], looked up in [directories] in order.
 * Without `-XX:ErrorFile` HotSpot writes it to the working directory, or to the temp directory when
 * that is not writable.
 */
internal fun findJvmCrashLog(pid: Long, directories: List<File>): File? = directories
    .map { File(it, "hs_err_pid$pid.log") }
    .firstOrNull(File::isFile)

/**
 * Reads the parts of an `hs_err_pid*.log` worth showing. Everything is optional: a log cut short by
 * the dying process still yields what it has.
 */
internal fun parseJvmCrashLog(path: String, text: String): JvmCrashLog {
    val lines = text.lines()
    val errorLine = lines
        .firstOrNull { it.startsWith("#  ") && (it.contains("SIG") || it.contains("EXCEPTION_") || it.contains("Internal Error") || it.contains("OutOfMemory")) }
        ?.removePrefix("#")
        ?.trim()
    val problematicFrame = lines
        .indexOfFirst { it.startsWith("# Problematic frame:") }
        .takeIf { it >= 0 }
        ?.let { lines.getOrNull(it + 1) }
        ?.removePrefix("#")
        ?.trim()
    val crashingThread = lines
        .firstOrNull { it.startsWith("Current thread (") }
        ?.substringAfter("):")
        ?.trim()
    return JvmCrashLog(
        path = path,
        errorLine = errorLine,
        problematicFrame = problematicFrame,
        crashingThread = crashingThread,
        javaFrames = crashingThreadJavaFrames(lines),
    )
}

/**
 * The Java methods on the crashing thread's stack, from the "Native frames" block (which interleaves
 * compiled `J` and interpreted `j` frames with native ones) or, failing that, the "Java frames" block.
 */
private fun crashingThreadJavaFrames(lines: List<String>): List<String> {
    val start = lines.indexOfFirst { it.startsWith("Native frames:") }.takeIf { it >= 0 }
        ?: lines.indexOfFirst { it.startsWith("Java frames:") }.takeIf { it >= 0 }
        ?: return emptyList()
    return lines.drop(start + 1)
        .takeWhile(String::isNotBlank)
        .mapNotNull(::javaMethodOf)
        .take(MAX_JAVA_FRAMES)
}

/**
 * The fully qualified method of a `J`/`j` frame line, e.g. `com.example.Foo.bar` from
 * `J 18474 c1 com.example.Foo.bar(II)V (219 bytes) @ 0x…` or `j  com.example.Foo.bar(I)V+102`.
 */
private fun javaMethodOf(line: String): String? {
    val trimmed = line.trimStart()
    if (!trimmed.startsWith("J ") && !trimmed.startsWith("j ")) return null
    val signature = trimmed.split(' ').filter(String::isNotEmpty).firstOrNull { it.contains('(') } ?: return null
    return signature.substringBefore('(').takeIf(String::isNotEmpty)
}

/**
 * The plugin whose code is on the crashing thread's stack, if any. A plugin's code is recognized by
 * the package of its factory class; the innermost frame that matches decides. When that frame's
 * package belongs to several plugins — one jar shipping more than one — no plugin is named, since
 * disabling the wrong one would not help.
 *
 * @param pluginPackages Plugin id to the package of that plugin's factory class.
 */
internal fun JvmCrashLog.suspectPlugin(pluginPackages: Map<String, String>): String? {
    val matches = javaFrames.asSequence()
        .map { frame -> pluginPackages.entries.filter { (_, pluginPackage) -> pluginPackage.isNotEmpty() && frame.startsWith("$pluginPackage.") } }
        .firstOrNull { it.isNotEmpty() }
        ?: return null
    // A plugin nested inside another's package is the more specific match.
    val longest = matches.maxOf { (_, pluginPackage) -> pluginPackage.length }
    return matches.singleOrNull { (_, pluginPackage) -> pluginPackage.length == longest }?.key
}
