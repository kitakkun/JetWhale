package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/**
 * Installs an official catalog plugin. Coordinate candidates (release, snapshot fallback) are
 * derived from the running host's version inside the mutation, so callers only name the plugin.
 */
typealias OfficialPluginInstallMutationKey = MutationKey<Unit, OfficialPlugin>
