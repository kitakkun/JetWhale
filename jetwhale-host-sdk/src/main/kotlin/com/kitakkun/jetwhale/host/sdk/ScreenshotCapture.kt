package com.kitakkun.jetwhale.host.sdk

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * True while the composition is being rendered for an MCP screenshot capture (the
 * `jetwhale.screenshot` tool) instead of the interactive host window.
 *
 * A plugin whose [JetWhaleHostPluginUi.Content] shows sensitive values can read this to render
 * them redacted in captures, keeping them visible on screen while hiding them from MCP-connected
 * AI agents.
 */
public val LocalIsScreenshotCapture: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }
