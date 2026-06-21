package com.kitakkun.jetwhale.host.sdk

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessenger

/**
 * The messenger of the plugin instance the host is currently composing, provided while it renders a
 * [JetWhaleHostPluginUi.Content]. Read it inside `Content()` to talk to the agent without holding a
 * long-lived reference (the value is scoped to the composition, which the host disposes with the
 * session):
 *
 * ```kotlin
 * @Composable override fun Content() {
 *     val messenger = LocalJetWhaleMessenger.current
 *     Button(onClick = { messenger.coroutineScope.launch { request(Ping) } }) { Text("Ping") }
 * }
 * ```
 *
 * Provided only for messaging plugins; reading it from a host-only (no agent) plugin's UI throws.
 */
public val LocalJetWhaleMessenger: ProvidableCompositionLocal<JetWhaleMessenger> =
    staticCompositionLocalOf { error("LocalJetWhaleMessenger is only available inside a messaging plugin's Content().") }
