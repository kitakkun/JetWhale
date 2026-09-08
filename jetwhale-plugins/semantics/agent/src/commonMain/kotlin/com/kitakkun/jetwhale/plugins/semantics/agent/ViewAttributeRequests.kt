package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.GetViewAttributes
import com.kitakkun.jetwhale.plugins.semantics.protocol.SetViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResponse
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResult
import kotlinx.coroutines.CancellationException

// Answers the two attribute requests, kept apart from the capture and action handlers because the
// capability is optional: a root has attributes only when its source implements ViewAttributeSource.

/** Reads every attribute of one node, or says why it has none. */
internal suspend fun readViewAttributes(request: GetViewAttributes): ViewAttributeResponse {
    val source = sourceOf(request.rootId)
        ?: return ViewAttributeResponse(snapshot = null, message = unknownRoot(request.rootId))
    val attributeSource = source as? ViewAttributeSource
        ?: return ViewAttributeResponse(snapshot = null, message = ROOT_WITHOUT_ATTRIBUTES)
    return try {
        attributeSource.attributes(request.nodeId)
            ?.let { ViewAttributeResponse(snapshot = it) }
            ?: ViewAttributeResponse(snapshot = null, message = noViewAttributesMessage(request.nodeId))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        ViewAttributeResponse(snapshot = null, message = "reading the attributes failed: ${e.describeFailure()}")
    }
}

/** Writes one attribute of one node, reporting a refusal rather than failing the request. */
internal suspend fun writeViewAttribute(request: SetViewAttribute): ViewAttributeResult {
    val source = sourceOf(request.rootId)
        ?: return ViewAttributeResult(applied = false, message = unknownRoot(request.rootId))
    val attributeSource = source as? ViewAttributeSource
        ?: return ViewAttributeResult(applied = false, message = ROOT_WITHOUT_ATTRIBUTES)
    return try {
        attributeSource.setAttribute(nodeId = request.nodeId, attributeId = request.attributeId, value = request.value)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        ViewAttributeResult(applied = false, message = "writing the attribute failed: ${e.describeFailure()}")
    }
}

private fun sourceOf(rootId: String): ComposeNodeSource? = ComposeNodeSourceRegistry.sources.firstOrNull { it.sourceId == rootId }

private const val ROOT_WITHOUT_ATTRIBUTES: String = "this root has no View attributes (it is a composition read through its SemanticsOwner)"

private fun unknownRoot(rootId: String): String = "unknown rootId: $rootId (the root may have been detached; capture the tree again)"

private fun Throwable.describeFailure(): String = message?.takeIf { it.isNotBlank() } ?: (this::class.simpleName ?: "unknown error")
