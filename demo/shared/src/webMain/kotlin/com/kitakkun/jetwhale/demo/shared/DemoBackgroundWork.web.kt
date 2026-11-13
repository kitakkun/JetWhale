package com.kitakkun.jetwhale.demo.shared

import com.kitakkun.jetwhale.plugins.background.agent.BackgroundWorkSource
import com.kitakkun.jetwhale.plugins.background.agent.platformDefaults

actual fun demoBackgroundWorkSources(): List<BackgroundWorkSource> = BackgroundWorkSource.platformDefaults()
