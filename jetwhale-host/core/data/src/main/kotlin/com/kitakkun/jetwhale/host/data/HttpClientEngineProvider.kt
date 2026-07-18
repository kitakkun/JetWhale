package com.kitakkun.jetwhale.host.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

@ContributesTo(AppScope::class)
interface HttpClientEngineProvider {
    @Provides
    fun providesHttpClientEngine(): HttpClientEngine = CIO.create()
}
