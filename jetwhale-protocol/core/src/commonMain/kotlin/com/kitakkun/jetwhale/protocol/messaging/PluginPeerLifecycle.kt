package com.kitakkun.jetwhale.protocol.messaging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

// Shared peer-lifecycle orchestration. Both runtimes create a [JetWhalePluginPeer] with
// awaitReady = true, register the plugin's handlers, then run the plugin's preparation behind a
// timeout before opening the ready gate. Hoisting that identical choreography here keeps the agent
// runtime (one peer per plugin per connection) and the host (one peer per plugin instance per
// session) from drifting.
//
// Each helper takes a `descriptor` that identifies the plugin in log messages (e.g. "plugin 'x'" on
// the agent, "plugin 'x' in session 'y'" on the host).

/**
 * Registers handlers on [peer] via [registerHandlers], isolating a throwing registration (e.g. a
 * duplicate handler). Returns `true` on success. On failure it reports via [warn] and returns
 * `false`; the caller must not use the peer, since a half-configured peer would misbehave.
 */
public fun configurePeerGuarded(
    peer: JetWhalePluginPeer,
    descriptor: String,
    registerHandlers: JetWhaleMessageHandlers.() -> Unit,
    warn: (String, Throwable) -> Unit,
): Boolean = try {
    peer.configure(registerHandlers)
    true
} catch (e: Throwable) {
    warn("JetWhale: handler registration for $descriptor failed; plugin stays offline this connection.", e)
    false
}

/**
 * Launches the plugin's preparation on this scope: runs [dispatchPrepare] under
 * [prepareTimeoutMillis], then — always, even on timeout or failure — opens the peer's ready gate
 * and invokes [onReady]. A degraded (ready but unprepared) plugin beats a frozen one, so the ready
 * gate opens on every exit path; a failed request during prepare is the common degraded path and
 * must never escape as an uncaught exception in the runtime's process. Returns the [Job] so the
 * caller can join it before rebinding/disposing.
 */
public fun CoroutineScope.launchPeerPreparation(
    peer: JetWhalePluginPeer,
    descriptor: String,
    prepareTimeoutMillis: Long,
    dispatchPrepare: suspend () -> Unit,
    warn: (String, Throwable) -> Unit,
    onReady: () -> Unit,
): Job = launch {
    try {
        withTimeout(prepareTimeoutMillis) {
            dispatchPrepare()
        }
    } catch (e: TimeoutCancellationException) {
        warn("JetWhale: onPrepare for $descriptor did not complete in time; proceeding.", e)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        warn("JetWhale: onPrepare for $descriptor failed; proceeding.", e)
    } finally {
        peer.markReady()
        onReady()
    }
}

/**
 * Fast-fails an inbound [frame] that has no live peer: if it is a request, replies with a
 * [PluginFrame.Reply.Failure] carrying [errorMessage] so the requester does not wait out its
 * timeout; notifications and replies are ignored. Launched on [scope], not awaited — [send] is a
 * suspending socket write and callers route every session/plugin's frames from one collector, so
 * awaiting here would stall delivery of every other frame while this one reply is in flight. The
 * send is best effort: the socket may already be dead (e.g. a request racing a disconnect), and a
 * failed failure-reply must not surface as an uncaught exception.
 */
public fun replyPeerUnavailable(
    scope: CoroutineScope,
    frame: PluginFrame,
    errorMessage: String,
    send: suspend (PluginFrame) -> Unit,
    warn: (String, Throwable) -> Unit,
) {
    if (frame !is PluginFrame.Request) return
    scope.launch {
        try {
            send(
                PluginFrame.Reply.Failure(
                    pluginId = frame.pluginId,
                    inReplyTo = frame.correlationId,
                    errorMessage = errorMessage,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            warn("JetWhale: failed to send fast-fail reply for plugin '${frame.pluginId}'.", e)
        }
    }
}
