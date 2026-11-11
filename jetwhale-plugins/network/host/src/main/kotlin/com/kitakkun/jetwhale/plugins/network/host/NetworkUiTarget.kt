package com.kitakkun.jetwhale.plugins.network.host

/** A part of the Network Inspector that an MCP command operates, ringed while the command runs. */
internal sealed interface NetworkUiTarget {
    data object TrafficList : NetworkUiTarget

    data object TransactionDetail : NetworkUiTarget

    data object ClearButton : NetworkUiTarget

    data object MocksTab : NetworkUiTarget

    data object MockingSwitch : NetworkUiTarget

    data object MockRuleList : NetworkUiTarget
}
