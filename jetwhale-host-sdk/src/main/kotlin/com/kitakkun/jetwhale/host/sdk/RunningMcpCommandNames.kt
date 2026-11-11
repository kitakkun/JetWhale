package com.kitakkun.jetwhale.host.sdk

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi

/**
 * The [JetWhaleMcpCommand.name]s of this plugin instance's commands that an AI agent is running now.
 *
 * The host provides it around each plugin instance's [JetWhaleHostPluginUi.Content], holding only
 * that instance's commands. A command is in the set while it runs and for 1.5 seconds after it
 * finishes, so a command that finishes within a frame still shows. Outside the host the set is
 * empty.
 *
 * To mark the control that does what a command does, use `Modifier.jwAiOperatingRingWhileRunning`
 * from jetwhale-host-ui, which reads this local itself and draws the ring the host marks AI
 * operations with. Read the local directly only for an indication of your own.
 */
@ExperimentalJetWhaleApi
public val LocalRunningMcpCommandNames: ProvidableCompositionLocal<Set<String>> = compositionLocalOf { emptySet() }
