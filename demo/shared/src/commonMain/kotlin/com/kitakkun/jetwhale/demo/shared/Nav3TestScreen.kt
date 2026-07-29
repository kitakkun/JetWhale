package com.kitakkun.jetwhale.demo.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.kitakkun.jetwhale.plugins.nav3.agent.TrackNavBackStack
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@SerialName("Home")
@Serializable
data object DemoHomeKey : NavKey

@SerialName("Detail")
@Serializable
data class DemoDetailKey(val itemId: String, val highlighted: Boolean = false) : NavKey

@SerialName("Settings")
@Serializable
data class DemoSettingsKey(val section: DemoSettingsSection) : NavKey

@Serializable
enum class DemoSettingsSection { General, Privacy, About }

/**
 * The one thing a Navigation 3 app has to hand the debugger: the serializers it already needs for
 * `rememberNavBackStack`. The Nav3 agent plugin derives the whole "what can be pushed" catalog from
 * exactly this module.
 */
val demoNavKeySerializersModule: SerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(DemoHomeKey::class, DemoHomeKey.serializer())
        subclass(DemoDetailKey::class, DemoDetailKey.serializer())
        subclass(DemoSettingsKey::class, DemoSettingsKey.serializer())
    }
}

/**
 * Creates the demo's back stack and hands it to the Nav3 agent plugin.
 *
 * It lives at the app root rather than inside the tab so the host can drive navigation whichever tab
 * is open — the same reason a real app registers its root back stack once, where it is created.
 */
@Composable
fun rememberTrackedDemoNavBackStack(): NavBackStack<NavKey> {
    val backStack = rememberNavBackStack(
        configuration = SavedStateConfiguration { serializersModule = demoNavKeySerializersModule },
        DemoHomeKey,
    )
    DIModule.nav3AgentPlugin.TrackNavBackStack(backStack)
    return backStack
}

@Composable
fun Nav3TestScreen(backStack: NavBackStack<NavKey>) {
    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize(),
        entryProvider = entryProvider {
            entry<DemoHomeKey> {
                DemoScreen(title = "Home") {
                    Button(onClick = { backStack.add(DemoDetailKey(itemId = "42")) }) {
                        Text("Open item 42")
                    }
                    OutlinedButton(onClick = { backStack.add(DemoSettingsKey(section = DemoSettingsSection.General)) }) {
                        Text("Open settings")
                    }
                }
            }
            entry<DemoDetailKey> { key ->
                DemoScreen(title = "Detail ${key.itemId}${if (key.highlighted) " ★" else ""}") {
                    Button(onClick = { backStack.add(DemoDetailKey(itemId = key.itemId + "0")) }) {
                        Text("Open a deeper item")
                    }
                    OutlinedButton(onClick = { backStack.removeLastOrNull() }) {
                        Text("Back")
                    }
                }
            }
            entry<DemoSettingsKey> { key ->
                DemoScreen(title = "Settings · ${key.section}") {
                    DemoSettingsSection.entries.forEach { section ->
                        OutlinedButton(onClick = { backStack.add(DemoSettingsKey(section = section)) }) {
                            Text("Go to $section")
                        }
                    }
                    OutlinedButton(onClick = { backStack.removeLastOrNull() }) {
                        Text("Back")
                    }
                }
            }
        },
    )
}

@Composable
private fun DemoScreen(title: String, actions: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            "Drive this stack from the host's Nav3 Navigator plugin, or over MCP.",
            style = MaterialTheme.typography.bodySmall,
        )
        actions()
    }
}
