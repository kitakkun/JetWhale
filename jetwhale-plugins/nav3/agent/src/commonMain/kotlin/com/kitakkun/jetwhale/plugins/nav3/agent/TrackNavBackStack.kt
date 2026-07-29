package com.kitakkun.jetwhale.plugins.nav3.agent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.plugins.nav3.protocol.DEFAULT_NAV_STACK_ID

/**
 * Mirrors [backStack] to the host for as long as this composable stays in composition.
 *
 * Drop it next to the `NavDisplay` that renders the stack:
 *
 * ```kotlin
 * val backStack = rememberNavBackStack(configuration, Home)
 * nav3Plugin.TrackNavBackStack(backStack)
 * NavDisplay(backStack = backStack, ...)
 * ```
 *
 * @param stackId Names the stack for the host; give each stack its own id when the app nests
 *   navigation.
 */
@Composable
fun <K : NavKey> JetWhaleNav3AgentPlugin<K>.TrackNavBackStack(
    backStack: MutableList<K>,
    stackId: String = DEFAULT_NAV_STACK_ID,
) {
    DisposableEffect(this, backStack, stackId) {
        registerBackStack(backStack, stackId)
        onDispose { unregisterBackStack(stackId) }
    }
}
