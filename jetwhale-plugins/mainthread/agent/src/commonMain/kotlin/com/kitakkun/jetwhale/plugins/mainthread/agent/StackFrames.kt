package com.kitakkun.jetwhale.plugins.mainthread.agent

/**
 * Frames from these packages belong to the platform, a library runtime, or this agent, not to the
 * app: a stall is attributed to the innermost frame outside them, which is where the app's own
 * code asked for the slow work.
 */
private val PLATFORM_FRAME_PREFIXES = listOf(
    "android.", "androidx.", "com.android.", "dalvik.", "libcore.", "java.", "javax.", "jdk.", "sun.",
    "kotlin.", "kotlinx.", "okio.", "okhttp3.", "io.ktor.", "org.jetbrains.skiko.", "org.jetbrains.skia.",
    // JetWhale's own agent code, but not an app that happens to share the namespace (the demo).
    "com.kitakkun.jetwhale.agent.", "com.kitakkun.jetwhale.plugins.", "com.kitakkun.jetwhale.protocol.",
)

/** How many of the app's innermost frames name a hotspot: enough to tell call sites apart. */
private const val SIGNATURE_FRAMES = 3

/**
 * Names the place a stack was stuck: its innermost frames that belong to the app, so samples from
 * the same call site group together even when the platform frames above them differ. A stack with
 * no app frame at all (the main thread waiting inside the platform) is named by its innermost
 * frames instead.
 */
internal fun stackSignature(frames: List<String>): String {
    val appFrames = frames.filterNot(::isPlatformFrame)
    return (appFrames.ifEmpty { frames }).take(SIGNATURE_FRAMES).joinToString(" ← ")
}

/** The innermost app frame of [frames], or the innermost frame when none belongs to the app. */
internal fun callSiteOf(frames: List<String>): String = frames.firstOrNull { !isPlatformFrame(it) } ?: frames.firstOrNull().orEmpty()

private fun isPlatformFrame(frame: String): Boolean = PLATFORM_FRAME_PREFIXES.any(frame::startsWith)

/** One line Android's main Looper writes around each message it dispatches. */
internal sealed interface LooperLogLine {
    data class Dispatching(val label: String) : LooperLogLine

    data object Finished : LooperLogLine
}

private const val DISPATCHING_PREFIX = ">>>>> Dispatching to "
private const val FINISHED_PREFIX = "<<<<< Finished to "

/** Hash codes and identity suffixes differ on every message, so they are left out of the label. */
private val IDENTITY_NOISE = Regex("""\s*\{[0-9a-f]+\}|@[0-9a-f]+""")

/** A task label for display: a Looper log line is reduced to its Handler and callback; anything else is kept. */
internal fun formatTaskLabel(description: String): String = when (val line = parseLooperLogLine(description)) {
    is LooperLogLine.Dispatching -> line.label
    else -> description
}

/**
 * Reads a line from `Looper.setMessageLogging`: `>>>>> Dispatching to Handler (X) {hash} callback: what`
 * or `<<<<< Finished to …`. The label keeps the Handler class and the callback, which is what tells
 * one kind of message from another; anything else yields null.
 */
internal fun parseLooperLogLine(line: String): LooperLogLine? = when {
    line.startsWith(DISPATCHING_PREFIX) -> {
        val body = line.removePrefix(DISPATCHING_PREFIX).substringBeforeLast(": ")
        LooperLogLine.Dispatching(body.replace(IDENTITY_NOISE, "").trim())
    }

    line.startsWith(FINISHED_PREFIX) -> LooperLogLine.Finished

    else -> null
}
