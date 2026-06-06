package com.kitakkun.jetwhale.demo.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var selectedTab by remember { mutableStateOf(0) }
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
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Example plugin") },
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Network plugin") },
                        )
                    }
                    when (selectedTab) {
                        0 -> ExampleTestScreen()
                        else -> NetworkTestScreen()
                    }
                }
            }
        }
    }
}
