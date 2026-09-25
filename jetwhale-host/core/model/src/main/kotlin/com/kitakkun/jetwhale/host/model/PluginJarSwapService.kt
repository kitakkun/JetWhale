package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.SharedFlow

/**
 * Changes the code behind plugins that are already running, taking their instances and scenes along
 * so that open sessions keep working: the dev plugins directory's hot reload, and an approved update
 * of an installed jar, both go through here.
 */
interface PluginJarSwapService {
    /**
     * Emits the `pluginId` of a plugin whose code was just swapped. The plugin screen observes this
     * to re-create its compose scene from the new code.
     */
    val pluginReloadedFlow: SharedFlow<String>

    /**
     * Replaces the running plugins of [jarPath] with the jar's current content, redefining their
     * classes in place when the runtime allows it so that instance state survives, and reloading them
     * otherwise. For the dev plugins directory: the in-place path attaches an instrumentation agent.
     */
    suspend fun hotSwap(jarPath: String)

    /**
     * Replaces the running plugins of [jarPath] with the jar's current content through a fresh
     * classloader; their instances are re-created.
     */
    suspend fun reload(jarPath: String)

    /** Disposes the instances and scenes of the plugins loaded from [jarPath] and unloads them. */
    suspend fun remove(jarPath: String)
}
