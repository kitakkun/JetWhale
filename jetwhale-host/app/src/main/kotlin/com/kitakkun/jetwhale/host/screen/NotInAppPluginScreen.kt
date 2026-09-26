package com.kitakkun.jetwhale.host.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.copy
import com.kitakkun.jetwhale.host.model.HostVersionInfo
import com.kitakkun.jetwhale.host.model.OfficialPlugin
import com.kitakkun.jetwhale.host.model.OfficialPluginCatalog
import com.kitakkun.jetwhale.host.plugin_not_in_app_add_agent
import com.kitakkun.jetwhale.host.plugin_not_in_app_open_guide
import com.kitakkun.jetwhale.host.plugin_not_in_app_register
import com.kitakkun.jetwhale.host.plugin_not_in_app_title
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwCodeBlock
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import org.jetbrains.compose.resources.stringResource

/** Wide enough for a Gradle line to read in one piece, narrow enough to stay a column of prose. */
private val ContentMaxWidth = 560.dp

/**
 * What an app needs so that a plugin it doesn't include shows up: its agent library as a Gradle
 * dependency, its registration in `startJetWhale`, and where the plugin documents both.
 *
 * @property guideUrl null for a plugin the host knows nothing more about than its name.
 */
data class AgentSetup(
    val gradleDependencies: String,
    val registration: String,
    val guideUrl: String?,
) {
    companion object {
        /**
         * The setup of [pluginId]: exact for an official plugin, whose agent is released under the
         * host's own [hostVersion]; placeholders for any other.
         */
        fun forPlugin(pluginId: String, hostVersion: HostVersionInfo): AgentSetup {
            val official = OfficialPluginCatalog.plugins.find { it.pluginId == pluginId }
            val runtime = "${OfficialPlugin.OFFICIAL_PLUGIN_GROUP_ID}:jetwhale-agent-runtime:${hostVersion.version}"
            val agent = official?.agentCoordinates(hostVersion) ?: "<group>:<plugin agent artifact>:<version>"
            return AgentSetup(
                gradleDependencies = """
                    |dependencies {
                    |    implementation("$runtime")
                    |    implementation("$agent")
                    |}
                """.trimMargin(),
                registration = """
                    |startJetWhale {
                    |    plugins {
                    |        register(${official?.agentRegistration ?: "/* the plugin's agent */"})
                    |    }
                    |}
                """.trimMargin(),
                guideUrl = official?.guideUrl,
            )
        }
    }
}

/**
 * The content pane for a plugin the selected app doesn't include: nothing on the host can switch it
 * on, so it says how to add the plugin to the app instead.
 */
@Composable
fun NotInAppPluginScreen(
    pluginName: String,
    setup: AgentSetup,
) {
    val uriHandler = LocalUriHandler.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .verticalScroll(rememberScrollState())
                .padding(JwSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(JwSpacing.medium),
        ) {
            JwText(text = stringResource(Res.string.plugin_not_in_app_title, pluginName), style = JwTheme.textStyles.title)
            JwText(text = stringResource(Res.string.plugin_not_in_app_add_agent), color = JwTheme.colors.textSecondary)
            JwCodeBlock(text = setup.gradleDependencies, copyLabel = stringResource(Res.string.copy))
            JwText(text = stringResource(Res.string.plugin_not_in_app_register), color = JwTheme.colors.textSecondary)
            JwCodeBlock(text = setup.registration, copyLabel = stringResource(Res.string.copy))
            setup.guideUrl?.let { url ->
                JwButton(
                    text = stringResource(Res.string.plugin_not_in_app_open_guide),
                    onClick = { uriHandler.openUri(url) },
                    style = JwButtonStyle.Text,
                )
            }
        }
    }
}

@Preview
@Composable
private fun NotInAppPluginScreenPreview() {
    NotInAppPluginScreen(
        pluginName = "Storage Inspector",
        setup = AgentSetup.forPlugin("com.kitakkun.jetwhale.storage", HostVersionInfo("1.0.0")),
    )
}
