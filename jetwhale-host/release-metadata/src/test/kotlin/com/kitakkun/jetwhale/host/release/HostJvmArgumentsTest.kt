package com.kitakkun.jetwhale.host.release

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostJvmArgumentsTest {
    @Test
    fun `accepts the forms the launcher contract names`() {
        listOf(
            "-Dcompose.application.configure.swing.globals=true",
            "-Dapple.awt.application.appearance=system",
            "-Dflag",
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
            "--enable-native-access=ALL-UNNAMED",
            "-Xdock:name=JetWhale Debugger",
            "-Xmx4g",
            "-Xmx512M",
        ).forEach { assertTrue(isAllowedHostJvmArgument(it), it) }
    }

    @Test
    fun `refuses agents, hooks, argument files and class-path options`() {
        listOf(
            "-javaagent:agent.jar",
            "-agentlib:jdwp=transport=dt_socket",
            "-agentpath:/tmp/agent.so",
            "-XX:OnError=sh -c 'rm -rf ~'",
            "-XX:OnOutOfMemoryError=reboot",
            "@args.txt",
            "-cp",
            "-classpath",
            "--class-path=evil.jar",
            "-Xbootclasspath/a:evil.jar",
            "--add-opens",
            "--add-opens=",
            "-Xdock:icon=/tmp/icon.icns",
            "-Xmx",
            "-Xms1g",
            "-D",
            "-D=value",
            "",
        ).forEach { assertFalse(isAllowedHostJvmArgument(it), it) }
    }
}
