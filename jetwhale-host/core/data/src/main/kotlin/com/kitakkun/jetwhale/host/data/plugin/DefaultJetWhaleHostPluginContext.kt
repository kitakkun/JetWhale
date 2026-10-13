package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.data.adb.DefaultJetWhaleAdb
import com.kitakkun.jetwhale.host.data.util.AdbLocator
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@OptIn(ExperimentalJetWhaleApi::class)
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultJetWhaleHostPluginContext : JetWhaleHostPluginContext {
    override val adb: JetWhaleAdb = DefaultJetWhaleAdb(AdbLocator.ofCurrentProcess())
}
