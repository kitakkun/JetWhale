package com.kitakkun.jetwhale.plugins.storage.agent

import java.io.File

// Not toRealPath: java.nio.file reaches Android at API 26, and this code runs down to API 23.
internal actual fun resolvedFile(file: File): File = file.canonicalFile
