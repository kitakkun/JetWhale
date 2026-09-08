package com.kitakkun.jetwhale.plugins.semantics.host

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.ui.JwDropdownButton
import com.kitakkun.jetwhale.host.ui.JwMenuItem
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwShapes
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwStatusLine
import com.kitakkun.jetwhale.host.ui.JwSwitch
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.semantics.protocol.GetViewAttributes
import com.kitakkun.jetwhale.plugins.semantics.protocol.SetViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResponse
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeValue
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.launch

/**
 * The platform attributes of the selected Android `View` node, and an editor for the ones that can
 * be written.
 *
 * Attributes are fetched per node rather than carried by the capture — a tree of two hundred nodes
 * would otherwise haul thirty attributes each — so this loads whenever the selection changes.
 */
@OptIn(ExperimentalJetWhaleApi::class)
@Composable
internal fun ViewAttributesPanel(
    rootId: String,
    nodeId: Int,
    loadAttributes: suspend (GetViewAttributes) -> ViewAttributeResponse,
    writeAttribute: suspend (SetViewAttribute) -> ViewAttributeResult,
) {
    var attributes by remember(rootId, nodeId) { mutableStateOf<List<ViewAttribute>?>(null) }
    var message by remember(rootId, nodeId) { mutableStateOf<String?>(null) }
    var writeStatus by remember(rootId, nodeId) { mutableStateOf<String?>(null) }
    var writeFailed by remember(rootId, nodeId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(rootId, nodeId) {
        try {
            val response = loadAttributes(GetViewAttributes(rootId = rootId, nodeId = nodeId))
            attributes = response.snapshot?.attributes
            message = response.message
        } catch (e: JetWhaleMessagingException) {
            attributes = null
            message = "The app did not answer: ${e.message}"
        }
    }

    fun commit(attribute: ViewAttribute, text: String) {
        scope.launch {
            val value = try {
                parseViewAttributeValue(attribute.id, attribute.value, text)
            } catch (e: JetWhaleMcpArgumentException) {
                writeStatus = e.message
                writeFailed = true
                return@launch
            }
            try {
                val result = writeAttribute(SetViewAttribute(rootId = rootId, nodeId = nodeId, attributeId = attribute.id, value = value))
                // The row shows what came back, not what was asked for: an app may clamp a value or
                // ignore it, and the difference is exactly what the panel is for.
                result.attribute?.let { written ->
                    attributes = attributes?.map { if (it.id == written.id) written else it }
                }
                writeFailed = !result.applied
                writeStatus = when {
                    result.applied -> "${attribute.id}: ${result.attribute?.value?.asText() ?: "written"}"
                    else -> "${attribute.id}: ${result.message ?: "not applied"}"
                }
            } catch (e: JetWhaleMessagingException) {
                writeStatus = "${attribute.id} failed: ${e.message}"
                writeFailed = true
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
        JwSectionHeader(title = "View attributes", contentPadding = PaddingValues(0.dp))
        JwText(
            text = "Edits are temporary: a relayout, a rebind, or the app writing the property itself takes the value back.",
            style = JwTheme.textStyles.labelSmall,
            color = JwTheme.colors.textSecondary,
        )

        val loaded = attributes
        when {
            loaded == null && message == null -> JwText(
                text = "Reading the view's attributes…",
                style = JwTheme.textStyles.bodySmall,
                color = JwTheme.colors.textSecondary,
            )

            loaded == null -> JwStatusLine(text = message.orEmpty(), tone = JwTone.Warning)

            else -> {
                for ((group, rows) in loaded.groupBy { it.group }) {
                    JwSectionHeader(title = group, contentPadding = PaddingValues(0.dp))
                    for (attribute in rows) {
                        AttributeRow(attribute = attribute, onCommit = { text -> commit(attribute, text) })
                    }
                }
            }
        }

        writeStatus?.let { JwStatusLine(text = it, tone = if (writeFailed) JwTone.Error else JwTone.Accent) }
    }
}

/** Fits "backgroundColor", the longest attribute label. */
private val AttributeLabelWidth = 120.dp

/** Wide enough for `#AARRGGBB` and for the enum names, without crowding the pane. */
private val AttributeEditorWidth = 180.dp

private val ColorSwatchSize = 16.dp

@Composable
private fun AttributeRow(attribute: ViewAttribute, onCommit: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        JwText(
            text = attribute.label,
            style = JwTheme.textStyles.bodySmall,
            color = JwTheme.colors.textSecondary,
            modifier = Modifier.width(AttributeLabelWidth),
        )
        val value = attribute.value
        when {
            !attribute.editable -> JwText(text = value.asText(), style = JwTheme.textStyles.code)

            value is ViewAttributeValue.BooleanValue -> JwSwitch(
                checked = value.value,
                onCheckedChange = { onCommit(it.toString()) },
                contentDescription = attribute.label,
            )

            value is ViewAttributeValue.EnumValue -> EnumEditor(value = value, onCommit = onCommit)

            value is ViewAttributeValue.ColorValue -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
            ) {
                Box(
                    Modifier
                        .size(ColorSwatchSize)
                        .background(Color(value.argb), JwShapes.extraSmall)
                        .border(1.dp, JwTheme.colors.border, JwShapes.extraSmall),
                )
                TextEditor(current = value.asText(), onCommit = onCommit)
            }

            else -> TextEditor(current = value.asText(), onCommit = onCommit)
        }
    }
}

@Composable
private fun EnumEditor(value: ViewAttributeValue.EnumValue, onCommit: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.width(AttributeEditorWidth)) {
        JwDropdownButton(
            text = value.value,
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            for (option in value.options) {
                JwMenuItem(
                    text = option,
                    selected = option == value.value,
                    onClick = {
                        expanded = false
                        onCommit(option)
                    },
                )
            }
        }
    }
}

/**
 * A field that commits on Enter or when it loses focus — never on every keystroke, which would send
 * a write to the app for each character typed.
 */
@Composable
private fun TextEditor(current: String, onCommit: (String) -> Unit) {
    var draft by remember(current) { mutableStateOf(current) }
    JwTextField(
        value = draft,
        onValueChange = { draft = it },
        textStyle = JwTheme.textStyles.code,
        modifier = Modifier
            .width(AttributeEditorWidth)
            .onFocusChanged { state ->
                if (!state.isFocused && draft != current) onCommit(draft)
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                    onCommit(draft)
                    true
                } else {
                    false
                }
            },
    )
}
