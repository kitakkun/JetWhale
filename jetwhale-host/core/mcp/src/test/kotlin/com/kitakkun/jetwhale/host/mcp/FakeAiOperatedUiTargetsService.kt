package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.AiOperatedUiTargetsService
import com.kitakkun.jetwhale.host.sdk.AiOperatedUiTargetsReporter
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Records every report as `"<pluginId>/<sessionId> started|ended <targets>"`, in order. */
@OptIn(InternalJetWhaleHostApi::class)
class FakeAiOperatedUiTargetsService : AiOperatedUiTargetsService {
    val reports: List<String>
        field = mutableListOf()

    override fun reporterFor(pluginId: String, sessionId: String): AiOperatedUiTargetsReporter = object : AiOperatedUiTargetsReporter() {
        override fun reportOperationStarted(targets: Set<Any>) {
            reports.add("$pluginId/$sessionId started $targets")
        }

        override fun reportOperationEnded(targets: Set<Any>) {
            reports.add("$pluginId/$sessionId ended $targets")
        }
    }

    override fun operatedUiTargetsFlow(pluginId: String, sessionId: String): Flow<Set<Any>> = emptyFlow()
}
