package com.kitakkun.jetwhale.plugins.nav3.protocol

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleEvent
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The pluginId shared by the Nav3 agent and host plugins. */
const val NAV3_PLUGIN_ID: String = "com.kitakkun.jetwhale.nav3"

/** Pushed by the agent whenever a tracked back stack changes, and once when it is registered. */
@SerialName("nav3/back_stack_changed")
@Serializable
data class BackStackChanged(val snapshot: NavBackStackSnapshot) : JetWhaleEvent

/** Pushed by the agent when a tracked back stack leaves composition and stops being observable. */
@SerialName("nav3/back_stack_unregistered")
@Serializable
data class BackStackUnregistered(val stackId: String) : JetWhaleEvent

/**
 * Asks the agent for everything the host needs to render: the current stacks and the key types the
 * app can construct from JSON. The host sends this once per connection, since the events above only
 * report *changes*.
 */
@SerialName("nav3/get_nav_state")
@Serializable
data object GetNavState : JetWhaleRequest<NavState>

/** Reply to [GetNavState]. */
@SerialName("nav3/nav_state")
@Serializable
data class NavState(
    val stacks: List<NavBackStackSnapshot>,
    /** Key types derived from the app's serializers; empty when none could be derived. */
    val keyTypes: List<NavKeyTypeDescriptor>,
)

/** Applies [operations] to the back stack registered as [stackId]. */
@SerialName("nav3/mutate_back_stack")
@Serializable
data class MutateBackStack(
    val stackId: String,
    val operations: List<NavBackStackOperation>,
) : JetWhaleRequest<MutationResult>

/**
 * Reply to [MutateBackStack]. [error] is null when the operations were applied; otherwise nothing
 * was applied and [error] says why. [snapshot] is the stack as it stands after the attempt (null
 * only when [stackId] names no registered stack).
 */
@SerialName("nav3/mutation_result")
@Serializable
data class MutationResult(
    val error: String?,
    val snapshot: NavBackStackSnapshot?,
)
