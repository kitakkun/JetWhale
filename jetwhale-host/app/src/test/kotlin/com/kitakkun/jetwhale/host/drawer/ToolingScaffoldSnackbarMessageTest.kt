package com.kitakkun.jetwhale.host.drawer

import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContains

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
    fun `a failure with a blank message or no simple class name is announced by its class name`() = runBlocking {
        val blankError = IOException("  ")
        val anonymousError = object : Throwable() {}

        assertContains(ToolingScaffoldScreenActionResult.SetPluginEnabledFailed(blankError).snackbarMessage(), "IOException")
        assertContains(ToolingScaffoldScreenActionResult.SetPluginEnabledFailed(anonymousError).snackbarMessage(), anonymousError.javaClass.name)
    }
}
