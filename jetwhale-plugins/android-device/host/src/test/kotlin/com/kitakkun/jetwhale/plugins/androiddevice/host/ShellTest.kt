package com.kitakkun.jetwhale.plugins.androiddevice.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShellQuotingTest {
    @Test
    fun `wraps a value so the device shell cannot act on it`() {
        assertEquals("'com.example.qa.sample'", singleQuoteForShell(TEST_PACKAGE))
        assertEquals("'a b; rm -rf /'", singleQuoteForShell("a b; rm -rf /"))
    }

    @Test
    fun `carries a single quote through by closing and reopening the quoting`() {
        assertEquals("'it'\\''s'", singleQuoteForShell("it's"))
    }
}

class InputTextEscapingTest {
    @Test
    fun `turns spaces into the escape input text splits on`() {
        assertEquals("hello%sworld", escapeForInputText("hello world"))
    }

    @Test
    fun `escapes the characters the device shell would otherwise act on`() {
        assertEquals("a\\&b", escapeForInputText("a&b"))
        assertEquals("a\\|b\\;c", escapeForInputText("a|b;c"))
        assertEquals("\\\$HOME", escapeForInputText("\$HOME"))
        assertEquals("it\\'s%s\\\"quoted\\\"", escapeForInputText("it's \"quoted\""))
    }

    @Test
    fun `escapes a backslash so it arrives as one`() {
        assertEquals("a\\\\b", escapeForInputText("a\\b"))
    }

    @Test
    fun `accepts every printable ASCII character`() {
        val printable = (' '..'~').joinToString("")

        assertTrue(unsupportedInputTextCharacters(printable).isEmpty())
    }

    @Test
    fun `names the characters input text cannot type`() {
        val unsupported = unsupportedInputTextCharacters("café\n")

        assertEquals(setOf('é', '\n'), unsupported.toSet())
    }
}
