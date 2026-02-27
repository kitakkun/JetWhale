package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.no_session_available
import com.kitakkun.jetwhale.host.select_session
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSelectorView(
    selectedSession: DebugSession?,
    sessions: ImmutableList<DebugSession>,
    onSelectSession: (DebugSession) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it && sessions.isNotEmpty() },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 56.dp)
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                )
                Text(
                    text = when {
                        selectedSession != null -> selectedSession.displayName
                        sessions.isNotEmpty() -> stringResource(Res.string.select_session)
                        else -> stringResource(Res.string.no_session_available)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            sessions.forEach {
                SessionDropdownMenuItem(
                    selected = it.id == selectedSession?.id,
                    isActive = it.isActive,
                    displayName = it.displayName,
                    onClick = {
                        onSelectSession(it)
                        expanded = false
                    },
                )
            }
        }
    }
}
