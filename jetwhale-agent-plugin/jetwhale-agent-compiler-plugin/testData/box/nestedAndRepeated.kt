// Several calls, one of them inside a lambda. The transformer recurses before deciding, so a call
// nested in another expression must be rewritten too — and each occurrence independently.

// FILE: scope.kt
package com.kitakkun.jetwhale.agent.runtime

interface JetWhaleEndpointScope {
    fun ws(host: String, port: Int)
    fun wss(host: String, port: Int)
    fun buildMachineWss(port: Int)
}

// FILE: box.kt
import com.kitakkun.jetwhale.agent.runtime.JetWhaleEndpointScope

class Recording : JetWhaleEndpointScope {
    val dialled = mutableListOf<String>()
    override fun ws(host: String, port: Int) { dialled += "ws://$host:$port" }
    override fun wss(host: String, port: Int) { dialled += "wss://$host:$port" }
    override fun buildMachineWss(port: Int) { dialled += "unrewritten:$port" }
}

fun endpoints(scope: JetWhaleEndpointScope, configure: JetWhaleEndpointScope.() -> Unit) = scope.configure()

fun box(): String {
    val scope = Recording()
    scope.buildMachineWss(5443)
    endpoints(scope) {
        buildMachineWss(5444)
        listOf(5445).forEach { buildMachineWss(it) }
    }
    val expected = listOf(
        "wss://198.51.100.7:5443",
        "wss://198.51.100.7:5444",
        "wss://198.51.100.7:5445",
    )
    return if (scope.dialled == expected) "OK" else "FAIL: ${scope.dialled}"
}
