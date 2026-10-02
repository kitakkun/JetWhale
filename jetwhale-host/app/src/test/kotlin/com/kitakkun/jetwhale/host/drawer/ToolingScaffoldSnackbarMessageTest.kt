package com.kitakkun.jetwhale.host.drawer

import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ToolingScaffoldSnackbarMessageTest {
    @Test
    fun `a plugin that could not be enabled or disabled is announced with the reason`() = runBlocking {
        val failure = ToolingScaffoldScreenActionResult.SetPluginEnabledFailed(IOException("the settings file is read-only"))

        assertContains(failure.snackbarMessage(), "the settings file is read-only")
    }

    @Test
    fun `a failure without a message is announced by its kind`() = runBlocking {
        val failure = ToolingScaffoldScreenActionResult.SetPluginEnabledFailed(IOException())

        assertContains(failure.snackbarMessage(), "IOException")
    }

    @Test
    fun `a failure with a blank message or no class name still names a reason`() = runBlocking {
        val blank = ToolingScaffoldScreenActionResult.SetPluginEnabledFailed(IOException("  "))
        val anonymous = ToolingScaffoldScreenActionResult.SetPluginEnabledFailed(object : Throwable() {})

        assertContains(blank.snackbarMessage(), "IOException")
        assertFalse(anonymous.snackbarMessage().trimEnd().endsWith(":"))
    }
}
