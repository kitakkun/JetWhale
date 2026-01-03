package com.kitakkun.jetwhale.host.cli

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class CommandLineArgumentsResolverTest {
    @Test
    fun testPluginDirs() {
        val resolver = CommandLineArgumentsParser()
        val args = arrayOf(
            "--plugin-dir", "/path/to/plugin1",
            "--plugin-dir", "/path/to/plugin2",
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
            "--unknown-arg", "some_value",
            "--plugin-dir", "/path/to/plugin",
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
            "--plugin-dir"
        )

        assertFailsWith<IllegalStateException> {
            resolver.parse(args)
        }
    }
}
