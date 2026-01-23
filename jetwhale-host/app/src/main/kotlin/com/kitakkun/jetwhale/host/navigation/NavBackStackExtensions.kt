package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

inline fun <reified T : NavKey> NavBackStack<T>.addSingleTopByType(navKey: T) {
    removeIf { it::class == navKey::class }
    add(navKey)
}

fun <T : NavKey> NavBackStack<T>.addSingleTop(navKey: T) {
    removeIf { it == navKey }
    add(navKey)
}
