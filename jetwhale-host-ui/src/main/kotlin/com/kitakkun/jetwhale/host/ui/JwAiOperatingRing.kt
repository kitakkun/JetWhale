package com.kitakkun.jetwhale.host.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawModifierNode
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.LocalRunningMcpCommandNames
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** How long the highlight takes to go once around the ring. */
private const val ROTATION_PERIOD_MILLIS = 2000

/**
 * Draws the ring the host marks AI operations with: a [JwColors.aiAccent] highlight that sweeps
 * continuously around [shape], [JwMetrics.focusStrokeWidth] wide, just inside the bounds and over
 * the content. The host rings a plugin's drawer item with it while an AI agent drives the plugin.
 *
 * The ring animates for as long as the modifier is applied, so apply it only while there is an
 * operation to mark. To ring a control inside a plugin while an agent runs one of the plugin's MCP
 * commands, use [jwAiOperatingRingWhileRunning], which decides that itself.
 *
 * @param shape the shape of what is ringed, so the ring hugs its corners.
 */
public fun Modifier.jwAiOperatingRing(shape: Shape): Modifier = this then AiOperatingRingElement(shape, AiOperatingRingVisibility.Always)

/**
 * Draws [jwAiOperatingRing] while an AI agent runs any of [commands], and nothing otherwise. Put it
 * on the control that does what the commands do, so the user sees what inside the plugin the agent
 * is driving. One control can stand for several commands, such as a screen that is both tapped and
 * swiped.
 *
 * It follows [LocalRunningMcpCommandNames], so a command that finishes within a frame still rings
 * the control for a moment. While none of [commands] runs, the modifier draws nothing and requests
 * no frames.
 *
 * @param commands the commands the control stands for, the same instances the plugin lists in
 * [com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin.mcpCommands].
 * @param shape the shape of the control, so the ring hugs its corners.
 */
@ExperimentalJetWhaleApi
public fun Modifier.jwAiOperatingRingWhileRunning(vararg commands: JetWhaleMcpCommand, shape: Shape): Modifier = this then AiOperatingRingElement(shape, AiOperatingRingVisibility.WhileAnyRuns(commands.mapTo(HashSet(), JetWhaleMcpCommand::name)))

private sealed interface AiOperatingRingVisibility {
    data object Always : AiOperatingRingVisibility

    data class WhileAnyRuns(val commandNames: Set<String>) : AiOperatingRingVisibility
}

private data class AiOperatingRingElement(
    val shape: Shape,
    val visibility: AiOperatingRingVisibility,
) : ModifierNodeElement<AiOperatingRingNode>() {
    override fun create(): AiOperatingRingNode = AiOperatingRingNode(shape, visibility)

    override fun update(node: AiOperatingRingNode) {
        node.update(shape, visibility)
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private class AiOperatingRingNode(
    private var shape: Shape,
    private var visibility: AiOperatingRingVisibility,
) : DelegatingNode(),
    CompositionLocalConsumerModifierNode,
    ObserverModifierNode {
    private val rotationDegrees = Animatable(0f)

    /** Turns the highlight while the ring is shown; null while it is not. */
    private var rotationJob: Job? = null

    private val ringDrawNode = delegate(
        CacheDrawModifierNode {
            var cachedRingPath: Path? = null
            onDrawWithContent {
                drawContent()
                if (rotationJob == null) return@onDrawWithContent
                val ringPath = cachedRingPath ?: ringPath(shape, JwMetrics.focusStrokeWidth.toPx()).also { cachedRingPath = it }
                val color = currentValueOf(LocalJwColors).aiAccent
                // Brush.sweepGradient takes no start angle, so a square that covers the node at any
                // angle turns under a clip that stays put.
                val coveringSquareSide = size.maxDimension * 2f
                clipPath(ringPath) {
                    rotate(rotationDegrees.value) {
                        drawRect(
                            brush = Brush.sweepGradient(listOf(color, Color.Transparent, color, Color.Transparent, color), center),
                            topLeft = Offset(center.x - coveringSquareSide / 2f, center.y - coveringSquareSide / 2f),
                            size = Size(coveringSquareSide, coveringSquareSide),
                        )
                    }
                }
            }
        },
    )

    fun update(shape: Shape, visibility: AiOperatingRingVisibility) {
        if (shape != this.shape) {
            this.shape = shape
            ringDrawNode.invalidateDrawCache()
        }
        this.visibility = visibility
        startOrStopRotation()
    }

    override fun onAttach() {
        startOrStopRotation()
    }

    override fun onDetach() {
        // No cancel: the node's coroutineScope is cancelled on detach. Forgetting the job lets the
        // next attach start the rotation again.
        rotationJob = null
    }

    override fun onObservedReadsChanged() {
        startOrStopRotation()
    }

    private fun startOrStopRotation() {
        if (!isAttached) return
        val shown = when (val visibility = visibility) {
            is AiOperatingRingVisibility.Always -> true

            is AiOperatingRingVisibility.WhileAnyRuns -> {
                var running = false
                observeReads { running = currentValueOf(LocalRunningMcpCommandNames).any(visibility.commandNames::contains) }
                running
            }
        }
        if (shown == (rotationJob != null)) return
        rotationJob?.cancel()
        rotationJob = if (shown) {
            coroutineScope.launch {
                rotationDegrees.snapTo(0f)
                rotationDegrees.animateTo(360f, infiniteRepeatable(tween(ROTATION_PERIOD_MILLIS, easing = LinearEasing)))
            }
        } else {
            null
        }
        ringDrawNode.invalidateDraw()
    }
}

/** The band [ringWidth] wide just inside [shape]'s outline, to clip the ring to. */
private fun DrawScope.ringPath(shape: Shape, ringWidth: Float): Path {
    val outline = shape.createOutline(size, layoutDirection, this)
    return Path().apply {
        fillType = PathFillType.EvenOdd
        addOutline(outline)
        if (outline is Outline.Rounded) {
            addRoundRect(outline.roundRect.deflate(ringWidth))
        } else {
            val innerSize = Size((size.width - ringWidth * 2f).coerceAtLeast(0f), (size.height - ringWidth * 2f).coerceAtLeast(0f))
            addPath(Path().apply { addOutline(shape.createOutline(innerSize, layoutDirection, this@ringPath)) }, Offset(ringWidth, ringWidth))
        }
    }
}

/** Moved [delta] in from every side, with corners [delta] tighter, so the band it leaves is as wide at the corners as along the sides. */
private fun RoundRect.deflate(delta: Float): RoundRect = RoundRect(
    left = left + delta,
    top = top + delta,
    right = right - delta,
    bottom = bottom - delta,
    topLeftCornerRadius = topLeftCornerRadius.shrink(delta),
    topRightCornerRadius = topRightCornerRadius.shrink(delta),
    bottomRightCornerRadius = bottomRightCornerRadius.shrink(delta),
    bottomLeftCornerRadius = bottomLeftCornerRadius.shrink(delta),
)

private fun CornerRadius.shrink(delta: Float): CornerRadius = CornerRadius((x - delta).coerceAtLeast(0f), (y - delta).coerceAtLeast(0f))
