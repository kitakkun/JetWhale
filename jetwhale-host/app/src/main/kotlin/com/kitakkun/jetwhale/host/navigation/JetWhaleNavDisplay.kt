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
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.kitakkun.jetwhale.host.di.JetWhaleAppGraph

context(appGraph: JetWhaleAppGraph)
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun JetWhaleNavDisplay(
    backStack: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
) {
    val listDetailSceneStrategy = rememberListDetailSceneStrategy<NavKey>()
    val dialogSceneStrategy = remember { DialogSceneStrategy<NavKey>() }
    val windowSceneStrategy = remember(backStack) {
        WindowSceneStrategy<NavKey> { contentKey ->
            backStack.removeAll { it.toString() == contentKey.toString() }
        }
    }

    NavDisplay(
        backStack = backStack,
        sceneStrategy = dialogSceneStrategy then windowSceneStrategy then listDetailSceneStrategy,
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
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            infoEntry(onClickOSSLicenses = { backStack.addSingleTop(LicensesNavKey) })
            emptyPluginEntry()
            settingsEntry(onClickClose = backStack::removeLastOrNull)
            licensesEntry(onClickBack = backStack::removeLastOrNull)
            pluginEntries(
                isOpenedOnPopout = { pluginId, sessionId ->
                    backStack.any {
                        it is PluginPopoutNavKey &&
                            it.pluginId == pluginId &&
                            it.sessionId == sessionId
                    }
                },
            )
            disabledPluginEntry()
        },
        modifier = modifier.fillMaxSize()
    )
}
