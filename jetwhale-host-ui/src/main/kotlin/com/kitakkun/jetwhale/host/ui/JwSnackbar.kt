package com.kitakkun.jetwhale.host.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/** How long a snackbar stays before it leaves on its own. */
public enum class JwSnackbarDuration {
    /** Long enough to read one line: a session came or went. */
    Short,

    /** For a message the user may want to act on before it goes. */
    Long,

    /** Stays until [JwSnackbarData.dismiss] is called; pair it with an action. */
    Indefinite,
}

/** Sizes of a [JwSnackbarHost]. */
public object JwSnackbarDefaults {
    /** The widest a snackbar grows; longer text wraps. */
    public val maxWidth: Dp = 480.dp

    /** How long [JwSnackbarDuration.Short] stays, in milliseconds. */
    public const val SHORT_DURATION_MILLIS: Long = 4_000

    /** How long [JwSnackbarDuration.Long] stays, in milliseconds. */
    public const val LONG_DURATION_MILLIS: Long = 10_000
}

/** The message a [JwSnackbarHost] is showing. */
@Stable
public interface JwSnackbarData {
    /** The text shown. */
    public val message: String

    /** The label of the action button, or null for a message with no action. */
    public val actionLabel: String?

    /** How long the message stays. */
    public val duration: JwSnackbarDuration

    /** Ends the message with [JwSnackbarResult.ActionPerformed]; the host wires it to the action button. */
    public fun performAction()

    /** Ends the message with [JwSnackbarResult.Dismissed]; the host calls it when the duration is up. */
    public fun dismiss()
}

/** How a [JwSnackbarHostState.showSnackbar] call ended. */
public enum class JwSnackbarResult {
    /** The message timed out or was dismissed without its action. */
    Dismissed,

    /** The user clicked the action button. */
    ActionPerformed,
}

/**
 * The queue behind a [JwSnackbarHost]: remember one, hand it to the host, and call [showSnackbar]
 * from a coroutine wherever a message needs showing. Messages show one at a time, in the order they
 * were requested.
 */
@Stable
public class JwSnackbarHostState {
    private val mutex = Mutex()

    /** The message on screen, or null while the host is idle. */
    public var currentSnackbarData: JwSnackbarData? by mutableStateOf(null)
        private set

    /**
     * Shows [message] and suspends until it leaves: on its own after [duration], or by its action.
     * A message already showing finishes first; cancelling the caller removes the message.
     *
     * @param message the text to show.
     * @param actionLabel the label of an action button, or null for none.
     * @param duration how long the message stays without being acted on.
     * @return whether the action was clicked.
     */
    public suspend fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        duration: JwSnackbarDuration = JwSnackbarDuration.Short,
    ): JwSnackbarResult = mutex.withLock {
        try {
            suspendCancellableCoroutine { continuation ->
                currentSnackbarData = SnackbarDataImpl(message, actionLabel, duration, continuation)
            }
        } finally {
            currentSnackbarData = null
        }
    }

    private class SnackbarDataImpl(
        override val message: String,
        override val actionLabel: String?,
        override val duration: JwSnackbarDuration,
        private val continuation: CancellableContinuation<JwSnackbarResult>,
    ) : JwSnackbarData {
        override fun performAction() {
            if (continuation.isActive) continuation.resume(JwSnackbarResult.ActionPerformed)
        }

        override fun dismiss() {
            if (continuation.isActive) continuation.resume(JwSnackbarResult.Dismissed)
        }
    }
}

/**
 * Shows the messages queued on [hostState], one at a time, as a small dark strip that slides in at
 * the bottom of whatever it is placed over. Put it in a `Box` over the content it comments on,
 * aligned to the bottom; the host itself takes no space while idle.
 *
 * @param hostState the queue to show.
 */
@Composable
public fun JwSnackbarHost(
    hostState: JwSnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val data = hostState.currentSnackbarData
    LaunchedEffect(data) {
        if (data == null) return@LaunchedEffect
        val millis = when (data.duration) {
            JwSnackbarDuration.Short -> JwSnackbarDefaults.SHORT_DURATION_MILLIS
            JwSnackbarDuration.Long -> JwSnackbarDefaults.LONG_DURATION_MILLIS
            JwSnackbarDuration.Indefinite -> return@LaunchedEffect
        }
        delay(millis)
        data.dismiss()
    }
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = data != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            // Held through the exit animation, when the state has already cleared. A plain holder,
            // not a state: this content recomposes with `data` anyway.
            val lastShown = remember { arrayOfNulls<JwSnackbarData>(1) }
            if (data != null) lastShown[0] = data
            lastShown[0]?.let { JwSnackbar(it) }
        }
    }
}

/** One message strip: the text, and the action button when the message has one. */
@Composable
private fun JwSnackbar(data: JwSnackbarData) {
    val colors = JwTheme.colors
    Row(
        modifier = Modifier
            .widthIn(max = JwSnackbarDefaults.maxWidth)
            .shadow(SnackbarShadowElevation, JwShapes.medium)
            .background(colors.tooltipBackground, JwShapes.medium)
            .padding(horizontal = JwSpacing.large, vertical = JwSpacing.medium)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.large),
    ) {
        JwText(
            text = data.message,
            style = JwTheme.textStyles.body,
            color = colors.onTooltip,
            modifier = Modifier.weight(1f, fill = false),
        )
        data.actionLabel?.let { label ->
            SnackbarAction(label = label, onClick = data::performAction)
        }
    }
}

/**
 * The action of a snackbar: a text button in the strip's own colors, since a [JwButton]'s accent
 * would fight the dark background.
 */
@Composable
private fun SnackbarAction(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .height(JwMetrics.controlHeight)
            .jwFocusRing(interactionSource, JwShapes.small)
            .clip(JwShapes.small)
            .background(if (hovered) JwTheme.colors.onTooltip.copy(alpha = ACTION_HOVER_ALPHA) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = JwSpacing.medium),
        contentAlignment = Alignment.Center,
    ) {
        JwText(
            text = label,
            style = JwTheme.textStyles.label,
            color = JwTheme.colors.onTooltip,
            maxLines = 1,
        )
    }
}

/** Opacity of the hover tint on a snackbar action. */
private const val ACTION_HOVER_ALPHA = 0.12f

/** Shadow under a snackbar. */
private val SnackbarShadowElevation = JwSpacing.small
