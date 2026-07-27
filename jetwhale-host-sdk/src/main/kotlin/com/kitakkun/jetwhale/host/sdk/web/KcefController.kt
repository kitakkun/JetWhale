package com.kitakkun.jetwhale.host.sdk.web

import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/** Progress of the one-time KCEF (Chromium) runtime bootstrap shared by every [JetWhaleWebView]. */
internal sealed interface KcefInitStatus {
    /** Downloading and/or initializing the browser runtime; [progress] is 0..100, or -1 when unknown. */
    data class Initializing(val progress: Float) : KcefInitStatus

    /** The runtime is ready; browsers can be created. */
    data object Ready : KcefInitStatus

    /** Initialization failed; [message] describes why. */
    data class Failed(val message: String) : KcefInitStatus

    /** All packages fetched but the host must restart to load them (rare). */
    data object RestartRequired : KcefInitStatus
}

/**
 * Owns the process-wide KCEF bootstrap. KCEF is a singleton, so it is initialized at most once and
 * lazily — the first time a web plugin is shown — keeping the heavy Chromium download off users who
 * never open one. [KCEF.init] is itself idempotent and thread-safe; the guard here just avoids
 * re-entering the download coroutine.
 */
internal object KcefController {
    private val _status = MutableStateFlow<KcefInitStatus>(KcefInitStatus.Initializing(-1f))
    val status: StateFlow<KcefInitStatus> = _status.asStateFlow()

    private val started = AtomicBoolean(false)

    /** Starts the bootstrap on first call; later calls return immediately. Observe [status] for progress. */
    suspend fun ensureInitialized() {
        if (!started.compareAndSet(false, true)) return
        withContext(Dispatchers.IO) {
            runCatching {
                KCEF.init(
                    builder = {
                        installDir(File(jetwhaleHome(), "kcef-bundle"))
                        progress {
                            onDownloading { percent -> _status.value = KcefInitStatus.Initializing(max(percent, 0f)) }
                            onInitialized { _status.value = KcefInitStatus.Ready }
                        }
                        settings {
                            cachePath = File(jetwhaleHome(), "kcef-cache").absolutePath
                        }
                    },
                    onError = { throwable ->
                        _status.value = KcefInitStatus.Failed(throwable?.message ?: throwable?.toString() ?: "unknown error")
                    },
                    onRestartRequired = {
                        _status.value = KcefInitStatus.RestartRequired
                    },
                )
            }.onFailure { throwable ->
                _status.value = KcefInitStatus.Failed(throwable.message ?: throwable.toString())
            }
        }
    }

    private fun jetwhaleHome(): File = File(System.getProperty("user.home"), ".jetwhale")
}
