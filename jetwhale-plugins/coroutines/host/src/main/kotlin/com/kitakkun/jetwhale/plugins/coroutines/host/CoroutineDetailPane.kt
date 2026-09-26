package com.kitakkun.jetwhale.plugins.coroutines.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwCodeBlock
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwKeyValueRow
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDetail
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineState

/**
 * Everything the inspector knows about the selected coroutine. [current] is where it is in the
 * latest tree, or null once it has gone; [lastSeen] is where it was the last time a tree had it.
 */
@Composable
internal fun CoroutineDetailPane(
    current: CoroutineLocation?,
    lastSeen: CoroutineLocation?,
    detail: CoroutineDetail?,
    onReloadStack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val location = current ?: lastSeen
    Box(modifier.fillMaxSize()) {
        if (location == null) {
            JwEmptyState(title = "No coroutine selected", description = "Pick a coroutine in the tree to see its state, where it lives, what runs below it and — on the JVM — the stack it waits at.")
        } else {
            DetailContent(location = location, gone = current == null, detail = detail, onReloadStack = onReloadStack)
        }
    }
}

@Composable
private fun DetailContent(location: CoroutineLocation, gone: Boolean, detail: CoroutineDetail?, onReloadStack: () -> Unit) {
    val node = location.node
    val stacks = detail?.takeIf { it.found && it.stackUnavailableReason == null }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(JwSpacing.large), verticalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
        if (gone) {
            JwBanner(
                text = "This coroutine is gone: it finished, or its scope was dropped. What follows is how it looked the last time the app reported it.",
                tone = JwTone.Warning,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.small), verticalAlignment = Alignment.CenterVertically) {
            JwText(text = node.name ?: "Unnamed coroutine", style = JwTheme.textStyles.subtitle)
            JwTag(text = node.state.label, tone = node.state.tone)
            stacks?.debugState?.let { JwTag(text = debugStateLabel(it), tone = JwTone.Neutral) }
        }
        // With DebugProbes' own state at hand, the stack says more than the Job state's caveats.
        JwText(text = stacks?.debugState?.let(::debugStateExplanation) ?: node.state.explanation, color = JwTheme.colors.textSecondary)

        JwSectionHeader(
            title = "Where it waits",
            trailing = { JwButton(text = "Read again", onClick = onReloadStack, style = JwButtonStyle.Text, enabled = !gone) },
        )
        StackSection(detail)

        JwSectionHeader(title = "Where it lives")
        JwKeyValueRow(key = "Path", value = (location.ancestors + node).joinToString(" › ") { it.name ?: it.id })
        JwKeyValueRow(key = "Dispatcher", value = node.dispatcher ?: "None — a plain Job rather than a coroutine", monospace = node.dispatcher != null)
        JwKeyValueRow(key = "Seen for", value = "${formatObserved(node.observedMillis)}, since the inspector first saw it")
        JwKeyValueRow(key = "Below it", value = describeDescendants(node.children.size, node.descendantStates()))
        JwKeyValueRow(key = "Job", value = node.description, monospace = true)
        JwKeyValueRow(key = "Id", value = node.id, monospace = true)
    }
}

@Composable
private fun StackSection(detail: CoroutineDetail?) {
    when {
        detail == null -> JwText(text = "Reading from the app…", color = JwTheme.colors.textSecondary)

        !detail.found -> JwText(text = "The app no longer knows this coroutine, so it has no stack to give.", color = JwTheme.colors.textSecondary)

        detail.stackUnavailableReason != null -> JwText(text = "No stack: ${detail.stackUnavailableReason}.", color = JwTheme.colors.textSecondary)

        detail.suspensionStack.isEmpty() -> JwText(text = "DebugProbes recorded no frames for it.", color = JwTheme.colors.textSecondary)

        else -> {
            firstAppFrame(detail.suspensionStack)?.let { JwKeyValueRow(key = "In app code", value = it, monospace = true) }
            JwCodeBlock(text = frameText(detail.suspensionStack), wrap = true, copyLabel = "Copy stack")
            if (detail.creationStack.isNotEmpty()) {
                JwText(text = "Created at", style = JwTheme.textStyles.label)
                JwCodeBlock(text = frameText(detail.creationStack), wrap = true, copyLabel = "Copy stack")
            }
        }
    }
}

