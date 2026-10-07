package com.kitakkun.jetwhale.host.model

interface AdbAutoPortMappingService {
    fun startPortMapping(port: Int)

    /**
     * Removes the reverse mapping of [port] from every device. Removing the last port also ends
     * device tracking, and then returns only once the `adb` process that tracked devices has exited.
     */
    suspend fun stopPortMapping(port: Int)
}
