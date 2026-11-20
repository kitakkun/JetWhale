package com.kitakkun.jetwhale.plugins.semantics.agent

import org.w3c.dom.MutationObserverInit

/**
 * Options that have a `MutationObserver` watch every node added under its target or removed from
 * it, and with [includeAttributeAndTextChanges] every attribute and text change there as well.
 *
 * Written in JavaScript because kotlinx-browser's `MutationObserverInit()` sets each option it is
 * not given to `null` on JS, and the browser rejects a `null` `attributeFilter`.
 */
@OptIn(ExperimentalWasmJsInterop::class)
internal fun subtreeMutationObserverInit(includeAttributeAndTextChanges: Boolean): MutationObserverInit = js("({ childList: true, subtree: true, attributes: includeAttributeAndTextChanges, characterData: includeAttributeAndTextChanges })")
