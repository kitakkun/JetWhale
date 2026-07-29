package com.kitakkun.jetwhale.plugins.nav3.agent

import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackOperation
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Keys are plain strings here: the operations are about positions, not about what a key is. */
private fun key(name: String): JsonElement = JsonPrimitive(name)

private fun decodeKey(element: JsonElement): String {
    val name = (element as JsonPrimitive).content
    require(name.isNotEmpty()) { "empty key" }
    return name
}

/** Counts the removals a mutation performs, so "smallest possible edit" can be asserted. */
private class CountingList(private val delegate: MutableList<String>) : MutableList<String> by delegate {
    var removals = 0
        private set

    override fun removeAt(index: Int): String {
        removals++
        return delegate.removeAt(index)
    }
}

class NavBackStackOperationsTest {
    private fun apply(stack: MutableList<String>, vararg operations: NavBackStackOperation) {
        applyNavOperations(stack, operations.toList(), ::decodeKey)
    }

    @Test
    fun `push appends to the top of the stack`() {
        val stack = mutableListOf("home")

        apply(stack, NavBackStackOperation.Push(key("detail"), index = null))

        assertContentEquals(listOf("home", "detail"), stack)
    }

    @Test
    fun `push with an index inserts below the top`() {
        val stack = mutableListOf("home", "detail")

        apply(stack, NavBackStackOperation.Push(key("list"), index = 1))

        assertContentEquals(listOf("home", "list", "detail"), stack)
    }

    @Test
    fun `pop removes the requested number of entries`() {
        val stack = mutableListOf("home", "list", "detail")

        apply(stack, NavBackStackOperation.Pop(count = 2))

        assertContentEquals(listOf("home"), stack)
    }

    @Test
    fun `popTo keeps the target entry unless it is inclusive`() {
        val exclusive = mutableListOf("home", "list", "detail")
        apply(exclusive, NavBackStackOperation.PopTo(index = 1, inclusive = false))
        assertContentEquals(listOf("home", "list"), exclusive)

        val inclusive = mutableListOf("home", "list", "detail")
        apply(inclusive, NavBackStackOperation.PopTo(index = 1, inclusive = true))
        assertContentEquals(listOf("home"), inclusive)
    }

    @Test
    fun `removeAt drops one entry and keeps the ones above it`() {
        val stack = mutableListOf("home", "list", "detail")

        apply(stack, NavBackStackOperation.RemoveAt(index = 1))

        assertContentEquals(listOf("home", "detail"), stack)
    }

    @Test
    fun `moveToTop reorders without dropping anything`() {
        val stack = mutableListOf("home", "list", "detail")

        apply(stack, NavBackStackOperation.MoveToTop(index = 0))

        assertContentEquals(listOf("list", "detail", "home"), stack)
    }

    @Test
    fun `replaceAll swaps the whole stack`() {
        val stack = mutableListOf("home", "list")

        apply(stack, NavBackStackOperation.ReplaceAll(listOf(key("settings"), key("about"))))

        assertContentEquals(listOf("settings", "about"), stack)
    }

    @Test
    fun `operations are applied in order against the stack as it stands`() {
        val stack = mutableListOf("home", "list", "detail")

        apply(
            stack,
            NavBackStackOperation.PopTo(index = 0, inclusive = false),
            NavBackStackOperation.Push(key("settings"), index = null),
            NavBackStackOperation.Push(key("about"), index = null),
        )

        assertContentEquals(listOf("home", "settings", "about"), stack)
    }

    @Test
    fun `an invalid index leaves the stack untouched`() {
        val stack = mutableListOf("home", "list")

        val failure = assertFailsWith<IllegalArgumentException> {
            apply(
                stack,
                NavBackStackOperation.Push(key("detail"), index = null),
                NavBackStackOperation.RemoveAt(index = 9),
            )
        }

        assertEquals("removeAt index 9 is out of range (0..2)", failure.message)
        assertContentEquals(listOf("home", "list"), stack)
    }

    @Test
    fun `an undecodable key leaves the stack untouched`() {
        val stack = mutableListOf("home")

        assertFailsWith<IllegalArgumentException> {
            apply(stack, NavBackStackOperation.Push(key(""), index = null))
        }

        assertContentEquals(listOf("home"), stack)
    }

    @Test
    fun `popping everything is refused so the app is never left with nothing to render`() {
        val stack = mutableListOf("home", "list")

        val failure = assertFailsWith<IllegalArgumentException> {
            apply(stack, NavBackStackOperation.Pop(count = 2))
        }

        assertEquals(
            "the operations would leave the back stack empty, which Navigation 3 cannot render",
            failure.message,
        )
        assertContentEquals(listOf("home", "list"), stack)
    }

    @Test
    fun `popping more entries than the stack holds is refused`() {
        val stack = mutableListOf("home")

        val failure = assertFailsWith<IllegalArgumentException> {
            apply(stack, NavBackStackOperation.Pop(count = 3))
        }

        assertEquals("cannot pop 3 entries off a stack of 1", failure.message)
    }

    @Test
    fun `pushing does not disturb the entries already on the stack`() {
        val stack = CountingList(mutableListOf("home", "list"))

        apply(stack, NavBackStackOperation.Push(key("detail"), index = null))

        assertContentEquals(listOf("home", "list", "detail"), stack)
        assertEquals(0, stack.removals)
    }

    @Test
    fun `a change deep in the stack only rewrites the entries above it`() {
        val stack = CountingList(mutableListOf("home", "list", "detail"))

        apply(stack, NavBackStackOperation.RemoveAt(index = 1))

        assertContentEquals(listOf("home", "detail"), stack)
        // "home" stays exactly where it is: only "list" and "detail" are taken off.
        assertEquals(2, stack.removals)
    }
}
