package com.kitakkun.jetwhale.host.data.server.negotiation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.util.logging.Logger

@Inject
@ContributesBinding(AppScope::class)
class DefaultServerSessionNegotiationStrategy(
    private val protocolNegotiationStrategy: ProtocolNegotiationStrategy,
    private val sessionNegotiationStrategy: SessionNegotiationStrategy,
    private val capabilitiesNegotiationStrategy: CapabilitiesNegotiationStrategy,
    private val pluginNegotiationStrategy: PluginNegotiationStrategy,
) : ServerSessionNegotiationStrategy {
    context(logger: Logger)
    override suspend fun DefaultWebSocketServerSession.negotiate(): ServerSessionNegotiationResult {
        try {
            with(protocolNegotiationStrategy) { negotiate() }

            val session = with(sessionNegotiationStrategy) { negotiate() }

            with(capabilitiesNegotiationStrategy) { negotiate() }

            val plugin = with(pluginNegotiationStrategy) { negotiate() }

            return ServerSessionNegotiationResult.Success(
                session = session,
                plugin = plugin,
            )
        } catch (e: Throwable) {
            return ServerSessionNegotiationResult.Failure(e)
        }
    }
}
