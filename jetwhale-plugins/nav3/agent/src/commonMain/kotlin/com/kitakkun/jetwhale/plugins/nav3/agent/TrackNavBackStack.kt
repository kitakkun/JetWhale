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
// The host drives the registered stack, so it has to be the app's own mutable list.
@Suppress("KOTRAIL_MUTABLE_COLLECTION_IN_PUBLIC_API")
@Composable
fun <K : NavKey> JetWhaleNav3AgentPlugin<K>.TrackNavBackStack(
    backStack: MutableList<K>,
    stackId: String = DEFAULT_NAV_STACK_ID,
) {
    DisposableEffect(key1 = this, key2 = backStack, key3 = stackId) {
        registerBackStack(backStack = backStack, stackId = stackId)
        onDispose { unregisterBackStack(stackId) }
    }
}
