package com.kitakkun.jetwhale.plugins.soil.agent

import kotlinx.serialization.KSerializer
import soil.query.core.UniqueId
import kotlin.reflect.KClass

/**
 * Serializers for values the agent cannot find one for itself, registered per namespace or per id
 * class.
 *
 * Without one, the agent looks up the serializer of each value's class, which covers every
 * non-generic `@Serializable` class and the built-in types, walking into collections, maps, pairs
 * and infinite-query chunks. A generic class such as `Page<User>` has no serializer to look up, so
 * its value falls back to `toString()` unless it is registered here:
 *
 * ```kotlin
 * SoilValueSerializers {
 *     namespace("users/page", Page.serializer(User.serializer()))
 *     idClass<GetPostPageKey.Id>(Page.serializer(Post.serializer()))
 * }
 * ```
 *
 * A serializer describes the value one fetch returns: for an infinite query, the data of one chunk,
 * not the list of chunks. A namespace registration wins over an id class one.
 */
class SoilValueSerializers private constructor(
    private val serializersByNamespace: Map<String, KSerializer<*>>,
    private val serializersByIdClass: Map<KClass<out UniqueId>, KSerializer<*>>,
) {
    internal fun serializerFor(id: UniqueId): KSerializer<*>? = serializersByNamespace[id.namespace] ?: serializersByIdClass[id::class]

    class Builder internal constructor() {
        private val serializersByNamespace = mutableMapOf<String, KSerializer<*>>()
        private val serializersByIdClass = mutableMapOf<KClass<out UniqueId>, KSerializer<*>>()

        /** Encodes the values of entries whose id has exactly [namespace] with [serializer]. */
        fun namespace(namespace: String, serializer: KSerializer<*>) {
            serializersByNamespace[namespace] = serializer
        }

        /** Encodes the values of entries whose id is an instance of exactly [idClass] with [serializer]. */
        fun idClass(idClass: KClass<out UniqueId>, serializer: KSerializer<*>) {
            serializersByIdClass[idClass] = serializer
        }

        /** Encodes the values of entries whose id is an instance of exactly [I] with [serializer]. */
        inline fun <reified I : UniqueId> idClass(serializer: KSerializer<*>) {
            idClass(I::class, serializer)
        }

        internal fun build(): SoilValueSerializers = SoilValueSerializers(serializersByNamespace.toMap(), serializersByIdClass.toMap())
    }

    companion object {
        /** No registrations: every value is encoded by its own classes' serializers or `toString()`. */
        val None: SoilValueSerializers = SoilValueSerializers(emptyMap(), emptyMap())

        operator fun invoke(block: Builder.() -> Unit): SoilValueSerializers = Builder().apply(block).build()
    }
}
