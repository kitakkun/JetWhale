package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.sdk.LocalIsMcpCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SECRET = "Bearer super-secret-token"
private const val MASKED = "Bearer ***"

/**
 * The semantics tree carries the same strings the screenshot does, so a plugin that redacts for
 * capture has to be redacted here too — otherwise the tree is a way around
 * [LocalIsMcpCapture], which exists to keep values on screen but away from MCP agents.
 */
class AccessibilityTreeRedactionTest {

    @Test
    fun `a value the plugin redacts for capture does not reach the semantics tree`() = runBlocking {
        val scene = createTestScene {
            val shown = if (LocalIsMcpCapture.current) MASKED else SECRET
            Box(Modifier.size(100.dp).semantics { text = AnnotatedString(shown) })
        }
        renderTestScene(scene)

        val tree = withContext(Dispatchers.Main) { captureAccessibilityTree(scene) }

        assertFalse(SECRET in tree, "Unredacted value leaked into the semantics tree: $tree")
        assertTrue(MASKED in tree, "Expected the redacted value in the tree instead: $tree")
    }

    @Test
    fun `a content description the plugin redacts for capture does not reach the semantics tree`() = runBlocking {
        val scene = createTestScene {
            val label = if (LocalIsMcpCapture.current) MASKED else SECRET
            Box(Modifier.size(100.dp).semantics { contentDescription = label })
        }
        renderTestScene(scene)

        val tree = withContext(Dispatchers.Main) { captureAccessibilityTree(scene) }

        assertFalse(SECRET in tree, "Unredacted content description leaked into the semantics tree: $tree")
        // Without this the test would also pass if content descriptions stopped being captured at all.
        assertTrue(MASKED in tree, "Expected the redacted content description in the tree instead: $tree")
    }

    @Test
    fun `the interactive composition is left unredacted after a tree capture`() = runBlocking {
        val observed = mutableListOf<Boolean>()
        val scene = createTestScene {
            observed.add(LocalIsMcpCapture.current)
        }
        renderTestScene(scene)

        withContext(Dispatchers.Main) { captureAccessibilityTree(scene) }
        renderTestScene(scene)

        assertTrue(observed.contains(true), "The tree capture must render with capture=true")
        assertFalse(observed.last(), "The composition must return to capture=false afterwards")
    }
}
