package com.kitakkun.jetwhale.debugger.host.data

import com.kitakkun.jetwhale.debugger.host.model.ADBTransportConfigurator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@ContributesBinding(AppScope::class)
@Inject
class DefaultADBTransportConfigurator : ADBTransportConfigurator {
    override fun mapLocalPortToDevicePort(localPort: Int) {
        ProcessBuilder().command("adb", "reverse", "tcp:$localPort", "tcp:$localPort")
            .redirectErrorStream(true)
            .start()
            .waitFor()
    }

    override fun resetPortMappingForHostPort(port: Int) {
        ProcessBuilder().command("adb", "reverse", "--remove", "tcp:$port")
            .start()
            .waitFor()
    }
}
