package com.kitakkun.jetwhale.host.sdk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Base class for a JetWhale host plugin. This base is **pure**: it has only a lifecycle and no
 * messaging — use it for plugins that don't talk to an agent (e.g. a host-only tool, declared with
 * `"requiresAgent": false` in the manifest).
 *
 * Add capabilities by combining types:
 * - [JetWhaleMessagingHostPlugin] (extend it instead) — to exchange messages with an agent counterpart.
 * - [JetWhaleHostPluginUi] (implement it) — to render a Compose UI.
 */
public abstract class JetWhaleHostPlugin {
    private var boundPluginScope: CoroutineScope? = null

    /**
     * Scope tied to this plugin instance's lifetime: available from [onCreate], cancelled by the
     * runtime when the instance is disposed. Launch background work here so it can never outlive
     * the instance.
     */
    protected val pluginScope: CoroutineScope
        get() = checkNotNull(boundPluginScope) {
            "pluginScope is only available after the plugin instance has been bound (in or after onCreate())."
        }

    private var boundStorage: JetWhalePluginStorage? = null

    /**
     * Persistent key-value storage scoped to this plugin's own pluginId, so a plugin can never name
     * or reach another plugin's data. Available from [onCreate]; bound by the runtime right after
     * the instance is created.
     */
    protected val storage: JetWhalePluginStorage
        get() = checkNotNull(boundStorage) {
            "storage is only available after the plugin instance has been bound (in or after onCreate())."
        }

    /**
     * The schema version of this plugin's persistent [storage]. Bump it together with an
     * [onStorageMigrate] implementation whenever the shape of the stored data changes.
     *
     * The runtime persists the version alongside the data and, on the first storage access after an
     * update, calls [onStorageMigrate] with the previously stored version before any other
     * operation goes through.
     */
    protected open val storageVersion: Int
        get() = 1

    /**
     * Migrates the persistent [storage] from [fromVersion] to the current [storageVersion].
     *
     * Called at most once per instance, before the first regular storage operation completes, so
     * reads never observe pre-migration data. Use [storage] directly to reshape the stored values
     * (rename keys, convert value shapes, drop obsolete entries).
     *
     * @param fromVersion The version the stored data was written with.
     */
    protected open suspend fun onStorageMigrate(fromVersion: Int) {}

    /** Called once when this plugin instance is created, before it is shown or used. */
    protected open fun onCreate() {}

    /** Called when this plugin instance is disposed (session closed, plugin disabled, or reloaded). */
    public open fun onDispose() {}

    // -- runtime hooks (not for plugin authors) -------------------------------

    /** Binds the instance-scoped coroutine scope. Called once, before [onCreate]. */
    @InternalJetWhaleHostApi
    public fun bindPluginScope(scope: CoroutineScope) {
        boundPluginScope = scope
    }

    /** Binds the pluginId-scoped persistent storage. Called once, before [onCreate]. */
    @InternalJetWhaleHostApi
    public fun bindStorage(storage: JetWhalePluginStorage) {
        boundStorage = MigratingPluginStorage(storage)
    }

    /**
     * The migration-gated storage bound to this instance, for runtime consumers such as the Compose
     * scene host (`LocalJetWhalePluginStorage`). Plugin code uses [storage] instead.
     */
    @InternalJetWhaleHostApi
    public fun boundStorageForRuntime(): JetWhalePluginStorage = storage

    @InternalJetWhaleHostApi
    public fun dispatchCreate() {
        onCreate()
    }

    @InternalJetWhaleHostApi
    public fun dispatchDispose() {
        onDispose()
    }

    /**
     * Gates every storage operation on a one-time schema migration: the first operation compares
     * the persisted version against [storageVersion], runs [onStorageMigrate] when the data is
     * older, and stamps the current version. The version lives in the same store under
     * [VERSION_KEY], which is hidden from the plugin-facing API.
     */
    private inner class MigratingPluginStorage(
        private val raw: JetWhalePluginStorage,
    ) : JetWhalePluginStorage {
        private val migrationMutex = Mutex()

        @Volatile
        private var migrated = false

        private suspend fun ensureMigrated() {
            if (migrated) return
            migrationMutex.withLock {
                if (migrated) return
                val storedVersion = raw.get(VERSION_KEY, Int.serializer())
                    // A store with data but no version predates storage versioning: treat it as v1.
                    // A fresh store has nothing to migrate and just gets stamped.
                    ?: if (raw.keysFlow.first().isEmpty()) storageVersion else 1
                if (storedVersion < storageVersion) {
                    // The migration body accesses this.storage; point it at the raw store for the
                    // duration so the hook does not re-enter this gate.
                    boundStorage = raw
                    try {
                        onStorageMigrate(storedVersion)
                    } finally {
                        boundStorage = this
                    }
                }
                // A stored version newer than storageVersion (downgraded plugin) is left untouched:
                // unreadable values surface as absent instead of destroying the newer data.
                raw.put(VERSION_KEY, maxOf(storedVersion, storageVersion), Int.serializer())
                migrated = true
            }
        }

        override suspend fun <T> put(key: String, value: T, serializer: KSerializer<T>) {
            ensureMigrated()
            require(key != VERSION_KEY) { "'$VERSION_KEY' is reserved by the JetWhale runtime." }
            raw.put(key, value, serializer)
        }

        override suspend fun <T> get(key: String, serializer: KSerializer<T>): T? {
            ensureMigrated()
            if (key == VERSION_KEY) return null
            return raw.get(key, serializer)
        }

        override fun <T> getFlow(key: String, serializer: KSerializer<T>): Flow<T?> = flow {
            ensureMigrated()
            if (key == VERSION_KEY) {
                emit(null)
                return@flow
            }
            emitAll(raw.getFlow(key, serializer))
        }

        override suspend fun contains(key: String): Boolean {
            ensureMigrated()
            if (key == VERSION_KEY) return false
            return raw.contains(key)
        }

        override suspend fun remove(key: String) {
            ensureMigrated()
            if (key == VERSION_KEY) return
            raw.remove(key)
        }

        override suspend fun clear() {
            ensureMigrated()
            raw.clear()
            // clear() wipes the version stamp with the data; restore it so the store is not treated
            // as pre-versioning legacy data on the next launch.
            raw.put(VERSION_KEY, storageVersion, Int.serializer())
        }

        override val keysFlow: Flow<Set<String>> = flow {
            ensureMigrated()
            emitAll(raw.keysFlow.map { it - VERSION_KEY })
        }
    }

    private companion object {
        /** Reserved key holding the persisted [storageVersion]; never visible to plugin code. */
        const val VERSION_KEY: String = "__jetwhale_storage_version"
    }
}

/**
 * Marks host-runtime-only entry points that plugin authors must not call. The host wires these when
 * it creates a plugin instance.
 */
@RequiresOptIn(message = "This is a JetWhale host-runtime API and must not be called by plugin code.")
@Retention(AnnotationRetention.BINARY)
public annotation class InternalJetWhaleHostApi
