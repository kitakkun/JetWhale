package com.kitakkun.jetwhale.plugins.background.agent

// A web page has no background scheduler of its own to read (service workers are out of reach).
actual fun BackgroundWorkSource.Companion.platformDefaults(): List<BackgroundWorkSource> = emptyList()
