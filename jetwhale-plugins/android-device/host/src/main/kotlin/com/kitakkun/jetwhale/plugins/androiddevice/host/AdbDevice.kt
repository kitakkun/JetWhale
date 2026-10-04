package com.kitakkun.jetwhale.plugins.androiddevice.host

/**
 * One line of `adb devices -l`.
 *
 * @property state adb's own connection state: `device`, `offline`, `unauthorized`, ... Only `device`
 *   is usable.
 */
internal data class AdbDevice(
    val serial: String,
    val state: String,
    val model: String?,
    val product: String?,
    val transportId: String?,
) {
    val isEmulator: Boolean get() = serial.startsWith("emulator-")

    val isUsable: Boolean get() = state == USABLE_STATE

    companion object {
        /** The one state in which adb will accept commands for a device. */
        const val USABLE_STATE = "device"
    }
}

/**
 * Parses `adb devices -l`. The header line is skipped; a line whose second column is missing is not
 * a device entry and is ignored.
 */
internal fun parseAdbDevices(output: String): List<AdbDevice> = output.lineSequence()
    .map { it.trim() }
    .filter(String::isNotEmpty)
    .filterNot { it.startsWith("List of devices") }
    .mapNotNull { line ->
        val columns = line.split(Regex("\\s+"))
        if (columns.size < 2) return@mapNotNull null
        val properties = columns.drop(2)
            .mapNotNull { column ->
                val separator = column.indexOf(':')
                if (separator <= 0) null else column.substring(0, separator) to column.substring(separator + 1)
            }
            .toMap()
        AdbDevice(
            serial = columns[0],
            state = columns[1],
            model = properties["model"],
            product = properties["product"],
            transportId = properties["transport_id"],
        )
    }
    .toList()
