package com.kitakkun.jetwhale.host.data.server.negotiation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.util.logging.Logger
import kotlin.coroutines.cancellation.CancellationException

@Inject
@ContributesBinding(AppScope::class)
class DefaultServerSessionNegotiationStrategy(
    private val protocolNegotiationStrategy: ProtocolNegotiationStrategy,
    private val sessionNegotiationStrategy: SessionNegotiationStrategy,
    private val pluginNegotiationStrategy: PluginNegotiationStrategy,
) : ServerSessionNegotiationStrategy {
    context(logger: Logger)
    override suspend fun DefaultWebSocketServerSession.negotiate(): ServerSessionNegotiationResult {
        try {
            with(protocolNegotiationStrategy) { negotiate() }

            val session = with(sessionNegotiationStrategy) { negotiate() }

            val plugin = with(pluginNegotiationStrategy) { negotiate() }

            return ServerSessionNegotiationResult.Success(
                session = session,
                plugin = plugin,
            )
        } catch (e: CancellationException) {
            // Never swallow cancellation: re-throw so the coroutine cancellation mechanism keeps working.
            throw e
        } catch (e: Throwable) {
            return ServerSessionNegotiationResult.Failure(e)
        }
    }
}
