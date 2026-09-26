package com.kitakkun.jetwhale.plugins.actions.host

import com.kitakkun.jetwhale.plugins.actions.protocol.ActionDescriptor

/**
 * The actions the palette lists for [query]: every word of the query must appear, case aside, in
 * the action's group or title. Pinned actions come first, then those whose title starts with the
 * query; otherwise the app's registration order is kept.
 */
internal fun searchActions(actions: List<ActionDescriptor>, query: String, pinnedIds: Set<String>): List<ActionDescriptor> {
    val words = query.trim().lowercase().split(' ').filter(String::isNotEmpty)
    val trimmedQuery = query.trim()
    return actions
        .filter { action ->
            val haystack = listOfNotNull(action.group, action.title).joinToString(" ").lowercase()
            words.all(haystack::contains)
        }
        .sortedWith(
            compareBy<ActionDescriptor>({ it.id !in pinnedIds }, { trimmedQuery.isEmpty() || !it.title.startsWith(trimmedQuery, ignoreCase = true) }),
        )
}
