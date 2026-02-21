package com.kitakkun.jetwhale.annotations

/**
 * Marks an API as internal to JetWhale and not intended for public use.
 */
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
public annotation class InternalJetWhaleApi
