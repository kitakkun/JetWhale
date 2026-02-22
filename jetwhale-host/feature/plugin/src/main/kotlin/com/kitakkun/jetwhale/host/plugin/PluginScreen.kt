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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.DpSize
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import soil.plant.compose.reacty.LocalCatchThrowHost
import soil.query.core.uuid

@OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
@Composable
fun PluginScreen(pluginComposeScene: PluginComposeScene) {
    val catchThrowHost = LocalCatchThrowHost.current
    var frameNanoTime by remember(pluginComposeScene) { mutableLongStateOf(0L) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(pluginComposeScene) {
        while (true) {
            withFrameNanos {
                frameNanoTime = it
            }
        }
    }

    LifecycleResumeEffect(Unit) {
        focusRequester.requestFocus()
        onPauseOrDispose {
            focusRequester.freeFocus()
        }
    }

    val density = LocalDensity.current

    Canvas(
        modifier = Modifier.fillMaxSize()
            .onSizeChanged {
                try {
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
                    dpSize = with(density) { DpSize(it.width.toDp(), it.height.toDp()) }
                )
            }
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    do {
                        val event = awaitPointerEvent()
                        try {
                            val scrollDelta = event.changes.map { it.scrollDelta }.reduce { acc, offset -> acc + offset }

                            pluginComposeScene.composeScene.sendPointerEvent(
                                eventType = event.type,
                                pointers = event.toComposeScenePointers(),
                                buttons = event.buttons,
                                keyboardModifiers = event.keyboardModifiers,
                                scrollDelta = scrollDelta,
                                nativeEvent = event.nativeEvent,
                                button = event.button,
                            )
                        } catch (e: Throwable) {
                            catchThrowHost[uuid()] = e
                        }
                    } while (true)
                }
            }
            .onKeyEvent {
                try {
                    pluginComposeScene.composeScene.sendKeyEvent(it)
                } catch (e: Throwable) {
                    catchThrowHost[uuid()] = e
                    false
                }
            },
    ) {
        this.drawIntoCanvas {
            pluginComposeScene.composeScene.render(it, frameNanoTime)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun PointerEvent.toComposeScenePointers(): List<ComposeScenePointer> {
    return this.changes.map { pointerInputChange ->
        ComposeScenePointer(
            id = pointerInputChange.id,
            position = pointerInputChange.position,
            pressed = pointerInputChange.pressed,
            type = pointerInputChange.type,
            pressure = pointerInputChange.pressure,
            historical = pointerInputChange.historical,
        )
    }
}
