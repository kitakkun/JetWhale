package sample

import com.kitakkun.jetwhale.agent.runtime.JetWhaleEndpointScope

/** Records what a scope was asked to dial, so a test can assert on it. */
class RecordingScope : JetWhaleEndpointScope {
    val dialled: MutableList<String> = mutableListOf()

    override fun ws(host: String, port: Int) {
        dialled += "ws://$host:$port"
    }

    override fun wss(host: String, port: Int) {
        dialled += "wss://$host:$port"
    }

    // Reaching this means the call was not rewritten — the plugin did not run, or ran and declined.
    override fun buildMachineWss(port: Int) {
        dialled += "unrewritten:$port"
    }
}

/** The shape a consumer writes. Exercised by the test against whatever the plugin did to it. */
fun declareEndpoints(scope: JetWhaleEndpointScope) {
    with(scope) {
        ws("localhost", 5080)
        buildMachineWss(5443)
    }
}
