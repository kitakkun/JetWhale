package com.kitakkun.jetwhale.host.settings.licenses

import com.kitakkun.jetwhale.host.architecture.ScreenContext
import com.kitakkun.jetwhale.host.model.LibrariesQueryKey
import dev.zacsweers.metro.Inject

@Inject
class LicensesScreenContext(
    val librariesQueryKey: LibrariesQueryKey,
) : ScreenContext
