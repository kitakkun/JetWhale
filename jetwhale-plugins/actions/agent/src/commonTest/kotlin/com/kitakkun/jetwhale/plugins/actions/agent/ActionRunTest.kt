package com.kitakkun.jetwhale.plugins.actions.agent

import com.kitakkun.jetwhale.plugins.actions.protocol.ActionOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ActionRunTest {
    private val json = Json

    @Test
    fun `decoded arguments reach the action and a string result comes back as text`() = runTest {
        val result = definition<Greeting> { "Hello ${it.name}" }.runWith(buildJsonObject { put("name", "Ada") }, json)

        assertEquals(ActionOutcome.SUCCESS, result.outcome)
        assertEquals("Hello Ada", result.text)
    }

    @Test
    fun `a JSON result comes back as JSON`() = runTest {
        val result = definition<Greeting> { JsonPrimitive(42) }.runWith(buildJsonObject { put("name", "Ada") }, json)

        assertEquals(JsonPrimitive(42), result.json)
        assertNull(result.text)
    }

    @Test
    fun `an action that returns nothing reports success without output`() = runTest {
        val result = definition<Greeting> { }.runWith(buildJsonObject { put("name", "Ada") }, json)

        assertEquals(ActionOutcome.SUCCESS, result.outcome)
        assertNull(result.text)
        assertNull(result.json)
    }

    @Test
    fun `arguments that do not decode are reported without running the action`() = runTest {
        var ran = false
        val result = definition<Greeting> { ran = true }.runWith(JsonObject(emptyMap()), json)

        assertEquals(ActionOutcome.FAILURE, result.outcome)
        assertTrue(result.error.orEmpty().startsWith("the arguments do not fit this action"))
        assertEquals(false, ran)
    }

    @Test
    fun `a throwing action is a failure with its message and stack trace`() = runTest {
        val result = definition<Greeting> { error("no network") }.runWith(buildJsonObject { put("name", "Ada") }, json)

        assertEquals(ActionOutcome.FAILURE, result.outcome)
        assertEquals("no network", result.error)
        assertNotNull(result.stackTrace)
    }

    @Test
    fun `an action that outlives its timeout is reported as timed out`() = runTest {
        val result = definition<Greeting> { delay(60.seconds) }.runWith(buildJsonObject { put("name", "Ada") }, json)

        assertEquals(ActionOutcome.TIMEOUT, result.outcome)
    }

    @Test
    fun `cancelling the run is not reported as a failure but propagates`() = runTest {
        val started = CompletableDeferred<Unit>()
        var escaped: Throwable? = null
        var returned: Any? = null
        val run = launch {
            try {
                returned = definition<Greeting> {
                    started.complete(Unit)
                    awaitCancellation()
                }.runWith(buildJsonObject { put("name", "Ada") }, json)
            } catch (e: CancellationException) {
                escaped = e
                throw e
            }
        }
        started.await()

        run.cancelAndJoin()

        assertNull(returned)
        assertTrue(escaped is CancellationException)
    }

    private inline fun <reified A> definition(noinline body: suspend (A) -> Any?): DebugActionDefinition<A> {
        val builder = DebugActionsBuilder(group = null)
        builder.action<A>("Test") {
            timeout = 5.seconds
            run(body)
        }
        @Suppress("UNCHECKED_CAST")
        return builder.definitions.single() as DebugActionDefinition<A>
    }
}

@Serializable
private data class Greeting(val name: String)
