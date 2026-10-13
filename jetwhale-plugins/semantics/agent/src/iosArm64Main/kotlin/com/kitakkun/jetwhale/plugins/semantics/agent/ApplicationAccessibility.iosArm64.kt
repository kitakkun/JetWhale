package com.kitakkun.jetwhale.plugins.semantics.agent

// App Store review rejects apps that use private API, so the device slice leaves the
// libAccessibility call out.
internal actual fun enableApplicationAccessibilityOnSimulator() = Unit
