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
import java.util.concurrent.atomic.AtomicBoolean

private const val DEVELOPMENT_TEAM_KEY = "iosDevelopmentTeam"

/**
 * The Apple development team the XCTest runners sign with for physical iPhones. There is one per
 * host process, like the runners, and every Mirror instance shows and changes the same one. The
 * stored team is taken once, by whichever instance reads it first, and never after the user has
 * chosen one: a read that finishes late would otherwise bring back the team the user replaced.
 */
@Stable
internal class RunnerSigningTeam : XcTestRunnerSettings {
    /** The team's ID, such as `ABCDE12345`, or null until one is set. */
    override var developmentTeam: String? by mutableStateOf(null)
        private set

    private val settled = AtomicBoolean(false)

    /** Takes [team], read from storage, unless a team was already read or chosen in this process. */
    fun adoptStoredTeam(team: String?) {
        if (settled.compareAndSet(false, true)) developmentTeam = team
    }

    fun chooseTeam(team: String?) {
        settled.set(true)
        developmentTeam = team
    }
}

/** Keeps [signingTeam] in the plugin's [storage]: reads it once, and stores each change. */
internal class DevelopmentTeamSetting(
    private val storage: JetWhalePluginStorage?,
    private val scope: CoroutineScope,
    private val signingTeam: RunnerSigningTeam,
) {
    val developmentTeam: String? get() = signingTeam.developmentTeam

    init {
        scope.launch { signingTeam.adoptStoredTeam(storage?.get<String>(DEVELOPMENT_TEAM_KEY)) }
    }

    /** Sets the team, or forgets it when [team] is null. */
    fun updateDevelopmentTeam(team: String?) {
        signingTeam.chooseTeam(team)
        scope.launch { if (team == null) storage?.remove(DEVELOPMENT_TEAM_KEY) else storage?.put(DEVELOPMENT_TEAM_KEY, team) }
    }
}
