package com.kitakkun.jetwhale.host.shell.navigator

import com.kitakkun.jetwhale.host.settings.licenses.LicensesNavKey
import com.kitakkun.jetwhale.host.shell.InfoNavigator
import com.kitakkun.jetwhale.host.shell.NavCommand
import com.kitakkun.jetwhale.host.shell.NavigationBus
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@Inject
@ContributesBinding(AppScope::class)
class DefaultInfoNavigator(
    private val navigationBus: NavigationBus,
) : InfoNavigator {
    override fun openLicenses() {
        navigationBus.send(NavCommand.ShowSingleTop(LicensesNavKey))
    }
}
