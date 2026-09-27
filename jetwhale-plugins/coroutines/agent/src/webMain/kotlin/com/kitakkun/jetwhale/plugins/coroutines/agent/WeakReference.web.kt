package com.kitakkun.jetwhale.plugins.coroutines.agent

// JS's WeakRef takes a Kotlin object directly on Kotlin/JS but only as a JsReference on Kotlin/Wasm,
// so one shared web actual cannot use it; a browser tab is also short-lived enough that holding
// the registered jobs until they complete costs little.
internal actual fun <T : Any> WeakReference(referent: T): WeakReference<T> = object : WeakReference<T> {
    override fun get(): T = referent
}
