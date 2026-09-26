package com.kitakkun.jetwhale.plugins.coroutines.agent.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.plugins.coroutines.agent.JetWhaleCoroutineInspectorAgentPlugin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlin.coroutines.coroutineContext

/**
 * Shows the coroutines this composition's Composables start — `LaunchedEffect`, `produceState`,
 * `collectAsState` and the scopes `rememberCoroutineScope` returns — under [name], for as long as
 * this call stays in composition.
 *
 * Every one of those coroutines is a child of the composition's effect job, so one call at the root
 * of a window or a `ComposeView` covers every Composable below it, subcompositions such as lazy
 * list items included:
 *
 * ```kotlin
 * @Composable
 * fun App() {
 *     inspector.TrackCompositionCoroutines(name = "Compose")
 *     ...
 * }
 * ```
 *
 * The coroutines have no name of their own, so they are listed as `StandaloneCoroutine`; give one a
 * `CoroutineName` to tell it apart, e.g. `LaunchedEffect(key) { withContext(CoroutineName("poll")) { ... } }`.
 */
@Composable
fun JetWhaleCoroutineInspectorAgentPlugin.TrackCompositionCoroutines(name: String) {
    // The effect's own coroutine is a child of the composition's effect job; reading its parent
    // and returning at once leaves nothing of it behind in the tree.
    var trackedJob by remember(this, name) { mutableStateOf<Job?>(null) }
    LaunchedEffect(this, name) {
        @OptIn(ExperimentalCoroutinesApi::class)
        trackedJob = coroutineContext[Job]?.parent?.also { register(it, name) }
    }
    // The effect job outlives this call (it belongs to the Recomposer), so it is unregistered here;
    // only this call's own registration, since another window may reuse the name.
    DisposableEffect(this, name) {
        onDispose { trackedJob?.let { unregister(it, name) } }
    }
}
