package com.kitakkun.jetwhale.host.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.kitakkun.jetwhale.host.di.JetWhaleAppGraph

// This builds its entries from the whole dependency graph it takes as a context parameter, which a
// `@Preview` has no way to build.
@Suppress("KOTRAIL_COMPOSABLE_WITHOUT_PREVIEW")
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
context(appGraph: JetWhaleAppGraph)
fun JetWhaleNavDisplay(
    backStack: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
) {
    val listDetailSceneStrategy = rememberListDetailSceneStrategy<NavKey>()
    val dialogSceneStrategy = remember { StableDialogSceneStrategy<NavKey>() }
    val windowSceneStrategy = remember(backStack) {
        WindowSceneStrategy<NavKey> { contentKey ->
            // A NavEntry's contentKey defaults to its key's toString(), so the closed window's
            // entry is found by that string.
            backStack.removeAll { it.toString() == contentKey.toString() }
        }
    }

    NavDisplay<NavKey>(
        backStack = backStack,
        onBack = backStack::popMainWindow,
        sceneStrategies = listOf(dialogSceneStrategy, windowSceneStrategy, listDetailSceneStrategy),
        transitionSpec = {
            ContentTransform(
                fadeIn(animationSpec = tween(100)),
                fadeOut(animationSpec = tween(100)),
            )
        },
        popTransitionSpec = {
            ContentTransform(
                fadeIn(animationSpec = tween(100)),
                fadeOut(animationSpec = tween(100)),
            )
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberRetainedNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            infoEntry(onClickOSSLicenses = { backStack.addSingleTop(LicensesNavKey) })
            emptyPluginEntry()
            settingsEntry(
                onClickClose = { backStack.removeIf { it is SettingsNavKey } },
                onOpenLogViewer = { backStack.addSingleTop(LogViewerNavKey) },
            )
            licensesEntry(onClickBack = backStack::popMainWindow)
            logViewerEntry()
            mcpToolsEntry()
            pluginEntries(
                isPoppedOut = backStack::isPluginPoppedOut,
                onBringBackToMainWindow = backStack::bringPluginBackToMainWindow,
            )
            disabledPluginEntry(onEnabled = backStack::openEnabledPlugin)
        },
        modifier = modifier.fillMaxSize(),
    )
    MainWindowBackHandler(backStack)
}

/**
 * Goes back one step in the main window per back event. NavDisplay's own handler calls `onBack`
 * once for every entry its main scene does not show, the windows of their own included, so with a
 * window open it would remove more than the top entry. This handler is registered after it, and a
 * dispatcher asks the newest handler first; a dialog's handler, newer still, keeps its own back.
 */
// It only registers a back handler and draws nothing, so a preview would show an empty frame.
@Suppress("KOTRAIL_COMPOSABLE_WITHOUT_PREVIEW")
@Composable
internal fun MainWindowBackHandler(backStack: NavBackStack<NavKey>) {
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = backStack.canPopMainWindow(),
        onBackCompleted = backStack::popMainWindow,
    )
}
