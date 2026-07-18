package com.kitakkun.jetwhale.host.model

import kotlinx.collections.immutable.ImmutableList
import soil.query.SubscriptionKey

/**
 * Wrapper around the untrusted jar paths. A distinct type (rather than a bare
 * `ImmutableList<String>`) so this subscription key does not collide with
 * [FailedPluginJarPathsSubscriptionKey] in dependency injection.
 */
data class UntrustedPluginJars(val paths: ImmutableList<String>)

/**
 * Jars present in the plugins directory that were not loaded because they are not trusted (never
 * approved, or their content changed after approval). Surfaced so the user can review and approve
 * them. See [PluginTrustService].
 */
typealias UntrustedPluginJarPathsSubscriptionKey = SubscriptionKey<UntrustedPluginJars>
