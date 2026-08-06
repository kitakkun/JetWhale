package com.kitakkun.jetwhale.host

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
import com.kitakkun.jetwhale.host.navigation.NavEntryProvider
import com.kitakkun.jetwhale.host.navigation.StableDialogSceneStrategy
import com.kitakkun.jetwhale.host.navigation.WindowSceneStrategy
import com.kitakkun.jetwhale.host.navigation.rememberRetainedNavEntryDecorator
import com.kitakkun.jetwhale.host.shell.NavCommand
import com.kitakkun.jetwhale.host.shell.NavigationBus
import org.jetbrains.compose.resources.painterResource

/**
 * The navigation host: it renders whichever entry the back stack points at.
 *
 * Every screen contributes its own [NavEntryProvider] to the dependency graph, so this builds its
 * entry provider by iterating [entryProviders] rather than naming the screens itself. Closing a
 * window goes to the bus like any other navigation, keeping [NavigatorEffect] the only writer of
 * the back stack.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun JetWhaleNavDisplay(
    backStack: NavBackStack<NavKey>,
    entryProviders: Set<NavEntryProvider>,
    navigationBus: NavigationBus,
    modifier: Modifier = Modifier,
) {
    val listDetailSceneStrategy = rememberListDetailSceneStrategy<NavKey>()
    val dialogSceneStrategy = remember { StableDialogSceneStrategy<NavKey>() }
    val windowIcon = painterResource(Res.drawable.app_icon)
    val windowSceneStrategy = remember(windowIcon, navigationBus) {
        WindowSceneStrategy<NavKey>(windowIcon) { contentKey ->
            navigationBus.send(NavCommand.CloseWindow(contentKey))
        }
    }

    NavDisplay<NavKey>(
        backStack = backStack,
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
            entryProviders.forEach { provider ->
                context(this) {
                    provider.provideEntry()
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}
