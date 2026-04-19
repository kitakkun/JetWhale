package com.kitakkun.jetwhale.host.architecture

import androidx.compose.runtime.Composable
import soil.plant.compose.reacty.Await
import soil.plant.compose.reacty.ErrorBoundary
import soil.plant.compose.reacty.Suspense
import soil.query.compose.util.rememberQueriesErrorReset
import soil.query.core.DataModel

@Composable
context(_: ScreenContext)
fun <T> SoilDataBoundary(
    state: DataModel<T>,
    fallback: SoilFallback = SoilFallbackDefaults.default(),
    content: @Composable (T) -> Unit,
) {
    ErrorBoundary(
        fallback = { fallback.errorFallback(it) },
        onReset = rememberQueriesErrorReset(),
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
    ErrorBoundary(
        fallback = { fallback.errorFallback(it) },
        onReset = rememberQueriesErrorReset(),
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
    ErrorBoundary(
        fallback = { fallback.errorFallback(it) },
        onReset = rememberQueriesErrorReset(),
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

@Composable
context(_: ScreenContext)
fun <T1, T2, T3, T4> SoilDataBoundary(
    state1: DataModel<T1>,
    state2: DataModel<T2>,
    state3: DataModel<T3>,
    state4: DataModel<T4>,
    fallback: SoilFallback = SoilFallbackDefaults.default(),
    content: @Composable (T1, T2, T3, T4) -> Unit,
) {
    ErrorBoundary(
        fallback = { fallback.errorFallback(it) },
        onReset = rememberQueriesErrorReset(),
    ) {
        Suspense(
            fallback = { fallback.suspenseFallback() },
        ) {
            Await(
                state1 = state1,
                state2 = state2,
                state3 = state3,
                state4 = state4,
                content = content,
            )
        }
    }
}
