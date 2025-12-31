package com.kitakkun.jetwhale.host.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class SettingsScreenSegmentedMenu(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenScaffold(
    uiState: SettingsScreenScaffoldUiState,
    onClickClose: () -> Unit,
    onSelectMenu: (SettingsScreenSegmentedMenu) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (SettingsScreenSegmentedMenu) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = uiState.selectedMenu.ordinal) { SettingsScreenSegmentedMenu.entries.size }

    LaunchedEffect(uiState.selectedMenu) {
        pagerState.animateScrollToPage(uiState.selectedMenu.ordinal)
    }

    Column(
        modifier = modifier
            .fillMaxSize(0.8f)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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
            modifier = Modifier.padding(16.dp),
        )
        SingleChoiceSegmentedButtonRow {
            SettingsScreenSegmentedMenu.entries.forEachIndexed { index, menu ->
                SegmentedButton(
                    selected = menu == uiState.selectedMenu,
                    label = { Text(stringResource(menu.labelTextRes)) },
                    icon = { Icon(menu.icon, null) },
                    onClick = { onSelectMenu(menu) },
                    shape = SegmentedButtonDefaults.itemShape(index, SettingsScreenSegmentedMenu.entries.size),
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
        ) {
            content(SettingsScreenSegmentedMenu.entries[it])
        }
    }
}
