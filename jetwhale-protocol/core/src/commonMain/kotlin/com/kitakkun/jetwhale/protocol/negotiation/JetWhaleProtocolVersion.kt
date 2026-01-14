package com.kitakkun.jetwhale.protocol.negotiation

import com.kitakkun.jetwhale.protocol.JetWhaleSerialNames
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Version information of JetWhale Debugger Protocol
 */
@JvmInline
@SerialName(JetWhaleSerialNames.MODEL_PROTOCOL_VERSION)
@Serializable
public value class JetWhaleProtocolVersion(public val version: Int) {
    public companion object {
        /**
         * Current version of JetWhale Debugger Protocol
         */
        public val Current: JetWhaleProtocolVersion = JetWhaleProtocolVersion(1)
    }
}
