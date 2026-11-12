package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/**
 * The absolute path of an installed jar to remove. A distinct type (rather than a bare `String`) so
 * this mutation key does not collide with [PluginInstallMutationKey] in dependency injection.
 */
@JvmInline
value class RemovePluginJarRequest(val jarPath: String)

/** Unloads the plugins of an installed jar, revokes its approval and deletes it. */
typealias RemovePluginJarMutationKey = MutationKey<Unit, RemovePluginJarRequest>
