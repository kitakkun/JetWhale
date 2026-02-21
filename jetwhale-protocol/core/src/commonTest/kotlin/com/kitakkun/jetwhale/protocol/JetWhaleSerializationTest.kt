package com.kitakkun.jetwhale.protocol

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.serialization.JetWhaleJson
import kotlinx.serialization.json.Json

abstract class JetWhaleSerializationTest {
    @OptIn(InternalJetWhaleApi::class)
    val json: Json = JetWhaleJson
}
