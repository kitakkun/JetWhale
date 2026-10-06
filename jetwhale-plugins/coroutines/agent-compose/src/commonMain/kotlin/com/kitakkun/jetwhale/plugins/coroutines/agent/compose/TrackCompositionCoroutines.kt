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
 * The compositions one `Recomposer` drives share that job — on Android, every `ComposeView` in a
 * window — so a call in any of them shows all of them. Give such calls one name: the root then stays
 * while any of them is in composition.
 *
 * The coroutines have no name of their own, so they are listed as `StandaloneCoroutine`; give one a
 * `CoroutineName` to tell it apart, e.g. `LaunchedEffect(key) { withContext(CoroutineName("poll")) { ... } }`.
 */
@Composable
fun JetWhaleCoroutineInspectorAgentPlugin.TrackCompositionCoroutines(name: String) {
    var trackedJob by remember(this, name) { mutableStateOf<Job?>(null) }
    // A LaunchedEffect's coroutine is a child of the composition's effect job; this one returns at
    // once so nothing of it stays in the tree.
    LaunchedEffect(this, name) {
        @OptIn(ExperimentalCoroutinesApi::class)
        trackedJob = coroutineContext[Job]?.parent?.also { register(it, name) }
    }
    // The effect job belongs to the Recomposer and outlives this call, so leaving composition does
    // not complete it.
    DisposableEffect(this, name) {
        onDispose { trackedJob?.let { unregister(it, name) } }
    }
}
