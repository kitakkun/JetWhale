package com.kitakkun.jetwhale.plugins.actions.agent

import com.kitakkun.jetwhale.annotations.McpDescription
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionParameter
import com.kitakkun.jetwhale.plugins.actions.protocol.ParameterType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames

/**
 * The fields of an action's argument type, one per property of the class this descriptor
 * describes. An `object` — `Unit` included — has none. Properties named in [parametersWithOptions]
 * are marked as having app-supplied options.
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun SerialDescriptor.toActionParameters(parametersWithOptions: Set<String>): List<ActionParameter> {
    if (kind != StructureKind.CLASS) return emptyList()
    return (0 until elementsCount).map { index ->
        val element = getElementDescriptor(index)
        // A value class is transparent on the wire, so its field is entered as what it wraps.
        val encoded = if (element.isInline) element.getElementDescriptor(0) else element
        val name = getElementName(index)
        ActionParameter(
            name = name,
            type = encoded.kind.toParameterType(),
            optional = isElementOptional(index),
            nullable = element.isNullable,
            description = getElementAnnotations(index).filterIsInstance<McpDescription>().firstOrNull()?.value,
            enumValues = if (encoded.kind == SerialKind.ENUM) encoded.elementNames.toList() else emptyList(),
            hasOptions = name in parametersWithOptions,
        )
    }
}

private fun SerialKind.toParameterType(): ParameterType = when (this) {
    is PrimitiveKind.STRING, is PrimitiveKind.CHAR -> ParameterType.STRING
    is PrimitiveKind.BYTE, is PrimitiveKind.SHORT, is PrimitiveKind.INT, is PrimitiveKind.LONG -> ParameterType.INTEGER
    is PrimitiveKind.FLOAT, is PrimitiveKind.DOUBLE -> ParameterType.NUMBER
    is PrimitiveKind.BOOLEAN -> ParameterType.BOOLEAN
    is SerialKind.ENUM -> ParameterType.ENUM
    else -> ParameterType.JSON
}
