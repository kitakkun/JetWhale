package com.kitakkun.jetwhale.host.model

import soil.query.SubscriptionKey

/**
 * Whether plugin trust-registry signing is currently enabled, i.e. a signing key exists in the OS
 * credential store. A dedicated wrapper type (rather than a bare `Boolean`) so this subscription key
 * has a distinct type for dependency injection.
 */
data class TrustRegistrySigningState(val enabled: Boolean)

/**
 * Reflects [PluginTrustService.signingEnabledFlow] for the settings toggle. See [PluginTrustService].
 */
typealias SignPluginTrustRegistrySubscriptionKey = SubscriptionKey<TrustRegistrySigningState>
