package com.kitakkun.jetwhale.protocol.negotiation

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Version information of JetWhale Debugger Protocol
 */
@JvmInline
@Serializable
public value class JetWhaleProtocolVersion(public val version: Int) {
    public companion object {
        /**
         * Current version of JetWhale Debugger Protocol
         */
        public val Current: JetWhaleProtocolVersion = JetWhaleProtocolVersion(1)
    }
}
