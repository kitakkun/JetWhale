package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/**
 * The absolute jar path to approve. A distinct type (rather than a bare `String`) so this mutation
 * key does not collide with [PluginInstallMutationKey] in dependency injection.
 */
data class TrustPluginRequest(val jarPath: String)

/** Approves a surfaced untrusted jar: pins its content hash and loads it. */
typealias TrustPluginMutationKey = MutationKey<Unit, TrustPluginRequest>
