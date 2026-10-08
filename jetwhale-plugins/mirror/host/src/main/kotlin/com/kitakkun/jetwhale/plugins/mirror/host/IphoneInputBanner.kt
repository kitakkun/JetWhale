package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwDialog
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTone

/**
 * Under a physical iPhone's live view: whether it takes input, which is experimental, and the
 * development team that signs the XCTest runner sending it, with a way to set the team.
 *
 * @param inputRefusal why the iPhone takes no input, or null when it does.
 */
@Composable
internal fun IphoneInputBanner(inputRefusal: String?, developmentTeam: String?, onUpdateDevelopmentTeam: (String?) -> Unit) {
    var editingTeam by remember { mutableStateOf(false) }
    val takesInput = inputRefusal == null
    val text = when {
        takesInput -> "Input on iPhones is experimental · signed with team $developmentTeam"
        developmentTeam == null -> "Input on an iPhone needs your Apple development team · experimental"
        else -> "No input: $inputRefusal"
    }
    JwBanner(
        text = text,
        tone = if (takesInput) JwTone.Info else JwTone.Warning,
        actions = {
            JwButton(text = if (developmentTeam == null) "Set team…" else "Change team…", onClick = { editingTeam = true }, style = JwButtonStyle.Text)
        },
    )
    if (editingTeam) {
        DevelopmentTeamDialog(
            current = developmentTeam,
            onSave = { newTeam ->
                onUpdateDevelopmentTeam(newTeam)
                editingTeam = false
            },
            onDismiss = { editingTeam = false },
        )
    }
}

@Composable
private fun DevelopmentTeamDialog(current: String?, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(current.orEmpty()) }
    val team = draft.trim().uppercase()
    val acceptable = team.isEmpty() || isDevelopmentTeamId(team)
    JwDialog(
        title = "Development team",
        closeLabel = "Close",
        onDismissRequest = onDismiss,
        confirmButton = { JwButton(text = "Save", onClick = { onSave(team.ifEmpty { null }) }, enabled = acceptable, style = JwButtonStyle.Primary) },
        dismissButton = { JwButton(text = "Cancel", onClick = onDismiss, style = JwButtonStyle.Text) },
    ) {
        JwText(
            "Input reaches an iPhone through an XCTest runner that Xcode builds, signs with this team and installs on the device. " +
                "The team ID is ten letters and digits, listed under Membership in your Apple Developer account; Xcode must be signed in to the team. " +
                "The iPhone needs Developer Mode and Enable UI Automation on, and must stay unlocked. Leave the field empty to stop driving iPhones.",
        )
        JwTextField(value = draft, onValueChange = { draft = it }, placeholder = "ABCDE12345", isError = !acceptable)
    }
}

/** Whether [text] has the shape of a development team ID: ten uppercase letters and digits. */
private fun isDevelopmentTeamId(text: String): Boolean = text.length == 10 && text.all { it in 'A'..'Z' || it in '0'..'9' }
