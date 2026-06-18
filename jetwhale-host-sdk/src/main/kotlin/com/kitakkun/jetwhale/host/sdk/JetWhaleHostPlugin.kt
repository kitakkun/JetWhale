package com.kitakkun.jetwhale.host.sdk

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
    /** Called once when this plugin instance is created, before it is shown or used. */
    protected open fun onCreate() {}

    /** Called when this plugin instance is disposed (session closed, plugin disabled, or reloaded). */
    public open fun onDispose() {}

    // -- runtime hooks (not for plugin authors) -------------------------------

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
