package com.kitakkun.jetwhale.plugins.nav3.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A single edit to a tracked back stack, sent from the host to the agent inside [MutateBackStack].
 *
 * Operations are applied in order and as one unit: if any of them is invalid (an out-of-range
 * index, a key the app's serializers cannot decode), none of them is applied. Indices always refer
 * to the stack *as it is when that operation runs*, with 0 being the root.
 */
@Serializable
sealed interface NavBackStackOperation {
    /** Inserts [key] at [index], or on top of the stack when [index] is null. */
    @SerialName("nav3/op/push")
    @Serializable
    data class Push(val key: JsonElement, val index: Int?) : NavBackStackOperation

    /** Removes the top [count] entries. */
    @SerialName("nav3/op/pop")
    @Serializable
    data class Pop(val count: Int) : NavBackStackOperation

    /**
     * Removes everything above [index], leaving that entry on top. When [inclusive] is true the
     * entry at [index] is removed as well.
     */
    @SerialName("nav3/op/pop_to")
    @Serializable
    data class PopTo(val index: Int, val inclusive: Boolean) : NavBackStackOperation

    /** Removes the single entry at [index], keeping everything above it. */
    @SerialName("nav3/op/remove_at")
    @Serializable
    data class RemoveAt(val index: Int) : NavBackStackOperation

    /** Moves the entry at [index] to the top of the stack, keeping the rest in order. */
    @SerialName("nav3/op/move_to_top")
    @Serializable
    data class MoveToTop(val index: Int) : NavBackStackOperation

    /** Replaces the whole stack with [keys], root first. */
    @SerialName("nav3/op/replace_all")
    @Serializable
    data class ReplaceAll(val keys: List<JsonElement>) : NavBackStackOperation
}
