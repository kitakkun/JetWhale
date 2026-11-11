package com.kitakkun.jetwhale.plugins.example.host

/** A part of the Example plugin's UI that an MCP command operates, ringed while the command runs. */
internal sealed interface ExampleUiTarget {
    data object SendPingButton : ExampleUiTarget

    data object EventLog : ExampleUiTarget
}
