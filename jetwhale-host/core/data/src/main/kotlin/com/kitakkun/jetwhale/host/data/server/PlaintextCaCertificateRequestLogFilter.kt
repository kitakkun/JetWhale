package com.kitakkun.jetwhale.host.data.server

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler
import io.netty.handler.ssl.NotSslRecordException

/**
 * Drops Netty's report of an agent asking for the CA certificate in plain HTTP on the wss port.
 *
 * An agent that trusts the host's certificate on first use asks `http://` on the port it is about to
 * use for wss before it asks `https://`, so every such connect sends one plaintext request to the TLS
 * server. Netty logs each as a failed TLS handshake, at WARN with a full stack trace. Every other
 * handshake failure still passes, other plaintext sent to that port included.
 */
class PlaintextCaCertificateRequestLogFilter : Filter<ILoggingEvent>() {
    override fun decide(event: ILoggingEvent): FilterReply {
        val throwableProxy = event.throwableProxy
        val isPlaintextCaCertificateRequest = event.loggerName == ApplicationProtocolNegotiationHandler::class.java.name &&
            throwableProxy?.className == NotSslRecordException::class.java.name &&
            // NotSslRecordException's message holds the bytes received, as a lowercase hex dump.
            throwableProxy.message.orEmpty().contains(PLAINTEXT_CA_CERTIFICATE_REQUEST_LINE_HEX)
        return if (isPlaintextCaCertificateRequest) FilterReply.DENY else FilterReply.NEUTRAL
    }
}

private val PLAINTEXT_CA_CERTIFICATE_REQUEST_LINE_HEX = "GET $CA_CERTIFICATE_URL_PATH ".encodeToByteArray().toHexString()
