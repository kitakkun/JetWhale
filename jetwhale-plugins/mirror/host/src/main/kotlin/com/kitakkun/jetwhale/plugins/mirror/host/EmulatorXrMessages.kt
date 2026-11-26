package com.kitakkun.jetwhale.plugins.mirror.host

import okio.Buffer
import java.io.ByteArrayInputStream

/**
 * The emulator's `XrOptions`: the virtual [environment] around the scene, by its enum number; how much
 * of the room shows through it instead, from 0 to 1 ([passthroughCoefficient]); and [dimmingValue].
 */
internal data class XrOptions(
    val environment: Long,
    val passthroughCoefficient: Float,
    val dimmingValue: Float,
)

/**
 * An `InputEvent` carrying an `xr_hand_event`: the hand points at pixel ([x], [y]) of the emulator's
 * framebuffer, pinching while [pinching], as the primary mouse button does on the emulator's window.
 */
internal fun encodeXrHandEvent(x: Int, y: Int, pinching: Boolean): ByteArray {
    val mouseEvent = Buffer().apply {
        writeVarintField(fieldNumber = 1, value = x.toLong())
        writeVarintField(fieldNumber = 2, value = y.toLong())
        writeVarintField(fieldNumber = 3, value = if (pinching) MOUSE_PRIMARY_BUTTON else 0L)
    }
    return Buffer().writeMessageField(fieldNumber = INPUT_EVENT_XR_HAND, message = mouseEvent).readByteArray()
}

/**
 * An `InputEvent` carrying an `xr_head_rotation_event`, which turns the head from where it faces
 * now: [upRadians] about the x axis, looking up when positive, and [leftRadians] about the y axis,
 * turning left when positive.
 */
internal fun encodeXrHeadRotation(upRadians: Float, leftRadians: Float): ByteArray {
    val rotation = Buffer().apply {
        writeFloatField(fieldNumber = 1, value = upRadians)
        writeFloatField(fieldNumber = 2, value = leftRadians)
    }
    return Buffer().writeMessageField(fieldNumber = INPUT_EVENT_XR_HEAD_ROTATION, message = rotation).readByteArray()
}

/** An `InputEvent` carrying the `xr_command` that puts the head back where it started, facing forward. */
internal fun encodeXrRecenter(): ByteArray {
    val command = Buffer().writeVarintField(fieldNumber = 1, value = XR_COMMAND_RECENTER)
    return Buffer().writeMessageField(fieldNumber = INPUT_EVENT_XR_COMMAND, message = command).readByteArray()
}

internal fun encodeXrOptions(options: XrOptions): ByteArray = Buffer().apply {
    writeVarintField(fieldNumber = 1, value = options.environment)
    writeFloatField(fieldNumber = 2, value = options.passthroughCoefficient)
    writeFloatField(fieldNumber = 4, value = options.dimmingValue)
}.readByteArray()

/** The `XrOptions` in [message]; a field it leaves out has its zero value, as protobuf has it. */
internal fun parseXrOptions(message: ByteArray): XrOptions = BoundedReader(ByteArrayInputStream(message), message.size).use { reader ->
    var options = XrOptions(environment = 0, passthroughCoefficient = 0f, dimmingValue = 0f)
    while (reader.hasMore()) {
        options = when (val key = reader.readVarint()) {
            fieldKey(1, WIRE_VARINT) -> options.copy(environment = reader.readVarint())
            fieldKey(2, WIRE_FIXED32) -> options.copy(passthroughCoefficient = reader.readFloat())
            fieldKey(4, WIRE_FIXED32) -> options.copy(dimmingValue = reader.readFloat())
            else -> options.also { reader.skipField(key) }
        }
    }
    options
}

private const val INPUT_EVENT_XR_HAND = 7
private const val INPUT_EVENT_XR_COMMAND = 9
private const val INPUT_EVENT_XR_HEAD_ROTATION = 10

private const val MOUSE_PRIMARY_BUTTON = 1L

private const val XR_COMMAND_RECENTER = 0L
