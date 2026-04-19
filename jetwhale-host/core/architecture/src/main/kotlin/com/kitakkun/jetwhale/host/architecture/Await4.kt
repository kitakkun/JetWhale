package com.kitakkun.jetwhale.host.architecture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import soil.plant.compose.reacty.AwaitHost
import soil.plant.compose.reacty.Catch
import soil.plant.compose.reacty.CatchScope
import soil.plant.compose.reacty.LocalAwaitHost
import soil.query.core.DataModel
import soil.query.core.Reply
import soil.query.core.getOrThrow
import soil.query.core.isNone
import soil.query.core.uuid

@PublishedApi
internal data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

@PublishedApi
internal inline fun <T1, T2, T3, T4, R> Reply.Companion.combine(
    r1: Reply<T1>,
    r2: Reply<T2>,
    r3: Reply<T3>,
    r4: Reply<T4>,
    transform: (T1, T2, T3, T4) -> R,
): Reply<R> = when {
    r1.isNone || r2.isNone || r3.isNone || r4.isNone -> none()
    else -> some(transform(r1.getOrThrow(), r2.getOrThrow(), r3.getOrThrow(), r4.getOrThrow()))
}

@Composable
inline fun <T1, T2, T3, T4> Await(
    state1: DataModel<T1>,
    state2: DataModel<T2>,
    state3: DataModel<T3>,
    state4: DataModel<T4>,
    key: Any? = null,
    host: AwaitHost = LocalAwaitHost.current,
    errorPredicate: (DataModel<*>) -> Boolean = { it.reply.isNone },
    errorFallback: @Composable CatchScope.(err: Throwable) -> Unit = { Throw(error = it) },
    crossinline content: @Composable (T1, T2, T3, T4) -> Unit,
) {
    val id = remember(key) { key ?: uuid() }
    when (val reply = Reply.combine(state1.reply, state2.reply, state3.reply, state4.reply, ::Quadruple)) {
        is Reply.Some -> content(reply.value.first, reply.value.second, reply.value.third, reply.value.fourth)
        is Reply.None -> Catch(state1, state2, state3, state4, filter = errorPredicate, content = errorFallback)
    }
    LaunchedEffect(id, state1, state2, state3, state4) {
        host[id] = listOf(state1, state2, state3, state4).any { it.isAwaited() }
    }
    DisposableEffect(id) {
        onDispose {
            host.remove(id)
        }
    }
}
