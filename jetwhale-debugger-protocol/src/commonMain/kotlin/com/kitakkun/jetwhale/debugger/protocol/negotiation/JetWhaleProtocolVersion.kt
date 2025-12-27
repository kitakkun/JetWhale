package com.kitakkun.jetwhale.debugger.protocol.negotiation

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Version information of JetWhale Debugger Protocol
 */
@JvmInline
@Serializable
value class JetWhaleProtocolVersion(val version: Int) {
    companion object {
        /**
         * Current version of JetWhale Debugger Protocol
         */
        val Current = JetWhaleProtocolVersion(1)
    }
}
