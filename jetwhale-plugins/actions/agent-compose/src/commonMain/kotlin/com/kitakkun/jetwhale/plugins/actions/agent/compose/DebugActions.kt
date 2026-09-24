package com.kitakkun.jetwhale.plugins.actions.agent.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.kitakkun.jetwhale.plugins.actions.agent.DebugActionsBuilder
import com.kitakkun.jetwhale.plugins.actions.agent.JetWhaleDebugActionsAgentPlugin

/**
 * Offers the actions [content] declares while this composable is in composition — the actions of
 * a screen exist only while the screen is shown, and the host sees them come and go.
 *
 * ```kotlin
 * @Composable
 * fun CheckoutScreen(form: CheckoutFormState) {
 *     actionsPlugin.DebugActions(form) {
 *         action("Fill test card") { run { form.fill(TestCards.visa) } }
 *     }
 *     ...
 * }
 * ```
 *
 * The actions are declared when this enters composition and again whenever one of [keys] changes,
 * the way `LaunchedEffect` restarts. Pass as keys whatever the actions capture that can be
 * replaced, or read changing values through a `State` inside `run`.
 */
@Composable
fun JetWhaleDebugActionsAgentPlugin.DebugActions(vararg keys: Any?, content: DebugActionsBuilder.() -> Unit) {
    DisposableEffect(this, *keys) {
        val registration = registerScoped(content)
        onDispose(registration::unregister)
    }
}
