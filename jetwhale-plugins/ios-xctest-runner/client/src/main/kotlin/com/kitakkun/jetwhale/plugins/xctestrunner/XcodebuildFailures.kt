package com.kitakkun.jetwhale.plugins.xctestrunner

/**
 * Turns what `xcodebuild` printed while building or starting the runner into what the user has to
 * do about it. The device cases follow Apple's and Appium's descriptions of what a device needs;
 * their wording has not been seen from a device here, so the matching is loose and every reason
 * quotes xcodebuild's own error.
 */
internal object XcodebuildFailures {
    fun reasonOf(output: String, destination: RunnerDestination): String {
        val quoted = errorLinesOf(output).ifEmpty { output.lines().map(String::trim).filter(String::isNotEmpty).takeLast(3) }.joinToString(" / ").take(400)
        val lowercaseOutput = output.lowercase()
        val deviceReason = when {
            destination !is RunnerDestination.Device -> null

            "developer mode" in lowercaseOutput -> "Developer Mode is off on the iPhone: turn it on in Settings → Privacy & Security → Developer Mode, then restart it"

            "passcode protected" in lowercaseOutput || "is locked" in lowercaseOutput || "unlock" in lowercaseOutput -> "the iPhone is locked: unlock it and keep it unlocked while it is driven"

            "automation mode" in lowercaseOutput || "ui automation" in lowercaseOutput -> "UI automation is off on the iPhone: turn on Settings → Developer → Enable UI Automation"

            "unable to find a destination" in lowercaseOutput || "not paired" in lowercaseOutput || "pairing" in lowercaseOutput -> "xcodebuild cannot reach the iPhone: connect it by USB, unlock it and trust this Mac"

            "signing" in lowercaseOutput || "code sign" in lowercaseOutput || "provisioning" in lowercaseOutput || "no profiles for" in lowercaseOutput || "no account for team" in lowercaseOutput || "development team" in lowercaseOutput ->
                "the XCTest runner could not be signed for team ${destination.developmentTeam}: check the team ID, and that Xcode is signed in to that team in Xcode → Settings → Accounts"

            else -> null
        }
        return if (deviceReason != null) "$deviceReason ($quoted)" else "the XCTest runner did not start: $quoted"
    }

    private fun errorLinesOf(output: String): List<String> = output.lines().map(String::trim).filter { it.contains("error:", ignoreCase = true) }.distinct().takeLast(3)
}
