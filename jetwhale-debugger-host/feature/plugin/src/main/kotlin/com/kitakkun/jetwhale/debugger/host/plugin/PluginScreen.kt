package com.kitakkun.jetwhale.debugger.host.plugin

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
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.lifecycle.compose.LifecycleResumeEffect
import soil.plant.compose.reacty.LocalCatchThrowHost
import soil.query.core.uuid

@OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
@Composable
fun PluginScreen(pluginComposeScene: ComposeScene) {
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

    Canvas(
        modifier = Modifier.fillMaxSize()
            .onSizeChanged { pluginComposeScene.size = it }
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    do {
                        val event = awaitPointerEvent()
                        try {
                            val scrollDelta = event.changes.map { it.scrollDelta }.reduce { acc, offset -> acc + offset }

                            pluginComposeScene.sendPointerEvent(
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
                    pluginComposeScene.sendKeyEvent(it)
                } catch (e: Throwable) {
                    catchThrowHost[uuid()] = e
                    false
                }
            },
    ) {
        this.drawIntoCanvas {
            pluginComposeScene.render(it, frameNanoTime)
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
