package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/**
 * Hands control to the OS update mechanism (Sparkle dialog on macOS, updater on Windows).
 * The app may quit as part of the flow, so callers must not rely on completion.
 */
typealias UpdateInstallMutationKey = MutationKey<Unit, Unit>