/** The frames one per line, the coroutines library's and the platform's dimmed so the app's own stand out. */
@Composable
private fun frameText(frames: List<String>): AnnotatedString {
    val dimmed = JwTheme.colors.textSecondary
    return buildAnnotatedString {
        frames.forEachIndexed { index, frame ->
            if (index > 0) append("\n")
            if (isLibraryFrame(frame)) withStyle(SpanStyle(color = dimmed)) { append(frame) } else append(frame)
        }
    }
}

/** Packages whose frames say how a coroutine suspends rather than where the app made it wait. */
private val LibraryFramePrefixes = listOf("kotlin.", "kotlinx.coroutines.", "java.", "javax.", "jdk.", "sun.", "android.", "androidx.")

internal fun isLibraryFrame(frame: String): Boolean = LibraryFramePrefixes.any(frame::startsWith)

/** The innermost frame of the app's own code: the line to open first. */
internal fun firstAppFrame(frames: List<String>): String? = frames.firstOrNull { !isLibraryFrame(it) }

private fun describeDescendants(children: Int, descendants: Map<CoroutineState, Int>): String {
    if (children == 0) return "No coroutines run below it."
    val total = descendants.values.sum()
    return "$children direct ${if (children == 1) "child" else "children"}, $total in all: ${formatStateCounts(descendants, separator = ", ")}."
}

private fun debugStateLabel(debugState: String): String = when (debugState) {
    "SUSPENDED" -> "Suspended"
    "RUNNING" -> "Running"
    "CREATED" -> "Not started"
    else -> debugState
}

private fun debugStateExplanation(debugState: String): String = when (debugState) {
    "SUSPENDED" -> "Suspended: waiting for something to resume it. The stack below is where it waits."
    "RUNNING" -> "Running on a thread right now. The stack below is where it last suspended."
    "CREATED" -> "Created and not started yet."
    else -> debugState
}

internal val CoroutineState.label: String
    get() = when (this) {
        CoroutineState.New -> "Not started"
        CoroutineState.Active -> "Active"
        CoroutineState.Cancelling -> "Cancelling"
        CoroutineState.Completed -> "Completed"
        CoroutineState.Cancelled -> "Cancelled"
    }

/** "3 active, 1 cancelling": [counts] in the order a coroutine goes through its states. */
internal fun formatStateCounts(counts: Map<CoroutineState, Int>, separator: String): String = CoroutineState.entries.mapNotNull { state -> counts[state]?.let { "$it ${state.label.lowercase()}" } }.joinToString(separator)

internal val CoroutineState.tone: JwTone
    get() = when (this) {
        CoroutineState.New -> JwTone.Neutral
        CoroutineState.Active -> JwTone.Info
        CoroutineState.Cancelling -> JwTone.Warning
        CoroutineState.Completed -> JwTone.Success
        CoroutineState.Cancelled -> JwTone.Error
    }

/** What the state means for someone looking at a live app, including what it cannot tell. */
internal val CoroutineState.explanation: String
    get() = when (this) {
        CoroutineState.New -> "Created with CoroutineStart.LAZY and never started. It runs once something calls start(), join() or await() — one that stays here is usually forgotten."
        CoroutineState.Active -> "Running, suspended, or done with its own body and waiting for its children: a Job cannot tell these apart. On the JVM with DebugProbes, the stack below tells running from suspended."
        CoroutineState.Cancelling -> "Cancelled and winding down: running its finally blocks and waiting for its children. One that stays here is stuck in cleanup, or in a child that ignores cancellation."
        CoroutineState.Completed -> "Finished normally, with all of its children."
        CoroutineState.Cancelled -> "Finished by cancellation or by an exception."
    }
