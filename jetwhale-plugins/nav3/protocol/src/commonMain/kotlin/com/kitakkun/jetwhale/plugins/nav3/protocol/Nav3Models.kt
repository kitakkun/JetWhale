package com.kitakkun.jetwhale.plugins.nav3.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** The stack id the agent uses when the app registers a back stack without naming one. */
const val DEFAULT_NAV_STACK_ID: String = "main"

/**
 * One key of a tracked back stack, as the agent observed it.
 *
 * A `NavKey` type is defined by the app being debugged, so the host cannot know it statically: this
 * carries both a rendering-friendly [display] and the machine-readable [key], which is the key
 * encoded with the app's own serializers. [key] is null when the agent could not encode it — the
 * entry is still listed and can still be popped or removed by index, it just cannot be copied into
 * a new push.
 *
 * Deliberately not named after Navigation's own entry types: this is an observation of a key at a
 * point in time, not a live entry, and it must not be mistaken for `androidx.navigation`'s
 * `NavBackStackEntry`.
 */
@Serializable
data class NavKeySnapshot(
    /** Serial name of the key's type (the `type` discriminator of [key]), or its class name. */
    val typeName: String,
    /** `toString()` of the key, for display. */
    val display: String,
    /** The key encoded with the app's serializers, or null when it is not encodable. */
    val key: JsonElement?,
)

/** A tracked back stack as the agent saw it: index 0 is the root, the last entry is the current one. */
@Serializable
data class NavBackStackSnapshot(
    val stackId: String,
    val entries: List<NavKeySnapshot>,
)

/**
 * A `NavKey` type the agent knows how to construct from JSON, derived from the app's serializers.
 *
 * [template] is a ready-to-edit JSON skeleton — the discriminator plus one placeholder per field —
 * so the host can offer a fill-in-the-blanks form (or hand it to an AI agent) without the host ever
 * having seen the app's key types.
 */
@Serializable
data class NavKeyTypeDescriptor(
    val serialName: String,
    val fields: List<NavKeyFieldDescriptor>,
    val template: JsonElement,
)

/** One field of a [NavKeyTypeDescriptor]. */
@Serializable
data class NavKeyFieldDescriptor(
    val name: String,
    /** Human-readable type, e.g. `String`, `Int`, `List<String>`, `enum(Grid|List)`. */
    val type: String,
    /** Whether the field has a default value and may be omitted. */
    val optional: Boolean,
    val nullable: Boolean,
)
