package com.kitakkun.jetwhale.demo.shared

import com.kitakkun.jetwhale.plugins.deeplinks.agent.DeepLinkOpener

internal actual fun demoDeepLinkOpener(): DeepLinkOpener = DemoRouterDeepLinkOpener
