import com.kitakkun.jetwhale.agent.runtime.startJetWhale

fun main() {
    startJetWhale {
        connection {
            host = "localhost"
            port = 5080
        }
    }
    // startJetWhale returns at once and keeps retrying a host that is not there in the background;
    // the wait lets that code run a few rounds, so a runtime failure shows before the success
    // marker is printed.
    Thread.sleep(3000)
    println("RUNTIME_OK")
}
