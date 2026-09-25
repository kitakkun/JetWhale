package com.kitakkun.jetwhale.plugins.coroutines.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetCoroutineDetailCommand(
    private val client: CoroutineInspectorClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getCoroutineDetail"
    override val description =
        "Returns one coroutine in depth, by an id from getCoroutineTree: its state, dispatcher, observedMillis, full Job description, the names on the path from its registered scope, " +
            "and how many coroutines below it are in each state. On the JVM with kotlinx-coroutines-debug's DebugProbes installed it also returns debugState (RUNNING or SUSPENDED, which the state Active cannot tell apart) " +
            "and the stack it is suspended at; otherwise stackUnavailableReason says why. found is false when the coroutine is gone (finished, or its scope was dropped)."

    private val id by string("The coroutine's id, as getCoroutineTree gives it (for example c12).")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val requested = arguments[id]
        // Reading the tree first both finds the coroutine and has the agent walk it, so the id is
        // one the agent can still resolve to its Job.
        val location = findCoroutine(client.coroutineTree().roots, requested)
            ?: return buildJsonObject {
                put("found", false)
                put("note", "No coroutine has the id '$requested' now: it finished, its scope was dropped, or the id is from another session. Read getCoroutineTree again.")
            }.toString()
        val node = location.node
        val detail = client.coroutineDetail(requested)
        return buildJsonObject {
            put("found", detail.found)
            put("id", node.id)
            put("name", node.name)
            put("state", node.state.name)
            put("dispatcher", node.dispatcher)
            put("observedMillis", node.observedMillis)
            put("description", node.description)
            putJsonArray("path") { location.ancestors.forEach { add(it.name ?: it.description) } }
            put("children", node.children.size)
            putJsonObject("descendantsByState") { node.descendantStates().forEach { (state, count) -> put(state.name, count) } }
            put("debugState", detail.debugState)
            putJsonArray("suspensionStack") { detail.suspensionStack.forEach(::add) }
            putJsonArray("creationStack") { detail.creationStack.forEach(::add) }
            put("stackUnavailableReason", detail.stackUnavailableReason)
        }.toString()
    }
}
