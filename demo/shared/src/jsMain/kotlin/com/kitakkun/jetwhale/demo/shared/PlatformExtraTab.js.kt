package com.kitakkun.jetwhale.demo.shared

import androidx.compose.runtime.Composable

actual val platformExtraTabLabel: String? = null

// This platform contributes no extra tab, so the screen is empty.
@Composable
actual fun PlatformExtraTabScreen() {}
