package com.kitakkun.jetwhale.agent.runtime

/**
 * A stand-in for the real scope in `jetwhale-agent-runtime`.
 *
 * The compiler plugin identifies its target by fully qualified name, so a local declaration under
 * the same name is indistinguishable to it — and lets this sample compile with nothing but the
 * plugin on the classpath. Pulling the actual runtime in would drag a multiplatform build and its
 * own Kotlin version constraints into a build whose whole purpose is varying the Kotlin version.
 *
 * Only the members the rewrite touches are declared. If the real scope's `wss` signature ever
 * changes, this stub stops matching and the sample fails — which is the intended alarm.
 */
interface JetWhaleEndpointScope {
    fun ws(host: String, port: Int)
    fun wss(host: String, port: Int)
    fun buildMachineWss(port: Int)
}
