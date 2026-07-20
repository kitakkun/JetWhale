package com.kitakkun.jetwhale.host.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

// A dialog OverlayScene whose identity is only the dialog entry's content key. NavDisplay tracks overlay
// scenes by object equality, and the built-in DialogScene also compares previousEntries/overlaidEntries.
// Those are the entries drawn *below* the overlay, so navigating the underlying back stack changes them
// and makes the scene unequal, which makes NavDisplay tear the dialog down and rebuild it in a fresh slot
// — a visible flash. Comparing only the content key keeps the dialog stable across unrelated navigation,
// mirroring [WindowOverlayScene].
internal class StableDialogScene<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val dialogProperties: DialogProperties,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable () -> Unit = {
        // NavDisplay provides a lifecycle owner around this overlay's content; re-provide it inside the
        // Dialog's sub-composition so the entry content keeps the same lifecycle.
        val lifecycleOwner = LocalLifecycleOwner.current
        Dialog(onDismissRequest = onBack, properties = dialogProperties) {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                entry.Content()
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StableDialogScene<*>) return false

        return key == other.key
    }

    override fun hashCode(): Int = key.hashCode()
}

/**
 * Displays entries that carry [dialog] metadata within a [Dialog], keeping the dialog stable while the
 * underlying back stack changes (unlike the built-in `DialogSceneStrategy`).
 */
class StableDialogSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull() ?: return null
        val dialogProperties = lastEntry.metadata[DIALOG_KEY] as? DialogProperties ?: return null

        return StableDialogScene(
            key = lastEntry.contentKey,
            entry = lastEntry,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            dialogProperties = dialogProperties,
            onBack = onBack,
        )
    }

    companion object {
        fun dialog(
            dialogProperties: DialogProperties = DialogProperties(),
        ): Map<String, Any> = mapOf(DIALOG_KEY to dialogProperties)

        const val DIALOG_KEY = "dialog"
    }
}
