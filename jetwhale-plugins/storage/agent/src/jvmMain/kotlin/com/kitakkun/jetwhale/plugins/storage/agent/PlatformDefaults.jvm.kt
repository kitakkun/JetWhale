package com.kitakkun.jetwhale.plugins.storage.agent

actual fun FileRoot.Companion.platformDefaults(): List<FileRoot> = listOfNotNull(
    System.getProperty("user.dir")?.let { FileRoot(name = "Working directory", path = it) },
    System.getProperty("java.io.tmpdir")?.let { FileRoot(name = "Temporary", path = it) },
)

actual fun KeyValueStore.Companion.platformDefaults(): List<KeyValueStore> = emptyList()
