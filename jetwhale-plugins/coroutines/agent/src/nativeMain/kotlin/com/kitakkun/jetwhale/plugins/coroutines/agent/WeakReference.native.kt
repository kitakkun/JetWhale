package com.kitakkun.jetwhale.plugins.coroutines.agent

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference as NativeWeakReference

@OptIn(ExperimentalNativeApi::class)
internal actual fun <T : Any> WeakReference(referent: T): WeakReference<T> = object : WeakReference<T> {
    private val reference = NativeWeakReference(referent)

    override fun get(): T? = reference.get()
}
