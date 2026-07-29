package com.kitakkun.jetwhale.host.sdk

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * True while the composition is being rendered for an MCP capture instead of for the interactive
 * host window — both `jetwhale.screenshot` and `jetwhale.getAccessibilityTree` raise it, since the
 * semantics tree carries the same strings the pixels do.
 *
 * A plugin whose [JetWhaleHostPluginUi.Content] shows sensitive values can read this to render
 * them redacted in captures, keeping them visible on screen while hiding them from MCP-connected
 * AI agents.
 *
 * The name is historical: it predates the semantics tree being captured the same way.
 */
public val LocalIsScreenshotCapture: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }
