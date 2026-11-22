package com.kitakkun.jetwhale.plugins.semantics.agent

import org.w3c.dom.Element

/**
 * Calls [onScroll] after each scroll of this element or of any element under it, until the returned
 * handle is closed. A `scroll` event does not bubble, so it is heard in the capture phase.
 */
@OptIn(ExperimentalWasmJsInterop::class)
internal fun Element.watchScrolls(onScroll: () -> Unit): AutoCloseable {
    val abortController = newAbortController()
    addCapturingScrollListener(this, abortController, onScroll)
    return AutoCloseable { abortListeners(abortController) }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun newAbortController(): JsAny = js("new AbortController()")

@OptIn(ExperimentalWasmJsInterop::class)
private fun addCapturingScrollListener(target: Element, abortController: JsAny, onScroll: () -> Unit) {
    js("target.addEventListener('scroll', () => onScroll(), { capture: true, passive: true, signal: abortController.signal })")
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun abortListeners(abortController: JsAny) {
    js("abortController.abort()")
}
