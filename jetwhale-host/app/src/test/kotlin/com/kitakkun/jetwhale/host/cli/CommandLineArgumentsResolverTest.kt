package com.kitakkun.jetwhale.host.cli

import com.kitakkun.jetwhale.host.model.McpPermissionOverride
import com.kitakkun.jetwhale.host.model.ServerPortOverrides
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandLineArgumentsResolverTest {
    @Test
    fun testPluginDirs() {
        val resolver = CommandLineArgumentsParser()
        val args = arrayOf(
            "--plugin-dir",
            "/path/to/plugin1",
            "--plugin-dir",
            "/path/to/plugin2",
        )
        val options = resolver.parse(args)

        assertContentEquals(
            listOf("/path/to/plugin1", "/path/to/plugin2"),
            options.pluginDirs,
        )
    }

    @Test
    fun testNoPluginDirs() {
        val resolver = CommandLineArgumentsParser()
        val args = arrayOf<String>()
        val options = resolver.parse(args)

        assertContentEquals(
            emptyList(),
            options.pluginDirs,
        )
    }

    @Test
    fun testUnknownArguments() {
        val resolver = CommandLineArgumentsParser()
        val args = arrayOf(
            "--unknown-arg",
            "some_value",
            "--plugin-dir",
            "/path/to/plugin",
        )
        val options = resolver.parse(args)

        assertContentEquals(
            listOf("/path/to/plugin"),
            options.pluginDirs,
        )
    }

    @Test
    fun testMissingPluginDirValue() {
        val resolver = CommandLineArgumentsParser()
        val args = arrayOf(
            "--plugin-dir",
        )

        assertFailsWith<IllegalStateException> {
            resolver.parse(args)
        }
    }

    @Test
    fun `port options override the persisted ports`() {
        val resolver = CommandLineArgumentsParser()
        val args = arrayOf(
            "--server-port",
            "5081",
            "--wss-port",
            "5444",
            "--mcp-server-port",
            "7081",
        )

        val options = resolver.parse(args)

        assertEquals(
            ServerPortOverrides(serverPort = 5081, wssPort = 5444, mcpServerPort = 7081),
            options.serverPortOverrides,
        )
    }

    @Test
    fun `ports left unspecified stay null so the persisted settings win`() {
        val resolver = CommandLineArgumentsParser()
        val args = arrayOf(
            "--server-port",
            "5081",
        )

        val options = resolver.parse(args)

        assertEquals(
            ServerPortOverrides(serverPort = 5081, wssPort = null, mcpServerPort = null),
            options.serverPortOverrides,
        )
    }

    @Test
    fun `MCP permissions are left to the stored settings unless the flag is passed`() {
        val options = CommandLineArgumentsParser().parse(arrayOf("--server-port", "5081"))

        assertEquals(McpPermissionOverride.None, options.mcpPermissionOverride)
    }

    @Test
    fun `the allow-all flag takes no value and lifts every MCP permission for the launch`() {
        // Automated QA has to reach tools a human would otherwise have to tick a checkbox for.
        val options = CommandLineArgumentsParser().parse(arrayOf("--mcp-allow-all-permissions"))

        assertEquals(McpPermissionOverride(allowAll = true), options.mcpPermissionOverride)
    }

    @Test
    fun `a launch is windowed unless the headless flag is passed`() {
        val options = CommandLineArgumentsParser().parse(arrayOf("--server-port", "5081"))

        assertFalse(options.headless)
    }

    @Test
    fun `the headless flag takes no value and runs the servers without a window`() {
        val options = CommandLineArgumentsParser().parse(arrayOf("--headless", "--server-port", "5081"))

        assertTrue(options.headless)
        assertEquals(5081, options.serverPortOverrides.serverPort)
    }

    @Test
    fun `a missing port value is rejected`() {
        val resolver = CommandLineArgumentsParser()

        assertFailsWith<IllegalStateException> {
            resolver.parse(arrayOf("--server-port"))
        }
    }

    @Test
    fun `a non-numeric port is rejected`() {
        val resolver = CommandLineArgumentsParser()

        assertFailsWith<IllegalStateException> {
            resolver.parse(arrayOf("--server-port", "not-a-port"))
        }
    }

    @Test
    fun `an out-of-range port is rejected`() {
        val resolver = CommandLineArgumentsParser()

        assertFailsWith<IllegalStateException> {
            resolver.parse(arrayOf("--mcp-server-port", "65536"))
        }
    }
}
