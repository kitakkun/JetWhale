package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HostVersion

sealed interface LaunchOutcome {
    /**
     * This process runs [chosenHostJar], and [startOutcomeRecorder] records how that start goes.
     *
     * @property setAsideVersion A version this launch set aside, which the host tells the user about.
     */
    class Starting(
        val chosenHostJar: ChosenHostJar,
        val setAsideVersion: HostVersion?,
        val startOutcomeRecorder: HostStartOutcomeRecorder,
    ) : LaunchOutcome

    data object BroughtRunningHostToFront : LaunchOutcome

    data object RunningHostUnreachable : LaunchOutcome

    data object NothingLeft : LaunchOutcome
}
