package com.kitakkun.jetwhale.tools.qaagent

import com.kitakkun.jetwhale.agent.runtime.JetWhaleSession
import io.ktor.client.HttpClient
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class RecordingSession : JetWhaleSession {
    private val stops = AtomicInteger()
    val stopCount: Int get() = stops.get()

    override fun stop() {
        stops.incrementAndGet()
    }
}

class QaAppTest {
    private fun qaApp(session: JetWhaleSession): QaApp = QaApp(
        name = "checkout",
        wirePluginsById = emptyMap(),
        httpClient = HttpClient(),
        session = session,
    )

    @Test
    fun `disconnect gives up the app's own session`() {
        val session = RecordingSession()
        val app = qaApp(session)

        assertTrue(app.disconnect())

        assertEquals(1, session.stopCount)
        assertFalse(app.isConnected)
    }

    @Test
    fun `disconnecting twice reports the second call as a no-op`() {
        val session = RecordingSession()
        val app = qaApp(session)

        app.disconnect()

        assertFalse(app.disconnect(), "the second disconnect had nothing left to give up")
        assertEquals(1, session.stopCount)
    }

    @Test
    fun `a disconnected app is never ready, so health stops counting it as drivable`() {
        val app = qaApp(RecordingSession())

        assertTrue(app.isReady, "an app with no plugins registered has nothing to wait for")

        app.disconnect()

        assertFalse(app.isReady)
    }
}
