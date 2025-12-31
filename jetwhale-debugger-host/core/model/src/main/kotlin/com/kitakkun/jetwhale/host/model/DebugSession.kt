package com.kitakkun.jetwhale.host.model

data class DebugSession(
    val id: String,
    val name: String?,
    val isActive: Boolean,
) {
    private val shortId: String
        get() = id.take(6)

    val displayName: String
        get() = name?.let { "$it ($shortId)" } ?: shortId
}
