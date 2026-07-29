package com.kitakkun.jetwhale.plugins.semantics.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide set of the Compose roots the agent can read.
 *
 * A process has exactly one composition hierarchy, and roots come and go on their own schedule —
 * a dialog opens, an activity is recreated — independently of when the debug host connects. So the
 * registry is a singleton the probe writes to, rather than state threaded through
 * [JetWhaleSemanticsAgentPlugin]: a root can be registered before the plugin is even activated,
 * and the plugin reads whatever is present at capture time.
 *
 * Registrations are **reference counted per [ComposeNodeSource.sourceId]**. The two ways of
 * installing a probe overlap by design — an app can install the Application-level one and still
 * drop the in-composition one into a screen — and either may be torn down first, so a root stays
 * registered until the last claim on it is closed.
 */
object ComposeNodeSourceRegistry {
    private val entries = MutableStateFlow<List<Entry>>(emptyList())

    /** The registered roots, oldest registration first. */
    val sources: List<ComposeNodeSource> get() = entries.value.map { it.source }

    /**
     * Claims [source]'s root and returns a handle releasing that claim. When a source with the same
     * [ComposeNodeSource.sourceId] is already registered, the existing one keeps serving captures
     * and this call only adds a claim on it.
     */
    fun register(source: ComposeNodeSource): Registration {
        entries.update { current ->
            val index = current.indexOfFirst { it.source.sourceId == source.sourceId }
            if (index < 0) {
                current + Entry(source, claims = 1)
            } else {
                current.toMutableList().apply { this[index] = this[index].withOneMoreClaim() }
            }
        }
        return Registration(source.sourceId)
    }

    /** Drops every registration. Intended for tests and for tearing down an install. */
    fun clear() {
        entries.value = emptyList()
    }

    private data class Entry(val source: ComposeNodeSource, val claims: Int) {
        fun withOneMoreClaim(): Entry = copy(claims = claims + 1)
    }

    /** One claim on a registered root; releasing the last one unregisters it. */
    class Registration internal constructor(private val sourceId: String) : AutoCloseable {
        // Closing twice must not release a claim someone else took out in the meantime.
        private val closed = MutableStateFlow(false)

        override fun close() {
            if (!closed.compareAndSet(expect = false, update = true)) return
            entries.update { current ->
                val index = current.indexOfFirst { it.source.sourceId == sourceId }
                when {
                    index < 0 -> current
                    current[index].claims <= 1 -> current.filterIndexed { i, _ -> i != index }
                    else -> current.toMutableList().apply { this[index] = this[index].copy(claims = this[index].claims - 1) }
                }
            }
        }
    }
}
