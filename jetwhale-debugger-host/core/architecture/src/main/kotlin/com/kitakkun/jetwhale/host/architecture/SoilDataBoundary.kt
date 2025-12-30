package com.kitakkun.jetwhale.host.architecture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import soil.plant.compose.reacty.Await
import soil.plant.compose.reacty.ErrorBoundary
import soil.plant.compose.reacty.Suspense
import soil.query.compose.QueryObject
import soil.query.compose.SubscriptionObject
import soil.query.core.DataModel

@Composable
context(_: ScreenContext)
fun <T> SoilDataBoundary(
    state: DataModel<T>,
    fallback: SoilFallback = SoilFallbackDefaults.default(),
    content: @Composable (T) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    ErrorBoundary(
        fallback = { fallback.errorFallback(it) },
        onReset = {
            coroutineScope.launch {
                state.performResetIfNeeded()
            }
        }
    ) {
        Suspense(
            fallback = { fallback.suspenseFallback() },
        ) {
            Await(
                state = state,
                content = content,
            )
        }
    }
}

@Composable
context(_: ScreenContext)
fun <T1, T2> SoilDataBoundary(
    state1: DataModel<T1>,
    state2: DataModel<T2>,
    fallback: SoilFallback = SoilFallbackDefaults.default(),
    content: @Composable (T1, T2) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    ErrorBoundary(
        fallback = { fallback.errorFallback(it) },
        onReset = {
            coroutineScope.launch {
                state1.performResetIfNeeded()
                state2.performResetIfNeeded()
            }
        }
    ) {
        Suspense(fallback = { fallback.suspenseFallback() }) {
            Await(
                state1 = state1,
                state2 = state2,
                content = content,
            )
        }
    }
}

@Composable
fun <T1, T2, T3> SoilDataBoundary(
    state1: DataModel<T1>,
    state2: DataModel<T2>,
    state3: DataModel<T3>,
    fallback: SoilFallback = SoilFallbackDefaults.default(),
    content: @Composable (T1, T2, T3) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    ErrorBoundary(
        fallback = { fallback.errorFallback(it) },
        onReset = {
            coroutineScope.launch {
                state1.performResetIfNeeded()
                state2.performResetIfNeeded()
                state3.performResetIfNeeded()
            }
        }
    ) {
        Suspense(
            fallback = { fallback.suspenseFallback() },
        ) {
            Await(
                state1 = state1,
                state2 = state2,
                state3 = state3,
                content = content,
            )
        }
    }
}

private suspend fun <T> DataModel<T>.performResetIfNeeded() {
    when (this) {
        is QueryObject<T> -> this.error?.let { this.refresh() }
        is SubscriptionObject<T> -> this.error?.let { this.reset() }
    }
}
