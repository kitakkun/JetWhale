package com.kitakkun.jetwhale.plugins.deeplinks.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LinkTemplatesTest {
    @Test
    fun `placeholders are listed once in order`() {
        assertEquals(listOf("shop", "id"), placeholdersOf("https://example.com/{shop}/item/{id}?from={shop}"))
    }

    @Test
    fun `values are percent encoded so they stay one segment`() {
        assertEquals("demo://item/a%2Fb%20c%3Fd", fillTemplate("demo://item/{id}", mapOf("id" to "a/b c?d")))
    }

    @Test
    fun `a placeholder without a value is refused`() {
        assertFailsWith<IllegalArgumentException> { fillTemplate("demo://item/{id}", mapOf("id" to "")) }
        assertFailsWith<IllegalArgumentException> { fillTemplate("demo://item/{id}", emptyMap()) }
    }
}
