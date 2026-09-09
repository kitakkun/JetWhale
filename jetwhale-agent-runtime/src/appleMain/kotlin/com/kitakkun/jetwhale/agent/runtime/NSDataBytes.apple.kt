@file:OptIn(ExperimentalForeignApi::class)

package com.kitakkun.jetwhale.agent.runtime

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSData

/** Copies the bytes out of an [NSData] so callers can hand them to common code. */
@OptIn(UnsafeNumber::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val source = bytes?.reinterpret<ByteVar>() ?: return ByteArray(0)
    return ByteArray(size) { source[it] }
}
