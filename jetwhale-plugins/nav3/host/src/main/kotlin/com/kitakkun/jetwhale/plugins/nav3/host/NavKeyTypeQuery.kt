package com.kitakkun.jetwhale.plugins.nav3.host

import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyTypeDescriptor

/**
 * A search of the key types by name: a type matches when its serial name contains [text], case
 * aside. A blank query matches every type.
 *
 * The serial name is all the agent sends of a type's name. It is the qualified class name unless
 * the app renames the type with `@SerialName`, and it ends with the simple name, so a part of
 * either matches.
 *
 * @param typedText the query as typed; surrounding whitespace is ignored.
 */
internal class NavKeyTypeQuery(typedText: String) {
    val text: String = typedText.trim()

    fun matches(type: NavKeyTypeDescriptor): Boolean = type.serialName.contains(text, ignoreCase = true)
}
