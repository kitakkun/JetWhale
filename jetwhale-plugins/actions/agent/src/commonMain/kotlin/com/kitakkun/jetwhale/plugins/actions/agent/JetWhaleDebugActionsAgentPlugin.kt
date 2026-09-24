package com.kitakkun.jetwhale.plugins.actions.agent

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.plugins.actions.protocol.ACTIONS_PLUGIN_ID
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionCatalog
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionDescriptor
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionOptions
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionOutcome
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionResult
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionsChanged
import com.kitakkun.jetwhale.plugins.actions.protocol.CancelActionRun
import com.kitakkun.jetwhale.plugins.actions.protocol.CancelResult
import com.kitakkun.jetwhale.plugins.actions.protocol.GetActionOptions
import com.kitakkun.jetwhale.plugins.actions.protocol.ListActions
import com.kitakkun.jetwhale.plugins.actions.protocol.RunAction
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.reply
import com.kitakkun.jetwhale.protocol.messaging.trySend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * Agent plugin that lets the app offer debug actions — logging in as a test user, resetting
 * onboarding, shifting the clock — to the JetWhale host and, through its MCP tools, to AI agents.
 *
 * ```kotlin
 * val actionsPlugin = JetWhaleDebugActionsAgentPlugin()
 * actionsPlugin.register {
 *     platformBuiltInActions()
 *     action("Reset onboarding") { run { onboarding.reset() } }
 * }
 * startJetWhale { plugins { register(actionsPlugin) } }
 * ```
 *
 * Actions can be registered at any time and from anywhere; a screen that has actions of its own
 * registers them while it is shown (see `DebugActions` in the Compose artifact) and the host sees
 * them come and go.
 */
@OptIn(InternalJetWhaleApi::class)
class JetWhaleDebugActionsAgentPlugin : JetWhaleAgentPlugin() {
    override val pluginId: String get() = ACTIONS_PLUGIN_ID
    override val pluginVersion: String get() = "1.0.0"

    private val json = Json { ignoreUnknownKeys = false }
    private val registered = MutableStateFlow<List<RegisteredAction>>(emptyList())
    private val runs = MutableStateFlow<Map<String, Deferred<ActionResult>>>(emptyMap())

    // Non-null exactly while the host has this plugin activated: changes are pushed then, and runs
    // live in it so that deactivating the plugin cancels whatever is still running.
    private var activeScope: CoroutineScope? = null

    /** Registers the actions [content] declares, until the returned registration is removed. */
    fun register(content: DebugActionsBuilder.() -> Unit): DebugActionsRegistration = add(scoped = false, content)

    /**
     * Registers actions that belong to part of the UI and exist only while it is shown; the host
     * marks them so. The Compose artifact's `DebugActions` calls this for the duration of its
     * composition — prefer it to pairing this with `unregister` by hand.
     */
    fun registerScoped(content: DebugActionsBuilder.() -> Unit): DebugActionsRegistration = add(scoped = true, content)

    private fun add(scoped: Boolean, content: DebugActionsBuilder.() -> Unit): DebugActionsRegistration {
        val definitions = DebugActionsBuilder(group = null).apply(content).definitions
        val handle = Any()
        registered.update { current ->
            val takenIds = current.mapTo(mutableSetOf(), RegisteredAction::id)
            current + definitions.map { definition ->
                RegisteredAction(id = uniqueId(definition, takenIds).also(takenIds::add), definition = definition, scoped = scoped, owner = handle)
            }
        }
        return DebugActionsRegistration { registered.update { current -> current.filterNot { it.owner === handle } } }
    }

    override fun JetWhaleMessageHandlers.configure() {
        onRequest { _: ListActions -> reply(catalog()) }
        onRequest { request: GetActionOptions -> reply(options(request)) }
        onRequest { request: RunAction -> reply(runAction(request)) }
        onRequest { request: CancelActionRun ->
            val run = runs.value[request.runId]
            run?.cancel()
            reply(CancelResult(cancelled = run != null))
        }
    }

    override fun onActivate() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        activeScope = scope
        scope.launch {
            // The host asks for the whole catalog when it connects; only later changes are news.
            registered.drop(1).collect { messenger.trySend(ActionsChanged(catalog())) }
        }
    }

    override fun onDeactivate() {
        activeScope?.cancel()
        activeScope = null
    }

    /** The actions registered right now, as the host sees them. For tests of code that registers actions. */
    @InternalJetWhaleApi
    fun catalog(): ActionCatalog = ActionCatalog(registered.value.map(RegisteredAction::toDescriptor))

    private suspend fun options(request: GetActionOptions): ActionOptions {
        val provider = registered.value.firstOrNull { it.id == request.actionId }?.definition?.optionProviders?.get(request.parameter)
            ?: return ActionOptions(values = emptyList(), error = "'${request.parameter}' of '${request.actionId}' has no options")
        return try {
            ActionOptions(values = provider(), error = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionOptions(values = emptyList(), error = e.message ?: "the options could not be read")
        }
    }

    /** Runs the action [request] names, as it is registered now. */
    @InternalJetWhaleApi
    suspend fun runAction(request: RunAction): ActionResult {
        val action = registered.value.firstOrNull { it.id == request.actionId }
            ?: return ActionResult(ActionOutcome.FAILURE, text = null, json = null, error = "no action has the id '${request.actionId}'; list the actions again", stackTrace = null, durationMillis = 0)
        if (action.definition.destructive && !request.confirmedDestructive) {
            return ActionResult(ActionOutcome.FAILURE, text = null, json = null, error = "'${action.definition.title}' is destructive and the run was not confirmed; list the actions again and confirm", stackTrace = null, durationMillis = 0)
        }
        val scope = activeScope
            ?: return ActionResult(ActionOutcome.FAILURE, text = null, json = null, error = "the plugin is not active", stackTrace = null, durationMillis = 0)
        val deferred = scope.async { action.definition.runWith(request.arguments, json) }
        runs.update { it + (request.runId to deferred) }
        return try {
            deferred.await()
        } catch (_: CancellationException) {
            // The run was cancelled on request; the request handling itself carries on to reply.
            currentCoroutineContext().ensureActive()
            ActionResult(ActionOutcome.CANCELLED, text = null, json = null, error = "the run was cancelled", stackTrace = null, durationMillis = 0)
        } finally {
            runs.update { it - request.runId }
        }
    }
}

/** Removes the actions one [JetWhaleDebugActionsAgentPlugin.register] call added. */
fun interface DebugActionsRegistration {
    fun unregister()
}

private class RegisteredAction(
    val id: String,
    val definition: DebugActionDefinition<*>,
    val scoped: Boolean,
    val owner: Any,
) {
    fun toDescriptor(): ActionDescriptor = ActionDescriptor(
        id = id,
        title = definition.title,
        group = definition.group,
        description = definition.description,
        destructive = definition.destructive,
        scoped = scoped,
        parameters = definition.argumentSerializer.descriptor.toActionParameters(definition.optionProviders.keys),
    )
}

/**
 * The action's path — group and title — made unique among [takenIds] by a numeric suffix, since a
 * screen shown twice registers the same actions twice.
 */
private fun uniqueId(definition: DebugActionDefinition<*>, takenIds: Set<String>): String {
    val base = listOfNotNull(definition.group, definition.title).joinToString(" / ")
    return generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base #$it" }.first { it !in takenIds }
}
