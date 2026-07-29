package com.kitakkun.jetwhale.host.shell.navigator

import com.kitakkun.jetwhale.host.settings.licenses.LicensesNavigator
import com.kitakkun.jetwhale.host.shell.NavCommand
import com.kitakkun.jetwhale.host.shell.NavigationBus
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@Inject
@ContributesBinding(AppScope::class)
class DefaultLicensesNavigator(
    private val navigationBus: NavigationBus,
) : LicensesNavigator {
    override fun back() {
        navigationBus.send(NavCommand.PopTop)
    }
}
