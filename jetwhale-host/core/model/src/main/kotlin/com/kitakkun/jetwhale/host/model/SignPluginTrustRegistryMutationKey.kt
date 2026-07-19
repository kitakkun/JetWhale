package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/**
 * Persists whether the plugin trust registry is HMAC-signed with a key held in the OS credential
 * store. Off by default: when disabled the credential store is never touched and the registry is
 * read and written unsigned. The SHA-256 content pinning of approved jars applies regardless.
 */
interface SignPluginTrustRegistryMutationKey : MutationKey<Unit, Boolean>
