package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/**
 * Checks the Conveyor update site for a newer host release. Triggered manually from the
 * settings screen; the OS-level update mechanism (Sparkle/MSIX/apt) applies the update itself.
 */
typealias UpdateCheckMutationKey = MutationKey<UpdateCheckResult, Unit>
