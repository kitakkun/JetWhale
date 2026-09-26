package com.kitakkun.jetwhale.plugins.actions.agent

/**
 * Declares, in a "Built-in" group, the actions every app on this platform can use without code of
 * its own:
 * - Android: restart the app, open a deep link in it, switch its dark mode (Android 12+) and its
 *   language (Android 13+).
 * - iOS, macOS and the JVM: open a URL.
 * - The web: none, since a page cannot open a URL outside a user gesture without a popup blocker
 *   stepping in.
 */
expect fun DebugActionsBuilder.platformBuiltInActions()
