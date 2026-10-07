package com.kitakkun.jetwhale.plugins.actions.host

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionCatalog
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionDescriptor
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionOutcome
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionParameter
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionResult
import com.kitakkun.jetwhale.plugins.actions.protocol.ParameterType
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsShot
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsShotRecorder
import com.kitakkun.jetwhale.tools.docsscreenshots.InMemoryPluginStorage
import com.kitakkun.jetwhale.tools.docsscreenshots.PluginSceneSurface
import com.kitakkun.jetwhale.tools.docsscreenshots.onSurface
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

/**
 * [ActionsScreen] rather than its Root: the Root only binds a live [ActionsBrowser], which needs the
 * app on the other end, to the screen.
 */
@OptIn(ExperimentalTestApi::class)
class DebugActionsDocsScreenshots {
    private val recorder = DocsShotRecorder.forImagesDirectoryProperty()

    @Test
    fun `the actions with one selected and its runs`() = recorder.record(
        DocsShot(page = "debug-actions", name = "actions", surfaceSize = DpSize(860.dp, 520.dp), density = 1.6f, displayWidth = 688),
    ) { darkTheme ->
        setContent {
            PluginSceneSurface(darkTheme = darkTheme, storage = InMemoryPluginStorage(emptyMap())) {
                ActionsScreen(
                    catalog = ActionCatalog(listOf(LOG_IN_AS, LOG_OUT, RESET_ONBOARDING, SHIFT_CLOCK, OPEN_DEEP_LINK, WIPE_LOCAL_DATA)),
                    selectedAction = LOG_IN_AS,
                    tab = ActionsTab.ACTIONS,
                    history = RUNS,
                    selectedRunId = null,
                    prefill = null,
                    options = mapOf(LOG_IN_AS.id to mapOf("email" to listOf("qa@example.com", "pro@example.com"))),
                    status = null,
                    query = "",
                    pinnedIds = setOf(WIPE_LOCAL_DATA.id),
                    rememberedArguments = mapOf(LOG_IN_AS.id to arguments("email" to "pro@example.com", "tier" to "PRO")),
                    actions = NoActionsScreenActions,
                    onQueryChange = {},
                    onTogglePin = {},
                    onRun = { _, _, _ -> },
                )
            }
        }
        onSurface()
    }
}

private object NoActionsScreenActions : ActionsScreenActions {
    override fun refresh() = Unit

    override fun select(actionId: String) = Unit

    override fun run(actionId: String, arguments: JsonObject, confirmedDestructive: Boolean) = Unit

    override fun cancel(runId: String) = Unit

    override fun showTab(tab: ActionsTab) = Unit

    override fun selectRun(runId: String) = Unit

    override fun prefillFormFromRun(runId: String) = Unit
}

private val LOG_IN_AS = ActionDescriptor(
    id = "Account / Log in as",
    title = "Log in as",
    group = "Account",
    description = "Signs in with a test account, skipping the sign-in screen.",
    destructive = false,
    scoped = false,
    parameters = listOf(
        ActionParameter("email", ParameterType.STRING, optional = false, nullable = false, description = "The test account", enumValues = emptyList(), hasOptions = true),
        ActionParameter("tier", ParameterType.ENUM, optional = false, nullable = false, description = null, enumValues = listOf("FREE", "PRO"), hasOptions = false),
    ),
)

private val LOG_OUT = ActionDescriptor(
    id = "Account / Log out",
    title = "Log out",
    group = "Account",
    description = null,
    destructive = false,
    scoped = false,
    parameters = emptyList(),
)

private val RESET_ONBOARDING = ActionDescriptor(
    id = "Onboarding / Reset onboarding",
    title = "Reset onboarding",
    group = "Onboarding",
    description = "Shows the onboarding again on the next launch.",
    destructive = false,
    scoped = false,
    parameters = emptyList(),
)

private val SHIFT_CLOCK = ActionDescriptor(
    id = "Clock / Shift the clock",
    title = "Shift the clock",
    group = "Clock",
    description = null,
    destructive = false,
    scoped = false,
    parameters = listOf(
        ActionParameter("hours", ParameterType.INTEGER, optional = false, nullable = false, description = "Negative goes back", enumValues = emptyList(), hasOptions = false),
    ),
)

private val OPEN_DEEP_LINK = ActionDescriptor(
    id = "Navigation / Open deep link",
    title = "Open deep link",
    group = "Navigation",
    description = null,
    destructive = false,
    scoped = false,
    parameters = listOf(
        ActionParameter("url", ParameterType.STRING, optional = false, nullable = false, description = null, enumValues = emptyList(), hasOptions = false),
    ),
)

private val WIPE_LOCAL_DATA = ActionDescriptor(
    id = "Wipe local data",
    title = "Wipe local data",
    group = null,
    description = null,
    destructive = true,
    scoped = false,
    parameters = emptyList(),
)

/** Newest first, as the browser keeps them. */
private val RUNS = listOf(
    RunRecord(
        runId = "3",
        actionId = LOG_IN_AS.id,
        title = LOG_IN_AS.title,
        arguments = arguments("email" to "pro@example.com", "tier" to "PRO"),
        origin = RunOrigin.AI_AGENT,
        startedAtMillis = 1_791_021_720_000,
        result = ActionResult(ActionOutcome.SUCCESS, text = "Signed in as pro@example.com (PRO)", json = null, error = null, stackTrace = null, durationMillis = 214),
    ),
    RunRecord(
        runId = "2",
        actionId = LOG_IN_AS.id,
        title = LOG_IN_AS.title,
        arguments = arguments("email" to "nobody@example.com", "tier" to "FREE"),
        origin = RunOrigin.USER,
        startedAtMillis = 1_791_021_660_000,
        result = ActionResult(ActionOutcome.FAILURE, text = null, json = null, error = "No test account nobody@example.com", stackTrace = null, durationMillis = 35),
    ),
    RunRecord(
        runId = "1",
        actionId = LOG_IN_AS.id,
        title = LOG_IN_AS.title,
        arguments = arguments("email" to "qa@example.com", "tier" to "FREE"),
        origin = RunOrigin.USER,
        startedAtMillis = 1_791_021_600_000,
        result = ActionResult(ActionOutcome.SUCCESS, text = "Signed in as qa@example.com (FREE)", json = null, error = null, stackTrace = null, durationMillis = 180),
    ),
)

private fun arguments(vararg values: Pair<String, String>) = JsonObject(values.associate { (name, value) -> name to JsonPrimitive(value) })
