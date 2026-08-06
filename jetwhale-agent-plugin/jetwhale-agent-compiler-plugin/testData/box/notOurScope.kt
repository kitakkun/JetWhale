// A same-named member on an unrelated type must be left alone. The plugin matches on the declaring
// interface, not the function name, and this is what proves the difference is load-bearing.

// FILE: other.kt
package someone.elses.library

interface JetWhaleEndpointScope {
    fun buildMachineWss(port: Int)
}

// FILE: box.kt
import someone.elses.library.JetWhaleEndpointScope

class Impostor : JetWhaleEndpointScope {
    var seen: Int = 0
    override fun buildMachineWss(port: Int) { seen = port }
}

fun box(): String {
    val impostor = Impostor()
    impostor.buildMachineWss(5443)
    return if (impostor.seen == 5443) "OK" else "FAIL: ${impostor.seen}"
}
