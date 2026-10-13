package com.kitakkun.jetwhale.host.settings.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.HostOs
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.approve_untrusted_plugin
import com.kitakkun.jetwhale.host.settings.component.SettingOptionView
import com.kitakkun.jetwhale.host.settings.component.SwitchSettingsItemView
import com.kitakkun.jetwhale.host.settings.plugin_security
import com.kitakkun.jetwhale.host.settings.sign_plugin_trust_registry
import com.kitakkun.jetwhale.host.settings.sign_plugin_trust_registry_hint
import com.kitakkun.jetwhale.host.settings.sign_plugin_trust_registry_hint_linux
import com.kitakkun.jetwhale.host.settings.sign_plugin_trust_registry_hint_macos
import com.kitakkun.jetwhale.host.settings.sign_plugin_trust_registry_hint_windows
import com.kitakkun.jetwhale.host.settings.untrusted_jar_hint
import com.kitakkun.jetwhale.host.settings.untrusted_plugins
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwShapes
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.pluginSecurityPageItems(
    untrustedJarPaths: ImmutableList<String>,
    signPluginTrustRegistry: Boolean,
    onApproveUntrustedJar: (String) -> Unit,
    onChangeSignPluginTrustRegistry: (Boolean) -> Unit,
) {
    if (untrustedJarPaths.isNotEmpty()) {
        item(key = "untrusted_jars") {
            UntrustedPluginsSection(
                untrustedJarPaths = untrustedJarPaths,
                onApprove = onApproveUntrustedJar,
            )
        }
    }
    item(key = "trust_registry_signing") {
        TrustRegistrySigningSection(
            signPluginTrustRegistry = signPluginTrustRegistry,
            onChangeSignPluginTrustRegistry = onChangeSignPluginTrustRegistry,
        )
    }
}

@Composable
private fun UntrustedPluginsSection(
    untrustedJarPaths: List<String>,
    onApprove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = JwTheme.colors.errorContainer,
                shape = JwShapes.small,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JwIcon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = JwTheme.colors.error,
            )
            JwText(
                text = stringResource(Res.string.untrusted_plugins) + " (${untrustedJarPaths.size})",
                style = JwTheme.textStyles.subtitle,
                color = JwTheme.colors.onErrorContainer,
            )
        }
        JwText(
            text = stringResource(Res.string.untrusted_jar_hint),
            style = JwTheme.textStyles.bodySmall,
            color = JwTheme.colors.onErrorContainer,
        )
        untrustedJarPaths.forEach { path ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JwText(
                    text = path,
                    style = JwTheme.textStyles.bodySmall,
                    color = JwTheme.colors.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                JwButton(
                    text = stringResource(Res.string.approve_untrusted_plugin),
                    onClick = { onApprove(path) },
                    style = JwButtonStyle.Primary,
                )
            }
        }
    }
}

@Composable
private fun TrustRegistrySigningSection(
    signPluginTrustRegistry: Boolean,
    onChangeSignPluginTrustRegistry: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingOptionView(
        label = stringResource(Res.string.plugin_security),
        modifier = modifier,
    ) {
        SwitchSettingsItemView(
            label = stringResource(Res.string.sign_plugin_trust_registry),
            isChecked = signPluginTrustRegistry,
            onCheckedChange = onChangeSignPluginTrustRegistry,
        )
        val osHint = when (HostOs.current) {
            HostOs.MAC -> Res.string.sign_plugin_trust_registry_hint_macos
            HostOs.WINDOWS -> Res.string.sign_plugin_trust_registry_hint_windows
            else -> Res.string.sign_plugin_trust_registry_hint_linux
        }
        JwText(
            text = "${stringResource(Res.string.sign_plugin_trust_registry_hint)} ${stringResource(osHint)}",
            style = JwTheme.textStyles.bodySmall,
            color = JwTheme.colors.textSecondary,
        )
    }
}
