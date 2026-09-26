package com.kitakkun.jetwhale.host.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.close
import com.kitakkun.jetwhale.host.model.ArrivedPluginJar
import com.kitakkun.jetwhale.host.model.DeclaredPlugin
import com.kitakkun.jetwhale.host.plugin_arrived_failed
import com.kitakkun.jetwhale.host.plugin_arrived_later
import com.kitakkun.jetwhale.host.plugin_arrived_load
import com.kitakkun.jetwhale.host.plugin_arrived_more
import com.kitakkun.jetwhale.host.plugin_arrived_new
import com.kitakkun.jetwhale.host.plugin_arrived_review
import com.kitakkun.jetwhale.host.plugin_arrived_unreadable
import com.kitakkun.jetwhale.host.plugin_arrived_unreadable_because
import com.kitakkun.jetwhale.host.plugin_arrived_update
import com.kitakkun.jetwhale.host.plugin_arrived_update_action
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwTone
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

/**
 * Asks about the jars that appeared in the plugins directory while the host was running, one strip
 * each: what the jar declares, then its file name, size and the start of its SHA-256, with Load (or
 * Update, for a jar that overwrote a running one) and Later. Nothing in a jar runs before Load.
 */
@Composable
fun PluginJarArrivalBanner(
    arrivedJars: ImmutableList<ArrivedPluginJar>,
    onLoad: (jar: ArrivedPluginJar) -> Unit,
    onPostpone: (jarPath: String) -> Unit,
    onReviewInSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Acting on a strip removes it and moves the next one up under the pointer; a double click must
    // not approve a jar the user never read.
    var listChangedAtMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(arrivedJars.map(ArrivedPluginJar::jarPath)) {
        listChangedAtMillis = System.currentTimeMillis()
    }
    val guarded = { action: () -> Unit ->
        if (System.currentTimeMillis() - listChangedAtMillis >= REFLOW_GUARD_MILLIS) action()
    }
    Column(modifier = modifier) {
        arrivedJars.take(MAX_LISTED_JARS).forEach { jar ->
            val loadFailure = jar.loadFailure
            if (loadFailure != null) {
                JwBanner(
                    text = stringResource(Res.string.plugin_arrived_failed, jar.declaredPlugins.joinToString(transform = DeclaredPlugin::pluginName).ifEmpty { jar.fileName }, loadFailure),
                    tone = JwTone.Error,
                    actions = {
                        JwButton(
                            text = stringResource(Res.string.close),
                            onClick = { guarded { onPostpone(jar.jarPath) } },
                            style = JwButtonStyle.Text,
                        )
                    },
                )
                return@forEach
            }
            JwBanner(
                text = jar.headline(),
                tone = JwTone.Warning,
                actions = {
                    JwButton(
                        text = stringResource(if (jar.replacedPlugins.isEmpty()) Res.string.plugin_arrived_load else Res.string.plugin_arrived_update_action),
                        onClick = { guarded { onLoad(jar) } },
                        style = JwButtonStyle.Text,
                    )
                    JwButton(
                        text = stringResource(Res.string.plugin_arrived_later),
                        onClick = { guarded { onPostpone(jar.jarPath) } },
                        style = JwButtonStyle.Text,
                    )
                },
            )
        }
        val unlisted = arrivedJars.size - MAX_LISTED_JARS
        if (unlisted > 0) {
            JwBanner(
                text = pluralStringResource(Res.plurals.plugin_arrived_more, unlisted, unlisted),
                tone = JwTone.Warning,
                actions = {
                    JwButton(
                        text = stringResource(Res.string.plugin_arrived_review),
                        onClick = onReviewInSettings,
                        style = JwButtonStyle.Text,
                    )
                },
            )
        }
    }
}

/**
 * Size and hash first: the file name can be long enough to push them out of the one line. Why a
 * manifest could not be read comes last, for the same reason.
 */
@Composable
private fun ArrivedPluginJar.headline(): String {
    val details = "${formatSize(sizeBytes)} · SHA-256 ${sha256.take(SHORT_HASH_LENGTH)} · $fileName"
    return when {
        declaredPlugins.isEmpty() -> when (val reason = unreadableReason) {
            null -> stringResource(Res.string.plugin_arrived_unreadable, details)
            else -> stringResource(Res.string.plugin_arrived_unreadable_because, details, reason)
        }

        replacedPlugins.isEmpty() -> stringResource(Res.string.plugin_arrived_new, declaredPlugins.joinToString { "${it.pluginName} ${it.version}" }, details)

        else -> stringResource(Res.string.plugin_arrived_update, describeUpdate(replaced = replacedPlugins, declared = declaredPlugins), details)
    }
}

/** "Network Inspector 1.2.0 → 1.3.0" for the usual one-plugin jar, each side in full otherwise. */
private fun describeUpdate(replaced: List<DeclaredPlugin>, declared: List<DeclaredPlugin>): String {
    val old = replaced.singleOrNull()
    val new = declared.singleOrNull()
    if (old != null && new != null && old.pluginId == new.pluginId) return "${new.pluginName} ${old.version} → ${new.version}"
    return "${replaced.joinToString { "${it.pluginName} ${it.version}" }} → ${declared.joinToString { "${it.pluginName} ${it.version}" }}"
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
}

private const val REFLOW_GUARD_MILLIS = 600L

/** More strips than this would push the window's content out of view; the rest are in the settings. */
private const val MAX_LISTED_JARS = 3

private const val SHORT_HASH_LENGTH = 12

@Preview
@Composable
private fun PluginJarArrivalBannerPreview() {
    val network = DeclaredPlugin(pluginId = "com.example.network", pluginName = "Network Inspector", version = "1.3.0")
    PluginJarArrivalBanner(
        arrivedJars = persistentListOf(
            ArrivedPluginJar(
                jarPath = "/plugins/network.jar",
                sizeBytes = 2_300_000,
                sha256 = "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b",
                declaredPlugins = listOf(network),
                unreadableReason = null,
                replacedPlugins = listOf(DeclaredPlugin(pluginId = "com.example.network", pluginName = "Network Inspector", version = "1.2.0")),
                loadFailure = null,
            ),
            ArrivedPluginJar(
                jarPath = "/plugins/storage.jar",
                sizeBytes = 840_000,
                sha256 = "9f8e7d6c5b4a39281706f5e4d3c2b1a09f8e7d6c5b4a39281706f5e4d3c2b1a0",
                declaredPlugins = listOf(DeclaredPlugin(pluginId = "com.example.storage", pluginName = "Storage", version = "0.4.0")),
                unreadableReason = null,
                replacedPlugins = emptyList(),
                loadFailure = "Declared dependency io.ktor:ktor-client-core:3.2.0 is missing",
            ),
        ),
        onLoad = {},
        onPostpone = {},
        onReviewInSettings = {},
    )
}
