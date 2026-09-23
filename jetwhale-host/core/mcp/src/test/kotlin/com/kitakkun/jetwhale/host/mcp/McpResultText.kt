package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpContent
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult

/** The text of a single-block result, which is what every command under test answers with. */
@OptIn(ExperimentalJetWhaleApi::class)
val JetWhaleMcpResult.text: String get() = (content.single() as JetWhaleMcpContent.Text).text
