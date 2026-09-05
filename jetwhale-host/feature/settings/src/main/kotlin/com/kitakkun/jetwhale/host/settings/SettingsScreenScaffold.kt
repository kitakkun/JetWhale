package com.kitakkun.jetwhale.host.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIconButton
import com.kitakkun.jetwhale.host.ui.JwIcons
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwMetrics
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwToolbar
import com.kitakkun.jetwhale.host.ui.JwVerticalDivider
import org.jetbrains.compose.resources.stringResource

val SettingsScreenScaffoldPageContentPadding = PaddingValues(JwSpacing.xl)

/** Wide enough for the longest label without wrapping, narrow enough to leave the detail room. */
private val MenuPaneWidth = 200.dp

@Composable
fun SettingsScreenScaffold(
    uiState: SettingsScreenScaffoldUiState,
    onClickClose: () -> Unit,
    onSelectPage: (SettingsScreenPage) -> Unit,
    onToggleSection: (SettingsScreenSection) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (SettingsScreenPage) -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .fillMaxSize(0.8f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(JwMetrics.borderWidth, JwTheme.colors.border, shape),
    ) {
        JwToolbar(
            title = stringResource(Res.string.settings_title),
            actions = {
                JwIconButton(onClick = onClickClose, tooltip = stringResource(Res.string.close)) {
                    JwIcon(imageVector = JwIcons.Close, contentDescription = null)
                }
            },
        )

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(MenuPaneWidth)
                    .fillMaxHeight()
                    .background(JwTheme.colors.sidebarBackground)
                    // The list grows downward as sections are added, which is the point of it — so it
                    // has to scroll, or the newest entries are the ones a short window cuts off.
                    .verticalScroll(rememberScrollState())
                    .padding(JwSpacing.xs),
            ) {
                SettingsScreenSection.entries.forEach { section ->
                    val expanded = section in uiState.expandedSections
                    JwSectionHeader(
                        title = stringResource(section.labelTextRes),
                        expanded = expanded,
                        onToggleExpanded = { onToggleSection(section) },
                        modifier = Modifier.padding(top = JwSpacing.xs),
                    )
                    if (expanded) {
                        SettingsScreenPage.entries.filter { it.section == section }.forEach { page ->
                            JwListItem(
                                text = stringResource(page.labelTextRes),
                                selected = page == uiState.selectedPage,
                                onClick = { onSelectPage(page) },
                                modifier = Modifier.padding(start = JwSpacing.lg),
                            )
                        }
                    }
                }
            }
            JwVerticalDivider()
            // Only the selected page is composed. The pager this replaced kept every section alive,
            // so each one's subscriptions ran whether or not it was on screen.
            Column(modifier = Modifier.fillMaxSize()) {
                content(uiState.selectedPage)
            }
        }
    }
}
