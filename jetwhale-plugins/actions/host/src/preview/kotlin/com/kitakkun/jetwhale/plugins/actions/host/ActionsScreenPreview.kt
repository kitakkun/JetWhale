package com.kitakkun.jetwhale.plugins.actions.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionCatalog
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionDescriptor
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionOutcome
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionParameter
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionResult
import com.kitakkun.jetwhale.plugins.actions.protocol.ParameterType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val loginAs = ActionDescriptor(
    id = "Account / Log in as",
    title = "Log in as",
    group = "Account",
    description = "Signs in with a test account.",
    destructive = false,
    scoped = false,
    parameters = listOf(
        ActionParameter("email", ParameterType.STRING, optional = false, nullable = false, description = "The test account", enumValues = emptyList(), hasOptions = true),
        ActionParameter("remember", ParameterType.BOOLEAN, optional = true, nullable = false, description = null, enumValues = emptyList(), hasOptions = false),
        ActionParameter("tier", ParameterType.ENUM, optional = false, nullable = false, description = null, enumValues = listOf("FREE", "PRO"), hasOptions = false),
    ),
)

private val wipe = ActionDescriptor(
    id = "Wipe local data",
    title = "Wipe local data",
    group = null,
    description = null,
    destructive = true,
    scoped = false,
    parameters = emptyList(),
)

private val fillCard = ActionDescriptor(
    id = "Checkout / Fill test card",
    title = "Fill test card",
    group = "Checkout",
    description = null,
    destructive = false,
    scoped = true,
    parameters = emptyList(),
)

private val finishedRun = RunRecord(
    runId = "1",
    actionId = loginAs.id,
    title = loginAs.title,
    arguments = JsonObject(mapOf("email" to JsonPrimitive("qa@example.com"))),
    origin = RunOrigin.AI_AGENT,
    result = ActionResult(ActionOutcome.SUCCESS, text = "Signed in as user 42", json = null, error = null, stackTrace = null, durationMillis = 180),
)

private object NoActions : ActionsScreenActions {
    override fun refresh() = Unit

    override fun select(actionId: String) = Unit

    override fun run(actionId: String, arguments: JsonObject) = Unit

    override fun cancel(runId: String) = Unit
}

@Preview
@Composable
private fun ActionsScreenPreview() {
    JwTheme(darkTheme = false) {
        ActionsScreen(
            catalog = ActionCatalog(listOf(loginAs, wipe, fillCard)),
            selectedAction = loginAs,
            history = listOf(finishedRun),
            options = mapOf(loginAs.id to mapOf("email" to listOf("qa@example.com", "pro@example.com"))),
            status = ActionsStatus(message = "Log in as finished in 180 ms.", isError = false),
            query = "",
            pinnedIds = setOf(wipe.id),
            rememberedArguments = emptyMap(),
            actions = NoActions,
            onQueryChange = {},
            onTogglePin = {},
            onRun = { _, _ -> },
        )
    }
}

@Preview
@Composable
private fun ActionsScreenEmptyPreview() {
    JwTheme(darkTheme = true) {
        ActionsScreen(
            catalog = ActionCatalog(emptyList()),
            selectedAction = null,
            history = emptyList(),
            options = emptyMap(),
            status = null,
            query = "",
            pinnedIds = emptySet(),
            rememberedArguments = emptyMap(),
            actions = NoActions,
            onQueryChange = {},
            onTogglePin = {},
            onRun = { _, _ -> },
        )
    }
}

@Preview
@Composable
private fun ActionListPanePreview() {
    JwTheme(darkTheme = false) {
        ActionListPane(
            actions = listOf(wipe, loginAs, fillCard),
            query = "",
            searchFocus = remember { FocusRequester() },
            pinnedIds = setOf(wipe.id),
            selectedId = loginAs.id,
            onQueryChange = {},
            onSelect = {},
            onTogglePin = {},
        )
    }
}

@Preview
@Composable
private fun ActionDetailPanePreview() {
    JwTheme(darkTheme = false) {
        ActionDetailPane(
            action = loginAs,
            options = mapOf("email" to listOf("qa@example.com")),
            rememberedArguments = null,
            runs = listOf(finishedRun),
            onRun = {},
            onCancel = {},
        )
    }
}

@Preview
@Composable
private fun ParameterFieldPreview() {
    JwTheme(darkTheme = false) {
        ParameterField(
            parameter = loginAs.parameters.first(),
            value = "",
            error = "required",
            suggestions = listOf("qa@example.com"),
            onValueChange = {},
        )
    }
}
