package com.kitakkun.jetwhale.host.architecture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import soil.query.compose.MutationObject

/**
 * Failure counterpart to Soil's `MutatedEffect`.
 *
 * Observes the mutation's error state and invokes [block] once per new failure (keyed on
 * `errorUpdatedAt`). Pair it with `mutateAsync` so failures flow through the mutation state
 * instead of being swallowed by a fire-and-forget `mutate`.
 */
@Composable
fun MutationErrorEffect(
    mutation: MutationObject<*, *>,
    block: suspend (Throwable) -> Unit,
) {
    val error = mutation.error
    LaunchedEffect(mutation.errorUpdatedAt) {
        if (error != null) block(error)
    }
}
