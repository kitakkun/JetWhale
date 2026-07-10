package com.kitakkun.jetwhale.demo.shared

import androidx.compose.runtime.Composable

/**
 * Label for an extra, platform-only demo tab shown alongside "Example plugin"/"Network plugin"
 * (e.g. an OkHttp-based network screen on Android, since OkHttp only targets JVM/Android) — `null`
 * on platforms with nothing extra to show.
 */
expect val platformExtraTabLabel: String?

/** Content for the extra tab described by [platformExtraTabLabel]; a no-op where that's `null`. */
@Composable
expect fun PlatformExtraTabScreen()
