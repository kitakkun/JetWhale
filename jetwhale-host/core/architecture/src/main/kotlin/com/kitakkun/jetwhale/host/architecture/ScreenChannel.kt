package com.kitakkun.jetwhale.host.architecture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Bundles a screen's input (action) and output (result) into a single object backed by
 * kotlinx [Channel] (not SharedFlow): exactly-once delivery, buffered, no replay. This
 * fixes the drop/duplicate problems of the previous `MutableSharedFlow`-based event flow.
 *
 * Capability gating is enforced through context parameters:
 * - [send] (non-suspend, from UI callbacks) requires [ScreenContext] (Root).
 * - [emit] (suspend, only from within an effect) requires [PresenterContext] (Presenter).
 * - [ActionEffect] consumes actions and requires [PresenterContext] (Presenter only).
 * - [ActionResultEffect] consumes results and requires [ScreenContext] (Root only).
 */
class ScreenChannel<Action, ActionResult>(
    internal val actions: Channel<Action> = Channel(Channel.BUFFERED),
    internal val results: Channel<ActionResult> = Channel(Channel.BUFFERED),
) {
    /** Produced by Root/UI callbacks. Non-suspend so it can be called from onClick. */
    context(_: ScreenContext)
    fun send(action: Action) {
        actions.trySend(action)
    }

    /** Produced by the Presenter. Suspend so it can only be emitted from within an effect. */
    context(_: PresenterContext)
    suspend fun emit(result: ActionResult) {
        results.send(result)
    }
}

@Composable
fun <A, R> rememberScreenChannel(): ScreenChannel<A, R> = remember { ScreenChannel() }

/** Consumes actions in the Presenter. Gated by [PresenterContext]. */
context(_: PresenterContext)
@Composable
fun <A> ActionEffect(
    screenChannel: ScreenChannel<A, *>,
    block: suspend (A) -> Unit,
) {
    LaunchedEffect(screenChannel) {
        screenChannel.actions.receiveAsFlow().collect { block(it) }
    }
}

/** Consumes results in the Root. Gated by [ScreenContext]. */
context(_: ScreenContext)
@Composable
fun <R> ActionResultEffect(
    screenChannel: ScreenChannel<*, R>,
    block: suspend (R) -> Unit,
) {
    LaunchedEffect(screenChannel) {
        screenChannel.results.receiveAsFlow().collect { block(it) }
    }
}
