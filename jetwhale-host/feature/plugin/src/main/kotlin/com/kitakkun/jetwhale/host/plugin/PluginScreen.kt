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
import com.kitakkun.jetwhale.host.model.isComposeSceneClosed
import kotlinx.coroutines.CancellationException
import soil.plant.compose.reacty.LocalCatchThrowHost
import soil.query.core.uuid

// This draws a live plugin's nested ComposeScene, which a @Preview has no way to build.
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

    val catchThrowHost = LocalCatchThrowHost.current
    // A plugin UI that threw while composing, laying out or drawing hands its exception to the
    // error boundary, which replaces this screen with the crash fallback and its Reload button.
    val failure = pluginComposeScene.failure.value
    LaunchedEffect(failure) {
        if (failure != null) catchThrowHost[uuid()] = failure
    }

    val density = LocalDensity.current
    Canvas(
        modifier = Modifier.fillMaxSize()
            // The plugin's scene is nested and windowless, so its Modifier.pointerHoverIcon requests
            // surface here instead; this Canvas is the innermost node that sits in a real window.
            .pointerHoverIcon(pluginComposeScene.pointerIcon.value)
            .onSizeChanged {
                try {
                    // The scene was seeded with a density when it was created, but only the window
                    // actually showing it knows the right one: a popped-out plugin gets its own
                    // Window, which can sit on a display with a different scale factor.
                    pluginComposeScene.composeScene.density = density
                    pluginComposeScene.composeScene.size = it
                } catch (_: IllegalStateException) {
                    // ignore: may happen during dispose
                    // without this try-catch, sometimes crashes with:
                    // java.lang.IllegalStateException: size/density set after ComposeScene is closed
                    // See: [androidx.compose.ui.scene.CanvasLayersComposeScene] implementation of size and density setter
                }
                pluginComposeScene.windowInfoUpdater.updateWindowSize(
                    intSize = it,
                    dpSize = with(density) { DpSize(it.width.toDp(), it.height.toDp()) },
                )
            }
            .focusRequester(focusRequester)
            .focusable()
            // Re-acquire focus once the node is actually placed: on a session switch the swapped-in
            // Canvas is not yet placed when composition runs, so a single-shot requestFocus is a
            // silently-dropped no-op. Driving it from placement makes the request survive the swap.
            .onPlaced { focusRequester.requestFocus() }
            // Key on the scene (not Unit): on hot reload the scene instance is replaced, and a
            // Unit-keyed pointerInput would keep dispatching to the old, now-closed scene — leaving
            // the freshly reloaded UI unresponsive to clicks.
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
                            // The plugin scene can be closed mid-dispatch (navigation, session
                            // switch, hot reload). A late event then hits the closed scene and
                            // throws "... after ComposeScene is closed"; that is a benign teardown
                            // race, so ignore it instead of surfacing it as a plugin error.
                            if (!e.isComposeSceneClosed()) catchThrowHost[uuid()] = e
                        } catch (e: Throwable) {
                            catchThrowHost[uuid()] = e
                        }
                    } while (true)
                }
            }
            .onKeyEvent {
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
        if (pluginComposeScene.failure.value != null) return@Canvas
        this.drawIntoCanvas { canvas ->
            try {
                pluginComposeScene.render(canvas)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Rethrowing would take the host window down with the plugin. render() has recorded
                // the exception in the scene's failure, which the effect above reports.
            }
        }
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
