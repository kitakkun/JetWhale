package com.kitakkun.jetwhale.protocol.messaging

/** Base type for messaging failures. */
public open class JetWhaleMessagingException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** A request failed: remote handler error, no handler registered, undecodable reply, or timeout. */
public class JetWhaleRequestException(
    message: String,
    cause: Throwable? = null,
) : JetWhaleMessagingException(message, cause)

/** The connection closed while a request was waiting for its reply. */
public class JetWhaleConnectionClosedException(
    message: String = "The connection was closed",
) : JetWhaleMessagingException(message)
