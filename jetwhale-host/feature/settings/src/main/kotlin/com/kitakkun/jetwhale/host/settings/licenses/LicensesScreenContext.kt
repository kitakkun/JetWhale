package com.kitakkun.jetwhale.host.settings.licenses

import com.kitakkun.jetwhale.host.architecture.ScreenContext
import com.kitakkun.jetwhale.host.model.LibrariesQueryKey
import dev.zacsweers.metro.GraphExtension

@GraphExtension
interface LicensesScreenContext : ScreenContext {
    val librariesQueryKey: LibrariesQueryKey

    @GraphExtension.Factory
    fun interface Factory {
        fun createLicencesScreenContext(): LicensesScreenContext
    }
}
