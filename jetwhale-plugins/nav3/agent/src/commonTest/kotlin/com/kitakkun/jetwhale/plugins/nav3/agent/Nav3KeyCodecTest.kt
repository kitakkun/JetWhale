package com.kitakkun.jetwhale.plugins.nav3.agent

import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyFieldDescriptor
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyTypeDescriptor
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val openModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(HomeKey::class, HomeKey.serializer())
        subclass(DetailKey::class, DetailKey.serializer())
        subclass(CatalogKey::class, CatalogKey.serializer())
    }
}

class Nav3KeyCodecTest {
    @Test
    fun `open polymorphic codec round-trips a key through JSON`() {
        val codec = Nav3KeyCodec.openPolymorphic(openModule)
        val key = DetailKey(id = "42", page = 3, note = null)

        val encoded = codec.encode(key)

        assertEquals(
            buildJsonObject {
                put("type", "Detail")
                put("id", "42")
                put("page", 3)
                put("note", JsonNull)
            },
            encoded,
        )
        assertEquals(key, codec.decode(assertNotNull(encoded)))
    }

    @Test
    fun `encoding a key the app did not register yields null instead of failing`() {
        // CatalogKey is registered; a key type left out of the module is not encodable, and the
        // entry still has to be listed so it can be popped by index.
        val codec = Nav3KeyCodec.openPolymorphic(
            SerializersModule { polymorphic(NavKey::class) { subclass(CatalogKey::class, CatalogKey.serializer()) } },
        )

        assertNull(codec.encode(HomeKey))
    }

    @Test
    fun `catalog lists every type registered against NavKey in a stable order`() {
        val codec = Nav3KeyCodec.openPolymorphic(openModule)

        // Sorted by name, because a SerializersModule enumerates its registrations in no defined
        // order — the host would otherwise show the types shuffled differently every session.
        assertEquals(listOf("Catalog", "Detail", "Home"), codec.keyTypes.map(NavKeyTypeDescriptor::serialName))
    }

    @Test
    fun `catalog describes fields with their defaults and nullability`() {
        val codec = Nav3KeyCodec.openPolymorphic(openModule)

        val detail = codec.keyTypes.single { it.serialName == "Detail" }

        assertContentEquals(listOf("id", "page", "note"), detail.fields.map(NavKeyFieldDescriptor::name))
        assertContentEquals(listOf("String", "Int", "String?"), detail.fields.map(NavKeyFieldDescriptor::type))
        assertContentEquals(listOf(false, true, false), detail.fields.map(NavKeyFieldDescriptor::optional))
        assertContentEquals(listOf(false, false, true), detail.fields.map(NavKeyFieldDescriptor::nullable))
    }

    @Test
    fun `catalog templates are ready to fill in and push`() {
        val codec = Nav3KeyCodec.openPolymorphic(openModule)

        val templates = codec.keyTypes.associate { it.serialName to it.template }

        assertEquals(buildJsonObject { put("type", "Home") }, templates["Home"])
        assertEquals(
            buildJsonObject {
                put("type", "Detail")
                put("id", "")
                put("page", 0)
                put("note", JsonNull)
            },
            templates["Detail"],
        )
        // An enum suggests its first entry and a list starts out empty, so the template decodes as-is.
        val catalog = templates["Catalog"] as JsonObject
        assertEquals(JsonPrimitive("Grid"), catalog["layout"])
        assertEquals(CatalogKey(layout = CatalogLayout.Grid, tags = emptyList()), codec.decode(catalog))
    }

    @Test
    fun `catalog reads a sealed hierarchy without any module`() {
        val codec = Nav3KeyCodec.closedPolymorphic(Screen.serializer())

        assertEquals(listOf("screen.detail", "screen.home"), codec.keyTypes.map(NavKeyTypeDescriptor::serialName))
        assertEquals(Screen.Detail("7"), codec.decode(assertNotNull(codec.encode(Screen.Detail("7")))))
    }

    @Test
    fun `enum and list fields are described in a readable way`() {
        val codec = Nav3KeyCodec.openPolymorphic(openModule)

        val catalog = codec.keyTypes.single { it.serialName == "Catalog" }

        assertContentEquals(listOf("enum(Grid|List)", "List<String>"), catalog.fields.map(NavKeyFieldDescriptor::type))
    }
}
