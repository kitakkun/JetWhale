package com.kitakkun.jetwhale.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.shell.NavigationBus
import com.kitakkun.jetwhale.host.shell.applyNavCommand

/**
 * Connects the navigation bus to the back stack, in both directions.
 *
 * Applying commands here is what makes this the only writer of the back stack: every navigator
 * sends a [com.kitakkun.jetwhale.host.shell.NavCommand] and nothing else touches the stack.
 * Publishing the result back into the bus is what gives navigators — and the MCP server through
 * them — something to read.
 */
@Composable
fun NavigatorEffect(
    backStack: NavBackStack<NavKey>,
    navigationBus: NavigationBus,
) {
    LaunchedEffect(backStack, navigationBus) {
        navigationBus.commands.collect { command ->
            backStack.applyNavCommand(command)
        }
    }

    LaunchedEffect(backStack, navigationBus) {
        snapshotFlow { backStack.toList() }.collect(navigationBus::publishBackStack)
    }
}
