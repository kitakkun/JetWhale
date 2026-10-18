package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi

/**
 * The host's own capabilities, handed to [JetWhaleHostPluginFactory.createPlugin]. It stays valid for
 * the plugin instance's whole life, so a plugin keeps whatever part of it it needs.
 */
public interface JetWhaleHostPluginContext {
    /** Runs adb through the executable the host finds, so a plugin needs no Android SDK setup of its own. */
    @ExperimentalJetWhaleApi
    public val adb: JetWhaleAdb
}
