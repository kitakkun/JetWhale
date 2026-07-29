package com.kitakkun.jetwhale.host.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

val SettingsScreenScaffoldPageContentPadding = PaddingValues(16.dp)

/** Wide enough for the longest label without wrapping, narrow enough to leave the detail room. */
private val MenuPaneWidth = 220.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenScaffold(
    uiState: SettingsScreenScaffoldUiState,
    onClickClose: () -> Unit,
    onSelectPage: (SettingsScreenPage) -> Unit,
    onToggleSection: (SettingsScreenSection) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (SettingsScreenPage) -> Unit,
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
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .width(MenuPaneWidth)
                    .fillMaxHeight()
                    // The list grows downward as sections are added, which is the point of it — so it
                    // has to scroll, or the newest entries are the ones a short window cuts off.
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
            ) {
                SettingsScreenSection.entries.forEach { section ->
                    SectionHeader(
                        section = section,
                        expanded = section in uiState.expandedSections,
                        onClick = { onToggleSection(section) },
                    )
                    if (section in uiState.expandedSections) {
                        SettingsScreenPage.entries.filter { it.section == section }.forEach { page ->
                            NavigationDrawerItem(
                                selected = page == uiState.selectedPage,
                                // One line keeps every row the same height, so the list does not
                                // reflow when a translation is longer than the pane.
                                label = {
                                    Text(
                                        text = stringResource(page.labelTextRes),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                onClick = { onSelectPage(page) },
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            }
            VerticalDivider()
            // Only the selected page is composed. The pager this replaced kept every section alive,
            // so each one's subscriptions ran whether or not it was on screen.
            Column(modifier = Modifier.fillMaxSize()) {
                content(uiState.selectedPage)
            }
        }
    }
}

/**
 * A section row. It collapses rather than selecting anything: the section is a container, and every
 * setting lives on one of its pages.
 */
@Composable
private fun SectionHeader(
    section: SettingsScreenSection,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(section.icon, contentDescription = null)
        Text(
            text = stringResource(section.labelTextRes),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
        )
    }
}
