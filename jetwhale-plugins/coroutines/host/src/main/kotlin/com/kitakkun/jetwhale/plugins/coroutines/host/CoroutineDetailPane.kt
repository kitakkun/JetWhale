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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(JwSpacing.large), verticalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
        if (gone) {
            JwBanner(
                text = "This coroutine is gone: it finished, or its scope was dropped. What follows is how it looked the last time the app reported it.",
                tone = JwTone.Warning,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
            JwText(text = node.name ?: "Unnamed coroutine", style = JwTheme.textStyles.subtitle)
            JwTag(text = node.state.label, tone = node.state.tone)
        }
        JwText(text = node.state.explanation, color = JwTheme.colors.textSecondary)

        JwSectionHeader(title = "Where it lives")
        JwKeyValueRow(key = "Path", value = (location.ancestors + node).joinToString(" › ") { it.name ?: it.id })
        JwKeyValueRow(key = "Dispatcher", value = node.dispatcher ?: "None — a plain Job rather than a coroutine", monospace = node.dispatcher != null)
        JwKeyValueRow(key = "Seen for", value = "${formatObserved(node.observedMillis)} (since the inspector first found it; a Job keeps no start time)")
        JwKeyValueRow(key = "Id", value = node.id, monospace = true)

        JwSectionHeader(title = "Below it")
        JwText(text = describeDescendants(node.children.size, node.descendantStates()))

        JwSectionHeader(title = "Job")
        JwCodeBlock(text = node.description, wrap = true, copyLabel = "Copy")

        JwSectionHeader(
            title = "Stack",
            trailing = { JwButton(text = "Read again", onClick = onReloadStack, style = JwButtonStyle.Text, enabled = !gone) },
        )
        StackSection(detail)
    }
}

@Composable
private fun StackSection(detail: CoroutineDetail?) {
    when {
        detail == null -> JwText(text = "Reading from the app…", color = JwTheme.colors.textSecondary)

        !detail.found -> JwText(text = "The app no longer knows this coroutine, so it has no stack to give.", color = JwTheme.colors.textSecondary)

        detail.stackUnavailableReason != null -> JwText(text = "No stack: ${detail.stackUnavailableReason}.", color = JwTheme.colors.textSecondary)

        else -> {
            detail.debugState?.let { JwText(text = debugStateExplanation(it)) }
            JwCodeBlock(text = detail.suspensionStack.joinToString("\n").ifEmpty { "(no frames recorded)" }, copyLabel = "Copy stack")
            if (detail.creationStack.isNotEmpty()) {
                JwText(text = "Created at", style = JwTheme.textStyles.label)
                JwCodeBlock(text = detail.creationStack.joinToString("\n"), copyLabel = "Copy stack")
            }
        }
    }
}

private fun describeDescendants(children: Int, descendants: Map<CoroutineState, Int>): String {
    if (children == 0) return "No coroutines run below it."
    val total = descendants.values.sum()
    val byState = CoroutineState.entries.mapNotNull { state -> descendants[state]?.let { "$it ${state.label.lowercase()}" } }.joinToString(", ")
    return "$children direct ${if (children == 1) "child" else "children"}, $total in all: $byState."
}

private fun debugStateExplanation(debugState: String): String = when (debugState) {
    "SUSPENDED" -> "Suspended — waiting to be resumed. The frames below are where it waits."
    "RUNNING" -> "Running on a thread right now. The frames below are where it last suspended."
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
