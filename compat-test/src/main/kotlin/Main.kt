import com.kitakkun.jetwhale.agent.runtime.startJetWhale

// A minimal consumer of the public agent API. Compiling this file proves the artifacts'
// Kotlin metadata is readable; running it smoke-tests the runtime (no host needs to be up —
// the agent just fails to connect and keeps retrying in the background).
fun main() {
    startJetWhale {
        connection {
            host = "localhost"
            port = 5080
        }
    }
    Thread.sleep(3000)
    println("RUNTIME_OK")
}
