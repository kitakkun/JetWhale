package com.kitakkun.jetwhale.plugins.background.agent

// No system scheduler an app can read exists here.
actual fun BackgroundWorkSource.Companion.platformDefaults(): List<BackgroundWorkSource> = emptyList()
