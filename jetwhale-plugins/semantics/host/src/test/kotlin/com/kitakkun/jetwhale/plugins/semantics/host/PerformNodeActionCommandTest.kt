package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalJetWhaleApi::class)
class PerformNodeActionCommandTest {
    @Test
    fun `performNodeAction marks the node it acts on`() {
        val command = PerformNodeActionCommand(
            lastSnapshot = { null },
            capture = { error("The root is given, so nothing is captured") },
            perform = { NodeActionResult(performed = true) },
        )

        val marked = command.markedTargets(
            buildJsonObject {
                put("rootId", "window")
                put("nodeId", 7)
                put("action", "Click")
            },
        )

        assertEquals(setOf<Any>(SemanticsUiTarget.Node(rootId = "window", nodeId = 7)), marked)
    }
}
