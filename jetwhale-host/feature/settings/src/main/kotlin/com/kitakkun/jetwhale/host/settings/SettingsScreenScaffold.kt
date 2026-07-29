package com.kitakkun.jetwhale.host.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Serializable
enum class SettingsScreenMenu(
    val labelTextRes: StringResource,
    val icon: ImageVector,
) {
    General(
        labelTextRes = Res.string.general,
        icon = Icons.Default.Info,
    ),
    Server(
        labelTextRes = Res.string.server,
        icon = Icons.Default.Computer,
    ),
    Plugins(
        labelTextRes = Res.string.plugins,
        icon = Icons.Default.Work,
    ),
}

val SettingsScreenScaffoldPageContentPadding = PaddingValues(16.dp)

/** Wide enough for the longest section label without wrapping, narrow enough to leave the detail room. */
private val MenuPaneWidth = 200.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenScaffold(
    uiState: SettingsScreenScaffoldUiState,
    onClickClose: () -> Unit,
    onSelectMenu: (SettingsScreenMenu) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (SettingsScreenMenu) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize(0.8f)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
            ),
    ) {
        TopAppBar(
            title = { Text(stringResource(Res.string.settings_title)) },
            expandedHeight = 40.dp,
            actions = {
                IconButton(onClick = onClickClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                    )
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        HorizontalDivider()

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .width(MenuPaneWidth)
                    .fillMaxHeight()
                    .padding(8.dp),
            ) {
                SettingsScreenMenu.entries.forEach { menu ->
                    NavigationDrawerItem(
                        selected = menu == uiState.selectedMenu,
                        label = { Text(stringResource(menu.labelTextRes)) },
                        icon = { Icon(menu.icon, contentDescription = null) },
                        onClick = { onSelectMenu(menu) },
                    )
                }
            }
            VerticalDivider()
            // Only the selected section is composed. The pager this replaced kept every section
            // alive, so each one's subscriptions ran whether or not it was on screen.
            Column(modifier = Modifier.fillMaxSize()) {
                content(uiState.selectedMenu)
            }
        }
    }
}
