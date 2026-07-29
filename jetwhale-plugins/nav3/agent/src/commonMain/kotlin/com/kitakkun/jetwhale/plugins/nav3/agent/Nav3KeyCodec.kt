package com.kitakkun.jetwhale.plugins.nav3.agent

import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyTypeDescriptor
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/** The JSON key under which an encoded NavKey names its type. */
internal const val NAV_KEY_DISCRIMINATOR = "type"

/**
 * Translates between the app's `NavKey` instances and JSON, so a key can travel to the host and a
 * key the host describes can be turned back into a real one.
 *
 * A `NavKey` type is defined by the app, never by the debugger, so this is the one thing the app has
 * to hand over — and it is something a Navigation 3 app already has: the very serializers it passes
 * to `rememberNavBackStack` for saved state. Build the codec the same way the back stack itself is
 * built:
 *
 * ```kotlin
 * // Open polymorphism — NavBackStack<NavKey> with a SerializersModule
 * val module = SerializersModule {
 *     polymorphic(NavKey::class) {
 *         subclass(Home::class, Home.serializer())
 *         subclass(Detail::class, Detail.serializer())
 *     }
 * }
 * val backStack = rememberNavBackStack(SavedStateConfiguration { serializersModule = module }, Home)
 * val codec = Nav3KeyCodec.openPolymorphic(module)
 *
 * // Closed polymorphism — a sealed NavKey hierarchy
 * val codec = Nav3KeyCodec.closedPolymorphic(Screen.serializer())
 * ```
 *
 * From that alone the codec also derives the [keyTypes] catalog, which is what lets the host offer
 * "push a Detail(id = ...)" without the host knowing a thing about the app's screens.
 */
class Nav3KeyCodec<K : NavKey> private constructor(
    private val keySerializer: KSerializer<K>,
    private val json: Json,
) {
    /** The key types this codec can construct, derived from the app's own serializers. */
    internal val keyTypes: List<NavKeyTypeDescriptor> by lazy {
        describeNavKeyTypes(keySerializer, json.serializersModule)
    }

    /** Encodes [key], or returns null when the app's serializers do not cover its type. */
    internal fun encode(key: K): JsonElement? = try {
        json.encodeToJsonElement(keySerializer, key)
    } catch (_: SerializationException) {
        null
    }

    /** @throws SerializationException when [element] does not describe a key this codec knows. */
    internal fun decode(element: JsonElement): K = json.decodeFromJsonElement(keySerializer, element)

    companion object {
        /**
         * For a `NavBackStack<NavKey>` whose subtypes are registered in [serializersModule] —
         * the module passed to `rememberNavBackStack`.
         */
        fun openPolymorphic(serializersModule: SerializersModule): Nav3KeyCodec<NavKey> = Nav3KeyCodec(
            keySerializer = PolymorphicSerializer(NavKey::class),
            json = navKeyJson(serializersModule),
        )

        /**
         * For a sealed `NavKey` hierarchy, whose subtypes its own serializer already enumerates —
         * e.g. `closedPolymorphic(Screen.serializer())`.
         */
        fun <K : NavKey> closedPolymorphic(keySerializer: KSerializer<K>): Nav3KeyCodec<K> = Nav3KeyCodec(
            keySerializer = keySerializer,
            json = navKeyJson(EmptySerializersModule()),
        )

        private fun navKeyJson(module: SerializersModule): Json = Json {
            serializersModule = module
            classDiscriminator = NAV_KEY_DISCRIMINATOR
            // The host echoes keys it read back into pushes; an app that added a field since should
            // not turn that round trip into a failure.
            ignoreUnknownKeys = true
            // Defaulted fields are part of what the host shows and offers for editing, so they have
            // to be in the encoded form rather than implied by their absence.
            encodeDefaults = true
        }
    }
}
