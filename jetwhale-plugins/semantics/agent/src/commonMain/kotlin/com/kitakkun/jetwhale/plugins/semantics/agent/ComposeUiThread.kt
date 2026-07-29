package com.kitakkun.jetwhale.plugins.semantics.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a block on the thread that owns a composition.
 *
 * Semantics may only be read — and semantics actions may only be invoked — from that thread, while
 * the agent's message handlers run on the agent's own dispatcher. Which thread that is, and how to
 * get onto it cheaply, differs per platform, so it is a parameter of [SemanticsOwnerNodeSource]
 * rather than a hardcoded dispatcher.
 */
interface ComposeUiThread {
    suspend fun <T> await(block: () -> T): T
}

/**
 * Hops to `Dispatchers.Main`, the UI thread on every Compose Multiplatform target.
 *
 * Android has a cheaper option that skips the dispatch when the caller is already on the main
 * thread — the Android probe installs it — so this is the default for everything else.
 */
object MainDispatcherComposeUiThread : ComposeUiThread {
    override suspend fun <T> await(block: () -> T): T = withContext(Dispatchers.Main) { block() }
}
