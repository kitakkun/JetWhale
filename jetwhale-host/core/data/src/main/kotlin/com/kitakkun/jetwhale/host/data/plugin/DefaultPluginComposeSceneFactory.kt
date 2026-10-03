package com.kitakkun.jetwhale.host.data.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.host.model.DynamicPluginBridgeProvider
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginComposeSceneFactory
import com.kitakkun.jetwhale.host.model.WindowInfoUpdater
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.LocalIsMcpCapture
import com.kitakkun.jetwhale.host.sdk.LocalJetWhalePluginStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@OptIn(InternalComposeUiApi::class, InternalJetWhaleHostApi::class)
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class DefaultPluginComposeSceneFactory(
    private val pluginBridgeProvider: DynamicPluginBridgeProvider,
) : PluginComposeSceneFactory {
    private var hostDensity: Density = Density(1f)

    private var hostPluginAreaSize by mutableStateOf(IntSize.Zero)
    private var hostPluginAreaDpSize by mutableStateOf(DpSize.Zero)

    private val hostPluginAreaWindowInfo: WindowInfo = object : WindowInfo by PlatformContext.Empty().windowInfo {
        override val containerSize: IntSize get() = hostPluginAreaSize
        override val containerDpSize: DpSize get() = hostPluginAreaDpSize
    }

    override fun updateHostDensity(density: Density) {
        hostDensity = density
    }

    override fun updateHostPluginArea(intSize: IntSize, dpSize: DpSize) {
        hostPluginAreaSize = intSize
        hostPluginAreaDpSize = dpSize
    }

    override fun createScene(plugin: JetWhaleHostPlugin, content: @Composable () -> Unit): PluginComposeScene {
        val windowUpdatableContext = DynamicWindowInfoPlatformContext(windowInfoUntilShown = hostPluginAreaWindowInfo)
        val composeScene = CanvasLayersComposeScene(
            density = hostDensity,
            platformContext = windowUpdatableContext,
        )
        val isMcpCapture = mutableStateOf(false)

        var composed = false
        try {
            composeScene.setContent {
                CompositionLocalProvider(
                    LocalJetWhalePluginStorage provides plugin.boundStorageForRuntime(),
                    LocalIsMcpCapture provides isMcpCapture.value,
                ) {
                    pluginBridgeProvider.PluginEntryPoint(content)
                }
            }
            composed = true
        } finally {
            if (!composed) composeScene.close()
        }

        return PluginComposeScene(
            composeScene = composeScene,
            windowInfoUpdater = windowUpdatableContext,
            semanticsOwners = windowUpdatableContext.semanticsOwners,
            isMcpCapture = isMcpCapture,
            pointerIcon = windowUpdatableContext.pointerIcon,
        )
    }
}

@OptIn(InternalComposeUiApi::class)
private class DynamicWindowInfoPlatformContext(
    private val windowInfoUntilShown: WindowInfo,
    private val baseContext: PlatformContext = PlatformContext.Empty(),
) : PlatformContext by baseContext,
    WindowInfoUpdater {
    private var windowInfoOverride: WindowInfo? by mutableStateOf(null)
    override val windowInfo: WindowInfo get() = windowInfoOverride ?: windowInfoUntilShown

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

    // A nested ComposeScene owns no window, and PlatformContext.setPointerIcon is a no-op by
    // default, so Modifier.pointerHoverIcon inside a plugin would otherwise never reach a cursor.
    // Publish the request instead; the renderer applies it to the window it does own.
    val pointerIcon: MutableState<PointerIcon> = mutableStateOf(PointerIcon.Default)
    override fun setPointerIcon(pointerIcon: PointerIcon) {
        this.pointerIcon.value = pointerIcon
    }

    override val currentIntSize: IntSize get() = windowInfo.containerSize
    override val currentDpSize: DpSize get() = windowInfo.containerDpSize

    override fun updateWindowSize(intSize: IntSize, dpSize: DpSize) {
        // LocalWindowInfo is a static composition local: a new WindowInfo, even an equal one,
        // recomposes the whole plugin UI.
        if (intSize == currentIntSize && dpSize == currentDpSize) return
        windowInfoOverride = object : WindowInfo by baseContext.windowInfo {
            override val containerSize: IntSize = intSize
            override val containerDpSize: DpSize = dpSize
        }
    }
}
