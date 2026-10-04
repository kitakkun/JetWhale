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
        assertEquals("'hello%sworld'", escapeForInputText("hello world"))
    }

    @Test
    fun `single-quotes the argument so the device shell acts on none of it`() {
        assertEquals("'a&b|c;d'", escapeForInputText("a&b|c;d"))
        assertEquals("'*'", escapeForInputText("*"))
        assertEquals("'#tag'", escapeForInputText("#tag"))
        assertEquals("'~/{a,b}'", escapeForInputText("~/{a,b}"))
        assertEquals("'\$HOME'", escapeForInputText("\$HOME"))
    }

    @Test
    fun `carries quotes and a backslash through as themselves`() {
        assertEquals("'it'\\''s%s\"quoted\"'", escapeForInputText("it's \"quoted\""))
        assertEquals("'a\\b'", escapeForInputText("a\\b"))
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
