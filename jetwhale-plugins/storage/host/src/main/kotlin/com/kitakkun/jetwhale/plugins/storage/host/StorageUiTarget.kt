package com.kitakkun.jetwhale.plugins.storage.host

/** A part of the Storage Inspector that an MCP command operates, ringed while the command runs. */
internal sealed interface StorageUiTarget {
    data object FileTree : StorageUiTarget

    /** A file or directory: its row in the tree and, while it is selected, the pane beside the tree. */
    data class FileEntry(val location: FileLocation) : StorageUiTarget

    data object StoreList : StorageUiTarget

    /** A key-value store: its row in the store list and, while it is selected, its entries. */
    data class KeyValueStore(val storeName: String) : StorageUiTarget
}
