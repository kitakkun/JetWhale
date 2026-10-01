package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/**
 * The absolute jar path to approve, and the hash of the content the user was shown when they approved
 * it, or null to approve the jar's current content. [replaceOtherVersions] removes the installed jars
 * of other versions of its plugins once it loads. A distinct type (rather than a bare `String`) so
 * this mutation key does not collide with [PluginInstallMutationKey] in dependency injection.
 */
data class TrustPluginRequest(val jarPath: String, val approvedSha256: String?, val replaceOtherVersions: Boolean)

/** Approves a surfaced untrusted jar: pins its content hash and loads it. */
typealias TrustPluginMutationKey = MutationKey<Unit, TrustPluginRequest>
