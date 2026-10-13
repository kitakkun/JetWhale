package com.kitakkun.jetwhale.plugins.coroutines.agent

import java.lang.ref.WeakReference as JavaWeakReference

internal actual fun <T : Any> WeakReference(referent: T): WeakReference<T> = object : WeakReference<T> {
    private val reference = JavaWeakReference(referent)

    override fun get(): T? = reference.get()
}
