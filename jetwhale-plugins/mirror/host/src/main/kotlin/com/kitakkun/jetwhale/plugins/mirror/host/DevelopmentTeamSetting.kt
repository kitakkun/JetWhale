package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import com.kitakkun.jetwhale.host.sdk.get
import com.kitakkun.jetwhale.host.sdk.put
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunnerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val DEVELOPMENT_TEAM_KEY = "iosDevelopmentTeam"

/**
 * The Apple development team the XCTest runners sign with for physical iPhones. There is one per
 * host process, like the runners, and every Mirror instance shows and changes the same one.
 */
@Stable
internal class RunnerSigningTeam : XcTestRunnerSettings {
    /** The team's ID, such as `ABCDE12345`, or null until one is set. */
    override var developmentTeam: String? by mutableStateOf(null)
}

/** Keeps [signingTeam] in the plugin's [storage]: reads it once, and stores each change. */
internal class DevelopmentTeamSetting(
    private val storage: JetWhalePluginStorage?,
    private val scope: CoroutineScope,
    private val signingTeam: RunnerSigningTeam,
) {
    val developmentTeam: String? get() = signingTeam.developmentTeam

    init {
        scope.launch { storage?.get<String>(DEVELOPMENT_TEAM_KEY)?.let { signingTeam.developmentTeam = it } }
    }

    /** Sets the team, or forgets it when [team] is null. */
    fun updateDevelopmentTeam(team: String?) {
        signingTeam.developmentTeam = team
        scope.launch { if (team == null) storage?.remove(DEVELOPMENT_TEAM_KEY) else storage?.put(DEVELOPMENT_TEAM_KEY, team) }
    }
}
