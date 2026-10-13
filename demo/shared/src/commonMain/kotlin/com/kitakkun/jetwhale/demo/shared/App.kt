package com.kitakkun.jetwhale.demo.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var selectedTab by remember { mutableStateOf(0) }
    val nav3BackStack = rememberTrackedDemoNavBackStack()
    MaterialTheme {
        Surface {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("JetWhale Demo App (Agent)") },
                    )
                },
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    DemoTabRow(selectedTab = selectedTab, onSelectTab = { selectedTab = it })
                    when (selectedTab) {
                        0 -> ExampleTestScreen()
                        1 -> NetworkTestScreen()
                        2 -> Nav3TestScreen(nav3BackStack)
                        3 -> ComposeNodeTestScreen()
                        4 -> MainThreadTestScreen()
                        else -> PlatformExtraTabScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoTabRow(selectedTab: Int, onSelectTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    SecondaryTabRow(selectedTabIndex = selectedTab, modifier = modifier) {
        Tab(
            selected = selectedTab == 0,
            onClick = { onSelectTab(0) },
            text = { Text("Example plugin") },
        )
        Tab(
            selected = selectedTab == 1,
            onClick = { onSelectTab(1) },
            text = { Text("Network plugin") },
        )
        Tab(
            selected = selectedTab == 2,
            onClick = { onSelectTab(2) },
            text = { Text("Nav3 plugin") },
        )
        Tab(
            selected = selectedTab == 3,
            onClick = { onSelectTab(3) },
            text = { Text("Compose nodes") },
        )
        Tab(
            selected = selectedTab == 4,
            onClick = { onSelectTab(4) },
            text = { Text("Main thread") },
        )
        platformExtraTabLabel?.let { label ->
            Tab(
                selected = selectedTab == 5,
                onClick = { onSelectTab(5) },
                text = { Text(label) },
            )
        }
    }
}

@Preview
@Composable
private fun AppPreview() {
    App()
}
