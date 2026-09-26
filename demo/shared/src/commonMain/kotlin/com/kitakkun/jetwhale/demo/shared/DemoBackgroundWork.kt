package com.kitakkun.jetwhale.demo.shared

import com.kitakkun.jetwhale.plugins.background.agent.BackgroundWorkSource

/**
 * The schedulers the demo shows in the Background Work plugin: the platform defaults, plus the
 * demo's WorkManager on Android.
 */
expect fun demoBackgroundWorkSources(): List<BackgroundWorkSource>
