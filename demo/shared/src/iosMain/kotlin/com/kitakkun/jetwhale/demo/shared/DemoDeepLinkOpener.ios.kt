package com.kitakkun.jetwhale.demo.shared

import com.kitakkun.jetwhale.plugins.deeplinks.agent.DeepLinkOpener
import com.kitakkun.jetwhale.plugins.deeplinks.agent.platformDefault

internal actual fun demoDeepLinkOpener(): DeepLinkOpener = DeepLinkOpener.platformDefault()
