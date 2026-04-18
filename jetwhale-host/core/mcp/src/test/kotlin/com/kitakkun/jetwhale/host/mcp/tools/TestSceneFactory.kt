package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.WindowInfoUpdater

@OptIn(InternalComposeUiApi::class)
fun createTestScene(content: @Composable () -> Unit = {}): PluginComposeScene {
    val platformContext = TestPlatformContext()
    val composeScene = CanvasLayersComposeScene(platformContext = platformContext)
    composeScene.setContent(content)
    return PluginComposeScene(
        composeScene = composeScene,
        windowInfoUpdater = platformContext,
        semanticsOwners = platformContext.semanticsOwners,
    )
}

@OptIn(InternalComposeUiApi::class)
private class TestPlatformContext(
    private val base: PlatformContext = PlatformContext.Empty(),
) : PlatformContext by base,
    WindowInfoUpdater {

    val semanticsOwners = mutableSetOf<SemanticsOwner>()

    override val semanticsOwnerListener = object : PlatformContext.SemanticsOwnerListener {
        override fun onSemanticsOwnerAppended(semanticsOwner: SemanticsOwner) {
            semanticsOwners.add(semanticsOwner)
        }
        override fun onSemanticsOwnerRemoved(semanticsOwner: SemanticsOwner) {
            semanticsOwners.remove(semanticsOwner)
        }
        override fun onSemanticsChange(semanticsOwner: SemanticsOwner) = Unit
        override fun onLayoutChange(semanticsOwner: SemanticsOwner, semanticsNodeId: Int) = Unit
    }

    override val currentIntSize: IntSize get() = IntSize(1280, 720)
    override val currentDpSize: DpSize get() = DpSize(1280.dp, 720.dp)
    override fun updateWindowSize(intSize: IntSize, dpSize: DpSize) {}
}
