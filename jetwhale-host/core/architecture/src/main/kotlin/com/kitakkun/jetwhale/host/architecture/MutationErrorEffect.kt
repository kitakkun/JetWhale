package com.kitakkun.jetwhale.host.architecture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.autoSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filterNotNull
import soil.query.compose.MutationErrorObject
import soil.query.compose.MutationObject

/**
 * Failure counterpart to Soil's `MutatedEffect`: triggers [block] when a mutation fails.
 *
 * Modeled on `MutatedEffect`. It observes the error state via [snapshotFlow] and uses
 * [keySelector] to identify whether a failure has already been handled, so [block] runs once
 * per distinct failure (deduped across recompositions and restored state). Pair it with
 * `mutateAsync` so failures flow through the mutation state instead of being swallowed by a
 * fire-and-forget `mutate`.
 *
 * @param T Type of the return value from the mutation.
 * @param U Type of the key to identify whether the failure has already been handled.
 * @param mutation The MutationObject whose error will be observed.
 * @param keySelector Calculates a key to identify whether the failure has already been handled.
 * @param keySaver A Saver to persist and restore the last consumed key.
 * @param block A callback to handle the error. Called only when the key differs from the previous one.
 */
@Composable
fun <T, U : Any> MutationErrorEffect(
    mutation: MutationObject<T, *>,
    keySelector: (MutationErrorObject<T, *>) -> U,
    keySaver: Saver<U?, out Any> = autoSaver(),
    block: suspend (error: Throwable) -> Unit,
) {
    val mutationState by rememberUpdatedState(mutation)
    var lastConsumedKey by rememberSaveable(stateSaver = keySaver) { mutableStateOf(null) }
    LaunchedEffect(Unit) {
        snapshotFlow { mutationState as? MutationErrorObject }
            .filterNotNull()
            .collect {
                val errorKey = keySelector(it)
                if (lastConsumedKey != errorKey) {
                    lastConsumedKey = errorKey
                    block(it.error)
                }
            }
    }
}

/**
 * A [MutationErrorEffect] keyed by `errorUpdatedAt`, so [block] is invoked once per new failure.
 *
 * @param T Type of the return value from the mutation.
 * @param mutation The MutationObject whose error will be observed.
 */
@Suppress("NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress")
@Composable
inline fun <T> MutationErrorEffect(
    mutation: MutationObject<T, *>,
    noinline block: suspend (error: Throwable) -> Unit,
) {
    MutationErrorEffect(
        mutation = mutation,
        keySelector = { it.errorUpdatedAt },
        block = block,
    )
}
