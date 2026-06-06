package com.kitakkun.jetwhale.host.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.RetainedValuesStoreRegistry
import androidx.compose.runtime.retain.retainRetainedValuesStoreRegistry
import androidx.navigation3.runtime.NavEntryDecorator

@Composable
fun <T : Any> rememberRetainedNavEntryDecorator(): NavEntryDecorator<T> {
    val registry = retainRetainedValuesStoreRegistry()
    return remember(registry) { RetainedNavEntryDecorator(registry) }
}

private class RetainedNavEntryDecorator<T : Any>(
    private val registry: RetainedValuesStoreRegistry,
) : NavEntryDecorator<T>(
    decorate = { entry ->
        registry.LocalRetainedValuesStoreProvider(entry.contentKey) {
            entry.Content()
        }
    },
    onPop = { contentKey ->
        registry.clearChild(contentKey)
    },
)
