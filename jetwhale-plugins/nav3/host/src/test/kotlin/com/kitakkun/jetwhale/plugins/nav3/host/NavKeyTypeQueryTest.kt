package com.kitakkun.jetwhale.plugins.nav3.host

import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyTypeDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals

class NavKeyTypeQueryTest {
    private val keyTypes = listOf(
        keyType("com.example.navigation.ProfileKey"),
        keyType("com.example.settings.SettingsKey"),
        keyType("Detail"),
    )

    @Test
    fun `a query matches regardless of case`() {
        assertEquals(listOf("com.example.navigation.ProfileKey"), matchingSerialNames("pROFILE"))
    }

    @Test
    fun `a query matches the simple name at the end of a qualified serial name`() {
        assertEquals(listOf("com.example.settings.SettingsKey"), matchingSerialNames("SettingsKey"))
    }

    @Test
    fun `a query matches a package segment of the qualified serial name`() {
        assertEquals(listOf("com.example.navigation.ProfileKey"), matchingSerialNames("example.navigation"))
    }

    @Test
    fun `a query matches a serial name the app gave without a package`() {
        assertEquals(listOf("Detail"), matchingSerialNames("deta"))
    }

    @Test
    fun `a query no serial name contains matches nothing`() {
        assertEquals(emptyList(), matchingSerialNames("Checkout"))
    }

    @Test
    fun `an empty query matches every type`() {
        assertEquals(keyTypes.map(NavKeyTypeDescriptor::serialName), matchingSerialNames(""))
    }

    @Test
    fun `a blank query matches every type`() {
        assertEquals(keyTypes.map(NavKeyTypeDescriptor::serialName), matchingSerialNames("   "))
    }

    @Test
    fun `whitespace around a query is ignored`() {
        val query = NavKeyTypeQuery("  profile ")

        assertEquals("profile", query.text)
        assertEquals(listOf("com.example.navigation.ProfileKey"), keyTypes.filter(query::matches).map(NavKeyTypeDescriptor::serialName))
    }

    private fun matchingSerialNames(typedText: String): List<String> = keyTypes.filter(NavKeyTypeQuery(typedText)::matches).map(NavKeyTypeDescriptor::serialName)
}
