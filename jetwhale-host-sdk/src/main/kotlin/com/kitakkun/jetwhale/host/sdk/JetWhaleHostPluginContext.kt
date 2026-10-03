package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi

/**
 * The host's own capabilities, handed to [JetWhaleHostPluginFactory.createPlugin]. One object serves
 * every plugin instance, in the `host` session and in each app's alike, and it stays valid for the
 * instance's whole life, so a plugin keeps whatever part of it it needs.
 */
public interface JetWhaleHostPluginContext {
    /** Runs adb through the executable the host finds, so a plugin needs no Android SDK setup of its own. */
    @ExperimentalJetWhaleApi
    public val adb: JetWhaleAdb
}
