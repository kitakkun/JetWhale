package com.kitakkun.jetwhale.host.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlin.time.Clock

@ContributesTo(AppScope::class)
interface ClockProvider {
    @Provides
    fun providesClock(): Clock = Clock.System
}
