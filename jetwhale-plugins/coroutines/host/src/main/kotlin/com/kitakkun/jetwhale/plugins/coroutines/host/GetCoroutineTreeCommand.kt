package com.kitakkun.jetwhale.plugins.coroutines.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineNode
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineState
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetCoroutineTreeCommand(
    private val client: CoroutineInspectorClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getCoroutineTree"
    override val description =
        "Returns the app's coroutines below the scopes it registered, as a tree: each with an id, its CoroutineName, state (New, Active, Cancelling, Completed, Cancelled), how long the agent has seen it (observedMillis), its dispatcher and its Job description. " +
            "To find stuck or leaking coroutines, filter by minObservedSeconds and look for many Active coroutines of one name, or Cancelling ones that never finish. Filters keep the ancestors of every match."

    private val state by enumOrNull("Only coroutines in this state.", CoroutineState.entries)
    private val nameContains by stringOrNull("Only coroutines whose name or Job description contains this text, ignoring case.")
    private val dispatcherContains by stringOrNull("Only coroutines whose dispatcher contains this text, ignoring case.")
    private val minObservedSeconds by longOrNull("Only coroutines the agent has seen for at least this many seconds.")
    private val namedOnly by booleanOrNull("Only coroutines given a CoroutineName; hides the unnamed ones libraries such as Compose start.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val tree = client.coroutineTree()
        val filter = CoroutineFilter(
            states = setOfNotNull(arguments[state]),
            nameContains = arguments[nameContains],
            dispatcherContains = arguments[dispatcherContains],
            minObservedMillis = arguments[minObservedSeconds]?.let { seconds ->
                if (seconds !in 0..MAX_MIN_OBSERVED_SECONDS) throw JetWhaleMcpArgumentException("minObservedSeconds must be between 0 and $MAX_MIN_OBSERVED_SECONDS; got $seconds")
                seconds * 1_000
            },
            namedOnly = arguments[namedOnly] == true,
        )
        return buildJsonObject {
            put("coroutineCount", tree.coroutineCount)
            if (filter != CoroutineFilter.None) put("matchingCount", countMatchingCoroutines(tree.roots, filter))
            put("truncated", tree.truncated)
            put("roots", McpJson.encodeToJsonElement(ListSerializer(CoroutineNode.serializer()), filterCoroutineTree(tree.roots, filter)))
            if (tree.roots.isEmpty()) {
                put("note", "The app registered no scopes; it must call inspector.register(scope, name) for its coroutines to be visible.")
            }
        }.toString()
    }
}

/** A year: longer than any coroutine an app keeps, and far from overflowing once in milliseconds. */
private const val MAX_MIN_OBSERVED_SECONDS = 365L * 24 * 60 * 60
