package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeRef
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalJetWhaleApi::class)
class NodeMcpTextTest {
    private val send = node(
        id = 7,
        role = "Button",
        text = "Send",
        testTag = "send-button",
        actions = listOf("OnClick"),
        isClickable = true,
        bounds = NodeBounds(left = 10f, top = 20f, right = 110f, bottom = 60f),
    )
    private val screen = snapshot(root("window", label = "MainActivity", node = node(id = 1, children = listOf(node(id = 2, text = "Hello"), send))))

    @Test
    fun `the outline writes each root on its own line and indents nodes by depth`() {
        assertEquals(
            """
            root window "MainActivity" unit=px density=2.0
            - node #1 tap=50,20
              - node #2 "Hello" tap=50,20
              - Button #7 "Send" tag=send-button [clickable] actions=Click tap=60,40
            """.trimIndent(),
            screen.toMcpText(),
        )
    }

    @Test
    fun `a node that cannot be reached says why in flags`() {
        val covered = node(id = 7, isClickable = true, isEnabled = false, isHittable = false, obscuredBy = NodeRef(rootId = "dialog", nodeId = 3))

        assertEquals("- node #7 [clickable] [disabled] [inoperable] [unhittable] obscuredBy=dialog#3 tap=50,20", covered.toMcpTextLine(rootId = null))
    }

    @Test
    fun `quotes and line breaks in app text stay inside one line`() {
        assertEquals("- node #3 \"say \\\"hi\\\"\\nbye\" tap=50,20", node(id = 3, text = "say \"hi\"\nbye").toMcpTextLine(rootId = null))
    }

    @Test
    fun `actions are the names performNodeAction accepts and leave out platform actions it cannot run`() {
        val line = node(id = 5, actions = listOf("SetTextSubstitution", "OnClick", "PerformImeAction")).toMcpTextLine(rootId = null)

        assertEquals("- node #5 actions=Click,ImeAction tap=50,20", line)
    }

    @Test
    fun `a node exposing only actions nothing can perform lists no actions`() {
        assertEquals("- node #5 tap=50,20", node(id = 5, actions = listOf("SetTextSubstitution")).toMcpTextLine(rootId = null))
    }

    @Test
    fun `a root without content claims no unit`() {
        assertEquals("root window \"Empty\" density=2.0", snapshot(root("window", label = "Empty", node = null)).toMcpText().trim())
    }

    @Test
    fun `a node with no bounds on screen has no tap point`() {
        assertEquals("- node #3", node(id = 3, bounds = NodeBounds(left = 0f, top = 0f, right = 0f, bottom = 0f)).toMcpTextLine(rootId = null))
    }

    @Test
    fun `View and iOS nodes are named by class and an iOS root is in points`() {
        val view = viewNode(id = -4, viewClass = "android.widget.TextView", resourceId = "status", text = "Ready")
        val apple = appleNode(id = -9, className = "UIButton", accessibilityIdentifier = "login", traits = listOf("button"), isClickable = true)

        assertEquals("- TextView #-4 \"Ready\" resId=status tap=50,20", view.toMcpTextLine(rootId = null))
        assertEquals("root ios-window \"Key window\" unit=pt density=2.0", snapshot(root("ios-window", label = "Key window", node = apple)).toMcpText().lineSequence().first())
        assertEquals("- UIButton #-9 axId=login traits=button [clickable] tap=50,20", apple.toMcpTextLine(rootId = null))
    }

    @Test
    fun `getNodeTree returns JSON unless the text format is asked for`() {
        val command = GetNodeTreeCommand(capture = { screen })

        val json = runBlocking { command.execute(JetWhaleMcpArguments(buildJsonObject {})) }
        val text = runBlocking { command.execute(JetWhaleMcpArguments(buildJsonObject { put("format", "text") })) }

        assertTrue("roots" in Json.parseToJsonElement(json).jsonObject)
        assertEquals(screen.toMcpText(), text)
    }

    @Test
    fun `both tools advertise the format values a caller passes`() {
        val advertised = listOf(GetNodeTreeCommand(capture = { screen }), FindNodesCommand(capture = { screen })).map { command ->
            command.toDescriptor().parameters.getValue("format").schema["enum"]?.jsonArray?.map { it.jsonPrimitive.content }
        }

        assertEquals(listOf(listOf("json", "text"), listOf("json", "text")), advertised)
    }

    @Test
    fun `findNodes in text addresses each node by root and says how many the limit left out`() {
        val command = FindNodesCommand(capture = { screen })

        val text = runBlocking {
            command.execute(
                JetWhaleMcpArguments(
                    buildJsonObject {
                        put("format", "text")
                        put("interactiveOnly", false)
                        put("limit", 1)
                    },
                ),
            )
        }

        assertEquals("- node #1 root=window tap=50,20\n… 2 more matches", text)
    }

    @Test
    fun `findNodes in text with nothing matching says so`() {
        val text = runBlocking {
            FindNodesCommand(capture = { screen }).execute(
                JetWhaleMcpArguments(
                    buildJsonObject {
                        put("format", "text")
                        put("testTag", "missing")
                    },
                ),
            )
        }

        assertEquals("no matches", text)
    }
}
