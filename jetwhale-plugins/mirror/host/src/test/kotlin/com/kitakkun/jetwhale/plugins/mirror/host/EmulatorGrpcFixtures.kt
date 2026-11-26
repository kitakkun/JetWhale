package com.kitakkun.jetwhale.plugins.mirror.host

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer

/** A unary gRPC answer carrying [message]. */
internal fun grpcReply(message: ByteArray): MockResponse = MockResponse()
    .setHeader("content-type", "application/grpc")
    .setHeader("grpc-status", "0")
    .setBody(Buffer().write(grpcMessage(message)))

/** The messages of a gRPC request body, without their five-byte prefixes. */
internal fun grpcMessagesOf(request: RecordedRequest): List<ByteArray> {
    val body = request.body
    return buildList {
        while (!body.exhausted()) {
            body.readByte()
            add(body.readByteArray(body.readInt().toLong()))
        }
    }
}
