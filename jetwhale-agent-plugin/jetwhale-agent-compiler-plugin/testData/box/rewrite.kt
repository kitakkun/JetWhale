// The declaring interface's fully qualified name is the whole of what the plugin matches on, so
// declaring it here keeps the fixture self-contained — no runtime artifact on the test classpath.

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

fun box(): String {
    val scope = Recording()
    with(scope) {
        ws("localhost", 5080)
        buildMachineWss(5443)
    }
    val expected = listOf("ws://localhost:5080", "wss://198.51.100.7:5443")
    return if (scope.dialled == expected) "OK" else "FAIL: ${scope.dialled}"
}
