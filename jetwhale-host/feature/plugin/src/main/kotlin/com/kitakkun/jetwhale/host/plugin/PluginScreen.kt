package com.kitakkun.jetwhale.host.plugin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.DpSize
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import kotlinx.coroutines.CancellationException
import soil.plant.compose.reacty.LocalCatchThrowHost
import soil.query.core.uuid

@OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
// Draws a live plugin scene; there is nothing a preview could show.
@Suppress("KOTRAIL_COMPOSABLE_WITHOUT_PREVIEW")
@Composable
fun PluginScreen(pluginComposeScene: PluginComposeScene) {
    var frameNanoTime by remember(pluginComposeScene) { mutableLongStateOf(0L) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(pluginComposeScene) {
        while (true) {
            withFrameNanos {
                frameNanoTime = it
            }
        }
    }

    val density = LocalDensity.current

    val catchThrowHost = LocalCatchThrowHost.current
    Canvas(
        modifier = Modifier.fillMaxSize()
            .pointerHoverIcon(pluginComposeScene.pointerIcon.value)
            .onSizeChanged {
                try {
                    pluginComposeScene.composeScene.density = density
                    pluginComposeScene.composeScene.size = it
                } catch (_: IllegalStateException) {
                    // The setters throw once the ComposeScene is closed, which can happen during
                    // dispose.
                }
                pluginComposeScene.windowInfoUpdater.updateWindowSize(
                    intSize = it,
                    dpSize = with(density) { DpSize(it.width.toDp(), it.height.toDp()) },
                )
            }
            .focusRequester(focusRequester)
            .focusable()
            // On a session switch the swapped-in Canvas is not yet placed when composition runs,
            // and a requestFocus before placement is silently dropped.
            .onPlaced { focusRequester.requestFocus() }
            .pointerInput(pluginComposeScene) {
                awaitPointerEventScope {
                    do {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press) {
                            // Whether the plugin handled the press is known only inside its scene,
                            // so the host's clearFocusOnBlankPress must not see it as unhandled; and
                            // the plugin's keys arrive through this node, which a host control may
                            // have taken focus from.
                            focusRequester.requestFocus()
                            event.changes.forEach(PointerInputChange::consume)
                        }
                        // Plugin UI code runs inside this dispatch; whatever it throws is shown as
                        // the plugin's error instead of taking the host down.
                        @Suppress("KOTRAIL_CATCH_TOO_BROAD")
                        try {
                            val scrollDelta = event.changes.map(PointerInputChange::scrollDelta).reduce(Offset::plus)

                            pluginComposeScene.composeScene.sendPointerEvent(
                                eventType = event.type,
                                pointers = event.toComposeScenePointers(),
                                buttons = event.buttons,
                                keyboardModifiers = event.keyboardModifiers,
                                scrollDelta = scrollDelta,
                                nativeEvent = event.nativeEvent,
                                button = event.button,
                            )
                        } catch (e: CancellationException) {
                            // Caught ahead of IllegalStateException, which it extends on the JVM.
                            throw e
                        } catch (e: IllegalStateException) {
                            if (!e.isComposeSceneClosed()) catchThrowHost[uuid()] = e
                        } catch (e: Throwable) {
                            catchThrowHost[uuid()] = e
                        }
                    } while (true)
                }
            }
            .onKeyEvent {
                // Plugin UI code runs inside this dispatch; whatever it throws is shown as the
                // plugin's error instead of taking the host down.
                @Suppress("KOTRAIL_CATCH_TOO_BROAD")
                try {
                    pluginComposeScene.composeScene.sendKeyEvent(it)
                } catch (e: IllegalStateException) {
                    if (!e.isComposeSceneClosed()) catchThrowHost[uuid()] = e
                    false
                } catch (e: Throwable) {
                    catchThrowHost[uuid()] = e
                    false
                }
            },
    ) {
        // Reading the host's frame time here is what keeps this draw invalidated once per frame, so
        // the plugin's own animations keep advancing. It is deliberately not the scene's animation
        // clock: PluginComposeScene.render owns that, because this scene is also rendered by the MCP
        // tools on a different clock.
        @Suppress("UNUSED_EXPRESSION")
        frameNanoTime
        this.drawIntoCanvas(pluginComposeScene::render)
    }
}

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
private fun PointerEvent.toComposeScenePointers(): List<ComposeScenePointer> = this.changes.map { pointerInputChange ->
    ComposeScenePointer(
        id = pointerInputChange.id,
        position = pointerInputChange.position,
        pressed = pointerInputChange.pressed,
        type = pointerInputChange.type,
        pressure = pointerInputChange.pressure,
        historical = pointerInputChange.historical,
    )
}

/**
 * True when this exception is the benign "input/render after the scene was closed" race that Compose
 * throws if we dispatch to a [androidx.compose.ui.scene.ComposeScene] that has already been disposed
 * (e.g. during navigation, session switch, or hot reload).
 */
private fun IllegalStateException.isComposeSceneClosed(): Boolean = message?.contains("ComposeScene is closed") == true
