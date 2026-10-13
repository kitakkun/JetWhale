package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult

/** The result for a request that names [action] but leaves out the [argument] it needs. */
internal fun NodeActionResult.Companion.missingArgument(action: NodeAction, argument: String): NodeActionResult = NodeActionResult(performed = false, message = "$action requires the '$argument' argument")

/** The result of a platform call that answered [handled]; [declined] explains a `false`. */
internal fun NodeActionResult.Companion.performedIf(handled: Boolean, declined: String): NodeActionResult = NodeActionResult(performed = handled, message = if (handled) null else declined)

/** The result for an action the node cannot run at all, for the [reason] given. */
internal fun NodeActionResult.Companion.notSupported(reason: String): NodeActionResult = NodeActionResult(performed = false, message = reason)
