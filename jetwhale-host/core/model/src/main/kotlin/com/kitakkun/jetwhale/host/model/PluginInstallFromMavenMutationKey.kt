package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/**
 * Installs a plugin from a Maven repository. The input is an ordered list of candidate
 * coordinates: they are attempted in order within a single mutation, so intermediate failures
 * (e.g. a release artifact not published yet, before its snapshot fallback) never surface as the
 * mutation's error — only the last candidate's failure does.
 */
typealias PluginInstallFromMavenMutationKey = MutationKey<Unit, List<MavenCoordinates>>
