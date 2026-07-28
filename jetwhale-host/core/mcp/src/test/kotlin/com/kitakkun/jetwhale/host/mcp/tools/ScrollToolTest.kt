package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class ScrollToolTest {

    @Test
    fun `dispatchScroll does not throw on empty scene`() = runBlocking {
        val scene = createTestScene()
        withContext(Dispatchers.Main) {
            dispatchScroll(scene, x = 100f, y = 200f, deltaX = 0f, deltaY = 50f)
        }
    }

    @Test
    fun `dispatchScroll does not throw with negative delta (scroll up)`() = runBlocking {
        val scene = createTestScene()
        withContext(Dispatchers.Main) {
            dispatchScroll(scene, x = 100f, y = 200f, deltaX = 0f, deltaY = -50f)
        }
    }

    @Test
    fun `dispatchScroll does not throw with horizontal delta`() = runBlocking {
        val scene = createTestScene()
        withContext(Dispatchers.Main) {
            dispatchScroll(scene, x = 100f, y = 200f, deltaX = 30f, deltaY = 0f)
        }
    }

    @Test
    fun `dispatchScroll advances vertical scroll position`() = runBlocking {
        var scrollState: ScrollState? = null
        val scene = createTestScene {
            val state = rememberScrollState()
            scrollState = state
            Column(
                modifier = Modifier
                    .size(200.dp)
                    .verticalScroll(state),
            ) {
                repeat(20) { Box(modifier = Modifier.size(50.dp)) }
            }
        }
        renderTestScene(scene)

        withContext(Dispatchers.Main) {
            dispatchScroll(scene, x = 100f, y = 100f, deltaX = 0f, deltaY = 50f)
        }

        val state = checkNotNull(scrollState) { "ScrollState was not captured" }
        assertTrue(state.value > 0, "Expected scroll position to have advanced, but was ${state.value}")
    }

    @Test
    fun `dispatchScroll dispatches scroll events with correct delta direction`() = runBlocking {
        val receivedDeltas = mutableListOf<Offset>()
        val scene = createTestScene {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll) {
                                    event.changes.firstOrNull()?.scrollDelta?.let { receivedDeltas += it }
                                }
                            }
                        }
                    },
            )
        }
        renderTestScene(scene)

        withContext(Dispatchers.Main) {
            dispatchScroll(scene, x = 100f, y = 100f, deltaX = 0f, deltaY = 50f)
        }
        withContext(Dispatchers.Main) {
            dispatchScroll(scene, x = 100f, y = 100f, deltaX = 0f, deltaY = -30f)
        }

        assertEquals(2, receivedDeltas.size, "Expected 2 scroll events to be received")
        assertTrue(receivedDeltas[0].y > 0, "First (down) scroll should have positive deltaY, but was ${receivedDeltas[0].y}")
        assertTrue(receivedDeltas[1].y < 0, "Second (up) scroll should have negative deltaY, but was ${receivedDeltas[1].y}")
    }

    @Test
    fun `scroll defaults the omitted axis to zero`() = runBlocking {
        val receivedDeltas = mutableListOf<Offset>()
        val scene = createTestScene {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll) {
                                    event.changes.firstOrNull()?.scrollDelta?.let { receivedDeltas += it }
                                }
                            }
                        }
                    },
            )
        }
        renderTestScene(scene)

        val server = Server(
            serverInfo = Implementation(name = "test", version = "1.0.0"),
            options = ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools())),
        )
        ScrollMcpTool(FakePluginComposeSceneService(scene)).register(server)
        val handler = server.tools.getValue("jetwhale.scroll").handler

        // Only deltaY is provided; deltaX is omitted and must default to 0.
        val request = CallToolRequest(
            CallToolRequestParams(
                name = "jetwhale.scroll",
                arguments = buildJsonObject {
                    put("pluginId", "plugin")
                    put("sessionId", "session")
                    put("x", 100)
                    put("y", 100)
                    put("deltaY", 50)
                },
            ),
        )

        val result = handler(noOpClientConnection(), request)

        assertTrue(result.isError != true, "Expected a successful result, but was an error")
        assertEquals(1, receivedDeltas.size, "Expected 1 scroll event to be received")
        assertEquals(0f, receivedDeltas[0].x, "Omitted deltaX should default to 0, but was ${receivedDeltas[0].x}")
        assertTrue(receivedDeltas[0].y > 0, "Provided deltaY should be dispatched, but was ${receivedDeltas[0].y}")
    }

    private class FakePluginComposeSceneService(
        private val scene: PluginComposeScene,
    ) : PluginComposeSceneService {
        override fun updateHostDensity(density: Density) = Unit
        override suspend fun getOrCreatePluginScene(pluginId: String, sessionId: String): PluginComposeScene = scene
        override fun disposePluginSceneForSession(sessionId: String) = Unit
        override fun disposePluginScenesForPlugin(pluginId: String) = Unit
        override fun disposeAllPluginScenes() = Unit
    }

    // The scroll handler never touches the connection, so a proxy that rejects every call is enough.
    private fun noOpClientConnection(): ClientConnection = Proxy.newProxyInstance(
        ClientConnection::class.java.classLoader,
        arrayOf(ClientConnection::class.java),
    ) { _, method, _ -> throw UnsupportedOperationException(method.name) } as ClientConnection
}
