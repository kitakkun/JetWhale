package com.kitakkun.jetwhale.host.sdk

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * The UI targets of this plugin instance that an AI agent is operating now: the targets of every
 * [JetWhaleMcpCommand.markingAiOperationOn] block that is running, or ended less than 1.5 seconds
 * ago, so work that finishes within a frame still shows. Outside the host the set is empty.
 *
 * A target is a value the plugin defines, compared by `equals`. To ring the control that stands for
 * one, use `Modifier.jwAiOperatingRingWhileOperated` from jetwhale-host-ui, which reads this local
 * itself and draws the ring the host marks AI operations with. Read the local directly only for an
 * indication of your own.
 */
@ExperimentalJetWhaleApi
public val LocalAiOperatedUiTargets: ProvidableCompositionLocal<Set<Any>> = compositionLocalOf { emptySet() }

/**
 * Receives the start and end of each [JetWhaleMcpCommand.markingAiOperationOn] block of one plugin
 * instance. The host puts one in the coroutine context it runs a command in; plugins don't
 * implement it.
 */
@InternalJetWhaleHostApi
public abstract class AiOperatedUiTargetsReporter : AbstractCoroutineContextElement(Key) {
    /** Called as a block that operates [targets] starts. */
    public abstract fun reportOperationStarted(targets: Set<Any>)

    /** Called as a block that operates [targets] ends, however it ends. */
    public abstract fun reportOperationEnded(targets: Set<Any>)

    public companion object Key : CoroutineContext.Key<AiOperatedUiTargetsReporter>
}
