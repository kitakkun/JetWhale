package com.kitakkun.jetwhale.host.sdk

import kotlinx.coroutines.CoroutineScope

/**
 * Base class for a JetWhale host plugin. This base is **pure**: it has only a lifecycle and no
 * messaging — use it for plugins that don't talk to an agent (e.g. a host-only tool, declared with
 * `"requiresAgent": false` in the manifest).
 *
 * Add capabilities by combining types:
 * - [JetWhaleMessagingHostPlugin] (extend it instead) — to exchange messages with an agent counterpart.
 * - [JetWhaleHostPluginUi] (implement it) — to render a Compose UI.
 */
public abstract class JetWhaleHostPlugin {
    private var boundPluginScope: CoroutineScope? = null

    /**
     * Scope tied to this plugin instance's lifetime: available from [onCreate], cancelled by the
     * runtime when the instance is disposed. Launch background work here so it can never outlive
     * the instance.
     */
    protected val pluginScope: CoroutineScope
        get() = checkNotNull(boundPluginScope) {
            "pluginScope is only available after the plugin instance has been bound (in or after onCreate())."
        }

    /** Called once when this plugin instance is created, before it is shown or used. */
    protected open fun onCreate() {}

    /** Called when this plugin instance is disposed (session closed, plugin disabled, or reloaded). */
    public open fun onDispose() {}

    // -- runtime hooks (not for plugin authors) -------------------------------

    /** Binds the instance-scoped coroutine scope. Called once, before [onCreate]. */
    @InternalJetWhaleHostApi
    public fun bindPluginScope(scope: CoroutineScope) {
        boundPluginScope = scope
    }

    @InternalJetWhaleHostApi
    public fun dispatchCreate() {
        onCreate()
    }

    @InternalJetWhaleHostApi
    public fun dispatchDispose() {
        onDispose()
    }
}

/**
 * Marks host-runtime-only entry points that plugin authors must not call. The host wires these when
 * it creates a plugin instance.
 */
@RequiresOptIn(message = "This is a JetWhale host-runtime API and must not be called by plugin code.")
@Retention(AnnotationRetention.BINARY)
public annotation class InternalJetWhaleHostApi
