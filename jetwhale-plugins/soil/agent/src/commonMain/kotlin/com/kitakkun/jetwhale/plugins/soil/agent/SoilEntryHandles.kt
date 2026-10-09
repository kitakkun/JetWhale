package com.kitakkun.jetwhale.plugins.soil.agent

import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind

/**
 * How many handles of entries Soil dropped are remembered, so that an entry which comes back — a
 * screen reopened, a query refetched after its cache expired — keeps the handle the host knows it by.
 */
private const val RETIRED_HANDLE_LIMIT = 256

/**
 * Names entries for the host: one handle per kind and id, where ids are compared with `equals` as
 * Soil compares them. Not thread-safe.
 */
internal class SoilEntryHandles {
    private val handlesByKey = mutableMapOf<SoilEntryKey, String>()
    private val keysByHandle = mutableMapOf<String, SoilEntryKey>()
    private val retiredHandlesByKey = LinkedHashMap<SoilEntryKey, String>()
    private var nextNumber = 1L

    fun handleOf(key: SoilEntryKey): String = handlesByKey.getOrPut(key) {
        val handle = retiredHandlesByKey.remove(key) ?: "${key.kind.handlePrefix}-${nextNumber++}"
        keysByHandle[handle] = key
        handle
    }

    /** The entry [handle] names, or null when Soil no longer holds it. */
    fun keyOf(handle: String): SoilEntryKey? = keysByHandle[handle]

    /** Forgets [key]'s entry, keeping its handle in case the entry comes back. */
    fun retire(key: SoilEntryKey) {
        val handle = handlesByKey.remove(key) ?: return
        keysByHandle.remove(handle)
        retiredHandlesByKey[key] = handle
        if (retiredHandlesByKey.size > RETIRED_HANDLE_LIMIT) retiredHandlesByKey.remove(retiredHandlesByKey.keys.first())
    }
}

private val SoilEntryKind.handlePrefix: String
    get() = when (this) {
        SoilEntryKind.QUERY -> "query"
        SoilEntryKind.INFINITE_QUERY -> "infinite-query"
        SoilEntryKind.MUTATION -> "mutation"
        SoilEntryKind.SUBSCRIPTION -> "subscription"
    }
