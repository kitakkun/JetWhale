package com.kitakkun.jetwhale.host.architecture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

typealias EventFlow<T> = MutableSharedFlow<T>

@Composable
context(_: ScreenContext)
fun <T> rememberEventFlow(): EventFlow<T> = remember {
    MutableSharedFlow(
        replay = 1,
        extraBufferCapacity = 10,
    )
}

@Composable
fun <T> EventEffect(
    eventFlow: EventFlow<T>,
    block: suspend (T) -> Unit,
) {
    LaunchedEffect(eventFlow) {
        eventFlow.collect {
            launch {
                try {
                    block(it)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }
}
