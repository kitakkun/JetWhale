package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

fun <T : NavKey> NavBackStack<T>.addSingleTop(navKey: T) {
    removeIf { it == navKey }
    add(navKey)
}

fun <T : NavKey> NavBackStack<T>.addSingleTop(index: Int, navKey: T) {
    removeIf { it == navKey }
    add(index, navKey)
}
