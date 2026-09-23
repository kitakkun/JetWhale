package com.kitakkun.jetwhale.host.architecture

import soil.query.core.DataModel
import soil.query.core.Reply

/**
 * A settled [DataModel] for the previews in this module: it always replies with [value], carries no
 * error, and is never awaited, so a preview renders the fulfilled branch without a query client.
 */
internal class PreviewDataModel<out T>(value: T) : DataModel<T> {
    override val reply: Reply<T> = Reply.some(value)
    override val replyUpdatedAt: Long = 0
    override val error: Throwable? = null
    override val errorUpdatedAt: Long = 0
    override fun isAwaited(): Boolean = false
}
