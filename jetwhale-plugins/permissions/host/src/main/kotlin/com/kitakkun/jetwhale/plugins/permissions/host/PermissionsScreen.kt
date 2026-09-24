package com.kitakkun.jetwhale.plugins.permissions.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwToolbar
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionCategory
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionChange
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionReport
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionState
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The permission list takes most of the width; the timeline beside it is a narrow log. */
private const val LIST_FRACTION = 0.68f

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

/** Binds the host-owned board to [PermissionsScreen]. */
@Composable
internal fun PermissionsScreenRoot(board: PermissionsBoard, modifier: Modifier = Modifier) {
    PermissionsScreen(
        report = board.report,
        timeline = board.timeline,
        status = board.status,
        actions = board,
        modifier = modifier,
    )
}

@Composable
internal fun PermissionsScreen(
    report: PermissionReport?,
    timeline: List<PermissionChange>,
    status: PermissionsStatus?,
    actions: PermissionsActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        JwToolbar(
            title = listOfNotNull("Permissions", report?.platform).joinToString(" · "),
            actions = {
                JwButton(text = "Open app settings", onClick = actions::openAppSettings, style = JwButtonStyle.Text)
                JwButton(text = "Reload from app", onClick = actions::refresh, style = JwButtonStyle.Text)
            },
        )
        status?.let { JwBanner(text = it.message, tone = if (it.isError) JwTone.Error else JwTone.Neutral) }
        when {
            report == null -> JwEmptyState(title = "Waiting for the app", description = "The permissions appear once the app connects.")

            report.unsupportedReason != null -> JwEmptyState(title = "Not supported on ${report.platform}", description = report.unsupportedReason)

            else -> JwSplitPane(
                state = rememberJwSplitPaneState(LIST_FRACTION),
                first = { PermissionList(report.permissions, actions) },
                second = { ChangeTimeline(timeline) },
            )
        }
    }
}

@Composable
private fun PermissionList(permissions: List<PermissionState>, actions: PermissionsActions) {
    LazyColumn(Modifier.fillMaxSize()) {
        PermissionCategory.entries.forEach { category ->
            val inCategory = permissions.filter { it.category == category }
            if (inCategory.isEmpty()) return@forEach
            item(key = category) { JwSectionHeader(title = category.title, count = inCategory.size) }
            items(inCategory, key = PermissionState::id) { permission -> PermissionRow(permission, actions) }
        }
        item(key = "revoke-note") {
            JwText(
                text = "Revoking a permission is not possible from inside the app; use the app's settings page, or adb shell pm revoke on Android.",
                style = JwTheme.textStyles.labelSmall,
                color = JwTheme.colors.textSecondary,
                modifier = Modifier.padding(JwSpacing.large),
            )
        }
    }
}

@Composable
private fun PermissionRow(permission: PermissionState, actions: PermissionsActions) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = JwSpacing.large, vertical = JwSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        Column(Modifier.weight(1f)) {
            JwText(text = permission.label)
            JwText(
                text = listOfNotNull(permission.id.takeIf { it != permission.label }, permission.protection).joinToString(" · "),
                style = JwTheme.textStyles.labelSmall,
                color = JwTheme.colors.textSecondary,
            )
            permission.note?.let { JwText(text = it, style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary) }
        }
        StatusTag(permission.status)
        Box(contentAlignment = Alignment.CenterEnd) {
            if (permission.requestable) {
                JwButton(
                    text = if (permission.category == PermissionCategory.SpecialAccess) "Open settings" else "Request",
                    onClick = { actions.request(permission.id) },
                )
            }
        }
    }
}

@Composable
private fun StatusTag(status: PermissionStatus?) {
    JwTag(
        text = status?.label ?: "Gone",
        tone = when (status) {
            PermissionStatus.Granted -> JwTone.Success
            PermissionStatus.Limited -> JwTone.Warning
            PermissionStatus.Denied, PermissionStatus.Restricted -> JwTone.Error
            PermissionStatus.NotDetermined, null -> JwTone.Neutral
        },
    )
}

@Composable
private fun ChangeTimeline(timeline: List<PermissionChange>) {
    Column(Modifier.fillMaxSize()) {
        JwSectionHeader(title = "Changes", count = timeline.size)
        if (timeline.isEmpty()) {
            JwEmptyState(title = "No changes yet", description = "A permission granted, denied or changed in the settings shows up here.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(timeline) { change ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = JwSpacing.large, vertical = JwSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
                ) {
                    JwText(
                        text = TimeFormatter.format(Instant.ofEpochMilli(change.observedAtEpochMillis)),
                        style = JwTheme.textStyles.labelSmall,
                        color = JwTheme.colors.textSecondary,
                    )
                    JwText(text = change.label, modifier = Modifier.weight(1f))
                    StatusTag(change.from)
                    JwText(text = "→", color = JwTheme.colors.textSecondary)
                    StatusTag(change.to)
                }
            }
        }
    }
}

private val PermissionCategory.title: String
    get() = when (this) {
        PermissionCategory.Runtime -> "Runtime"
        PermissionCategory.SpecialAccess -> "Special access"
        PermissionCategory.InstallTime -> "Install time"
    }

private val PermissionStatus.label: String
    get() = when (this) {
        PermissionStatus.Granted -> "Granted"
        PermissionStatus.Denied -> "Denied"
        PermissionStatus.NotDetermined -> "Not asked"
        PermissionStatus.Limited -> "Limited"
        PermissionStatus.Restricted -> "Restricted"
    }
