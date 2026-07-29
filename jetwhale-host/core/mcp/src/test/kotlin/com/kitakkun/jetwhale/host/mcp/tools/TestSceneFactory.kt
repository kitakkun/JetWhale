package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.WindowInfoUpdater
import com.kitakkun.jetwhale.host.sdk.LocalIsMcpCapture

const val TEST_SCENE_WIDTH = 1280
const val TEST_SCENE_HEIGHT = 720

@OptIn(InternalComposeUiApi::class)
fun createTestScene(content: @Composable () -> Unit = {}): PluginComposeScene {
    val platformContext = TestPlatformContext()
    val composeScene = CanvasLayersComposeScene(platformContext = platformContext)
    val isMcpCapture = mutableStateOf(false)
    composeScene.setContent {
        CompositionLocalProvider(LocalIsMcpCapture provides isMcpCapture.value) {
            content()
        }
    }
    return PluginComposeScene(
        composeScene = composeScene,
        windowInfoUpdater = platformContext,
        semanticsOwners = platformContext.semanticsOwners,
        isMcpCapture = isMcpCapture,
        pointerIcon = platformContext.pointerIcon,
    )
}

@OptIn(InternalComposeUiApi::class)
fun renderTestScene(scene: PluginComposeScene) {
    scene.composeScene.size = IntSize(TEST_SCENE_WIDTH, TEST_SCENE_HEIGHT)
    scene.composeScene.render(Canvas(ImageBitmap(TEST_SCENE_WIDTH, TEST_SCENE_HEIGHT)), System.nanoTime())
}

@OptIn(InternalComposeUiApi::class)
private class TestPlatformContext(
    private val base: PlatformContext = PlatformContext.Empty(),
) : PlatformContext by base,
    WindowInfoUpdater {

    val semanticsOwners = mutableSetOf<SemanticsOwner>()

    val pointerIcon = mutableStateOf(PointerIcon.Default)
    override fun setPointerIcon(pointerIcon: PointerIcon) {
        this.pointerIcon.value = pointerIcon
    }

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

    override val currentIntSize: IntSize get() = IntSize(TEST_SCENE_WIDTH, TEST_SCENE_HEIGHT)
    override val currentDpSize: DpSize get() = DpSize(TEST_SCENE_WIDTH.dp, TEST_SCENE_HEIGHT.dp)
    override fun updateWindowSize(intSize: IntSize, dpSize: DpSize) {}
}
