package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.AiOperatedUiTargetsReporter
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

/** The UI targets the command marks while it runs with [arguments]. */
@OptIn(ExperimentalJetWhaleApi::class, InternalJetWhaleHostApi::class)
internal fun JetWhaleMcpCommand.markedTargets(arguments: JsonObject): Set<Any> {
    val marked = mutableSetOf<Any>()
    val reporter = object : AiOperatedUiTargetsReporter() {
        override fun reportOperationStarted(targets: Set<Any>) {
            marked.addAll(targets)
        }

        override fun reportOperationEnded(targets: Set<Any>) = Unit
    }
    runBlocking(reporter) { execute(JetWhaleMcpArguments(arguments)) }
    return marked
}
