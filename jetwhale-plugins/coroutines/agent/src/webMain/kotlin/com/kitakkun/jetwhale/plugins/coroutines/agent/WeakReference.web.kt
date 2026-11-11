package com.kitakkun.jetwhale.plugins.coroutines.agent

// JS's WeakRef takes a Kotlin object on Kotlin/JS but only a JsReference on Kotlin/Wasm, so this
// shared web actual holds the referent strongly; a browser tab is short-lived enough that keeping
// registered jobs until they complete costs little.
internal actual fun <T : Any> WeakReference(referent: T): WeakReference<T> = object : WeakReference<T> {
    override fun get(): T = referent
}
