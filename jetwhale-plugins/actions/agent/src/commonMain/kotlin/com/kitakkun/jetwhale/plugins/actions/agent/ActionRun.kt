package com.kitakkun.jetwhale.plugins.actions.agent

import com.kitakkun.jetwhale.plugins.actions.protocol.ActionOutcome
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

/**
 * Decodes [arguments] and runs the action, turning every way it can end — returned, threw, took
 * too long — into an [ActionResult]. Cancellation of the calling coroutine is not one of them: it
 * propagates, and whoever cancelled reports it.
 */
internal suspend fun <A> DebugActionDefinition<A>.runWith(arguments: JsonObject, json: Json): ActionResult {
    val started = TimeSource.Monotonic.markNow()
    val decoded = try {
        json.decodeFromJsonElement(argumentSerializer, arguments)
    } catch (e: SerializationException) {
        return failedResult("the arguments do not fit this action: ${e.message}", stackTrace = null, durationMillis = 0)
    } catch (e: IllegalArgumentException) {
        return failedResult("the arguments do not fit this action: ${e.message}", stackTrace = null, durationMillis = 0)
    }
    return try {
        val value = withTimeout(timeout) {
            if (runsOnMainThread) withContext(Dispatchers.Main) { body(decoded) } else body(decoded)
        }
        val elapsed = started.elapsedNow().inWholeMilliseconds
        when (value) {
            null, Unit -> ActionResult(ActionOutcome.SUCCESS, text = null, json = null, error = null, stackTrace = null, durationMillis = elapsed)
            is JsonElement -> ActionResult(ActionOutcome.SUCCESS, text = null, json = value, error = null, stackTrace = null, durationMillis = elapsed)
            else -> ActionResult(ActionOutcome.SUCCESS, text = value.toString(), json = null, error = null, stackTrace = null, durationMillis = elapsed)
        }
    } catch (_: TimeoutCancellationException) {
        ActionResult(ActionOutcome.TIMEOUT, text = null, json = null, error = "the action did not finish within $timeout", stackTrace = null, durationMillis = started.elapsedNow().inWholeMilliseconds)
    } catch (e: Exception) {
        // A cancellation of this coroutine is not the action failing; let it reach the caller.
        if (e is CancellationException) throw e
        failedResult(e.message ?: e::class.simpleName ?: "the action failed", e.stackTraceToString(), started.elapsedNow().inWholeMilliseconds)
    }
}

private fun failedResult(error: String, stackTrace: String?, durationMillis: Long): ActionResult =
    ActionResult(ActionOutcome.FAILURE, text = null, json = null, error = error, stackTrace = stackTrace, durationMillis = durationMillis)
