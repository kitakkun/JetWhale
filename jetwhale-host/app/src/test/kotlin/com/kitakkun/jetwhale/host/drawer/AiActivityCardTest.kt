package com.kitakkun.jetwhale.host.drawer

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.model.McpClientSetup
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AiActivityCardTest {
    private val setup = McpClientSetup.forServer(host = "localhost", port = 7080)

    @Test
    fun `with MCP running and no agent the card offers the endpoint and the Claude Code command`() = runComposeUiTest {
        setContent {
            JwTheme(darkTheme = false) {
                AiActivityBanner(uiState = AiActivityUiState.Idle.copy(mcpServer = McpServerAvailability.Ready(setup)), onFollowChange = {}, onOpenMcpSettings = {})
            }
        }

        onNodeWithText("Connect an AI agent").performClick()

        onNodeWithText(setup.endpointUrl).assertExists()
        onNodeWithText(setup.claudeCodeCommand).assertExists()
    }

    @Test
    fun `with MCP off the card leads to the MCP settings`() = runComposeUiTest {
        var openedSettings = false
        setContent {
            JwTheme(darkTheme = false) {
                AiActivityBanner(uiState = AiActivityUiState.Idle, onFollowChange = {}, onOpenMcpSettings = { openedSettings = true })
            }
        }

        onNodeWithText("MCP is off").performClick()
        onNodeWithText("Open MCP settings").performClick()

        assertTrue(openedSettings)
    }

    @Test
    fun `a failed MCP server shows why it could not start`() = runComposeUiTest {
        setContent {
            JwTheme(darkTheme = false) {
                AiActivityBanner(uiState = AiActivityUiState.Idle.copy(mcpServer = McpServerAvailability.Off(reason = "port 7080 is in use")), onFollowChange = {}, onOpenMcpSettings = {})
            }
        }

        onNodeWithText("MCP is off").performClick()

        onNodeWithText("The MCP server couldn't start: port 7080 is in use").assertExists()
    }

    @Test
    fun `Enter on the focused card of a connected agent opens the follow switch`() = runComposeUiTest {
        setContent {
            JwTheme(darkTheme = false) {
                AiActivityBanner(
                    uiState = AiActivityUiState.Idle.copy(isAgentConnected = true, mcpServer = McpServerAvailability.Ready(setup)),
                    onFollowChange = {},
                    onOpenMcpSettings = {},
                )
            }
        }

        onNodeWithText("AI agent connected").requestFocus().performKeyInput { pressKey(Key.Enter) }

        onNodeWithText("Follow the AI").assertExists()
    }
}
