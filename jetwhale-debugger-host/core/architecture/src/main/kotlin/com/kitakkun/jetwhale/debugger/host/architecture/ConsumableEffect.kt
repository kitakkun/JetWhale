package com.kitakkun.jetwhale.debugger.host.architecture

class MutableConsumableEffect<T> : ConsumableEffect<T> {
    val queue: ArrayDeque<T> = ArrayDeque()

    fun enqueue(effect: T) {
        queue.add(effect)
    }
}

interface ConsumableEffect<T> {
    suspend fun consume(block: (T) -> Unit) {
    }
}
