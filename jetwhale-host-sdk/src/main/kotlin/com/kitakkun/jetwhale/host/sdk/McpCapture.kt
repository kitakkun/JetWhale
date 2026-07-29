package com.kitakkun.jetwhale.host.sdk

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * True while the composition is being read for an MCP client instead of drawn for the interactive
 * host window.
 *
 * Raised by every tool that hands a plugin's UI to an AI agent — `jetwhale.screenshot` for the
 * pixels and `jetwhale.getAccessibilityTree` for the semantics — because both carry the same
 * strings.
 *
 * A plugin whose [JetWhaleHostPluginUi.Content] shows sensitive values can read this to render them
 * redacted, keeping them visible on screen while hiding them from MCP-connected AI agents. Redact
 * the value itself rather than only what is painted: a masked pixel with the original string still
 * in its `Text` or `contentDescription` is handed over in full by the semantics tree.
 */
public val LocalIsMcpCapture: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }
