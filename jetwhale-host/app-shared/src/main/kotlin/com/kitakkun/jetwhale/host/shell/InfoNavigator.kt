package com.kitakkun.jetwhale.host.shell

/**
 * Everything the info dialog can ask the host to navigate to.
 *
 * The screens the app module owns declare their navigators here rather than in `app`, because the
 * implementations live in this module and `app` depends on it: an interface declared there could
 * not be bound from here.
 */
interface InfoNavigator {
    fun openLicenses()
}
