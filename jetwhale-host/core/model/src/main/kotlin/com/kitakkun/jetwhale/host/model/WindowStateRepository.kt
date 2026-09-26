package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.Flow

/**
 * Persisted main window geometry, expressed in dp values.
 *
 * A null [x]/[y] means no position was recorded and the window should fall back to a centered
 * placement on the next launch.
 */
data class PersistedWindowState(
    val width: Float,
    val height: Float,
    val x: Float?,
    val y: Float?,
)

interface WindowStateRepository {
    suspend fun loadWindowState(): PersistedWindowState?
    suspend fun saveWindowState(state: PersistedWindowState)

    /** The sidebar width the user last dragged to, in dp, or null when they never resized it. */
    val sidebarWidthFlow: Flow<Float?>

    suspend fun saveSidebarWidth(widthDp: Float)
}
