package com.kitakkun.jetwhale.plugins.actions.agent

// A page may open a URL only in response to a user gesture; a debugger-triggered window.open is
// what popup blockers exist to stop, so the web offers no built-in actions.
actual fun DebugActionsBuilder.platformBuiltInActions() = Unit
