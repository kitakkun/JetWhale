package com.kitakkun.jetwhale.plugins.nav3.host

import com.kitakkun.jetwhale.plugins.nav3.protocol.MutationResult
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackOperation
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackSnapshot
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeySnapshot
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyTypeDescriptor
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Stands in for the debuggee: records what the commands ask for and replies with a canned result. */
internal class FakeNav3BackStackController(
    private val stacks: List<NavBackStackSnapshot>,
    private val keyTypes: List<NavKeyTypeDescriptor> = emptyList(),
    private val result: MutationResult = MutationResult(error = null, snapshot = stacks.firstOrNull()),
) : Nav3BackStackController {
    val requests: MutableList<Pair<String, List<NavBackStackOperation>>> = mutableListOf()

    override fun stacks(): List<NavBackStackSnapshot> = stacks

    override fun keyTypes(): List<NavKeyTypeDescriptor> = keyTypes

    override suspend fun mutate(stackId: String, operations: List<NavBackStackOperation>): MutationResult {
        requests += stackId to operations
        return result
    }
}

internal fun navKey(type: String, id: String? = null): JsonElement = buildJsonObject {
    put("type", type)
    id?.let { put("id", it) }
}

internal fun snapshot(stackId: String, vararg typeNames: String): NavBackStackSnapshot = NavBackStackSnapshot(
    stackId = stackId,
    entries = typeNames.map { typeName ->
        NavKeySnapshot(typeName = typeName, display = "$typeName()", key = navKey(typeName))
    },
)
