package com.kitakkun.jetwhale.host.release

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
            "-Xdock:name=JetWhale Debugger",
            "-Xdock:icon=/tmp/icon.icns",
            "-Xmx",
            "-Xms1g",
            "-D",
            "-D=value",
            "",
        ).forEach { assertFalse(isAllowedHostJvmArgument(it), it) }
    }

    @Test
    fun `reads the system property a -D argument sets`() {
        assertEquals("apple.awt.application.appearance" to "system", systemPropertyOf("-Dapple.awt.application.appearance=system"))
        assertEquals("key" to "a=b c", systemPropertyOf("-Dkey=a=b c"))
        assertEquals("flag" to "", systemPropertyOf("-Dflag"))
        listOf("--add-opens=java.desktop/sun.awt=ALL-UNNAMED", "-Xmx4g", "-D", "-D=value").forEach { assertNull(systemPropertyOf(it), it) }
    }
}
