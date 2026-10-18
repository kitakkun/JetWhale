package com.kitakkun.jetwhale.plugins.network.protocol

internal fun String.formUrlDecode(): String {
    if ('%' !in this && '+' !in this) return this
    val decoded = StringBuilder(length)
    val bytes = mutableListOf<Byte>()
    fun flushBytes() {
        if (bytes.isEmpty()) return
        decoded.append(bytes.toByteArray().decodeToString())
        bytes.clear()
    }
    var index = 0
    while (index < length) {
        val char = this[index]
        val escaped = if (char == '%' && index + 2 < length) substring(index + 1, index + 3).hexByteOrNull() else null
        if (escaped != null) {
            bytes.add(escaped.toByte())
            index += 3
        } else {
            flushBytes()
            decoded.append(if (char == '+') ' ' else char)
            index++
        }
    }
    flushBytes()
    return decoded.toString()
}

// toIntOrNull(radix = 16) accepts a leading sign, which would decode "%-1" as a byte.
private fun String.hexByteOrNull(): Int? = if (all(Char::isHexDigit)) toInt(radix = 16) else null

internal fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
