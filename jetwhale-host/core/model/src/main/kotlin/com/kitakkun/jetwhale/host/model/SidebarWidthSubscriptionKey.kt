package com.kitakkun.jetwhale.host.model

import soil.query.SubscriptionKey

/** The sidebar width the user last dragged to, in dp; [widthDp] is null until they resize it. */
@JvmInline
value class SidebarWidth(val widthDp: Float?)

typealias SidebarWidthSubscriptionKey = SubscriptionKey<SidebarWidth>
