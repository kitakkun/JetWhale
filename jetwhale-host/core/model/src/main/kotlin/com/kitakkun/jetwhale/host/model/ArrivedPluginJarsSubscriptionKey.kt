package com.kitakkun.jetwhale.host.model

import kotlinx.collections.immutable.ImmutableList
import soil.query.MutationKey
import soil.query.SubscriptionKey

/**
 * Wrapper around the arrived jars. A distinct type (rather than a bare `ImmutableList`) so this
 * subscription key does not collide with other list-valued keys in dependency injection.
 */
@JvmInline
value class ArrivedPluginJars(val jars: ImmutableList<ArrivedPluginJar>)

/** Jars that appeared in the plugins directory at runtime and await the user's decision. */
typealias ArrivedPluginJarsSubscriptionKey = SubscriptionKey<ArrivedPluginJars>

/**
 * The jar path to put off. A distinct type (rather than a bare `String`) so this mutation key does
 * not collide with other string-keyed mutations in dependency injection.
 */
@JvmInline
value class PostponeArrivedPluginJarRequest(val jarPath: String)

/**
 * Puts off an arrived jar: it leaves the prompt but stays among the untrusted jars in the plugin
 * settings, where it can still be approved.
 */
typealias PostponeArrivedPluginJarMutationKey = MutationKey<Unit, PostponeArrivedPluginJarRequest>
