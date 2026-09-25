package com.kitakkun.jetwhale.plugins.coroutines.agent

/** A reference that does not by itself keep [T] from being garbage-collected. */
internal interface WeakReference<T : Any> {
    /** The referent, or null once it has been collected. */
    fun get(): T?
}

internal expect fun <T : Any> WeakReference(referent: T): WeakReference<T>
