package com.kitakkun.jetwhale.protocol.serialization

import com.kitakkun.jetwhale.protocol.InternalJetWhaleApi
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json

/**
 * The Json instance used for JetWhale Debugger Protocol.
 * Configured to use "type" as the class discriminator and to ignore unknown keys for safe deserialization.
 */
@OptIn(ExperimentalSerializationApi::class)
@InternalJetWhaleApi
public val JetWhaleJson: Json = Json {
    classDiscriminator = "type"
    classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = true
}

/**
 * Tries to decode the given string to the specified type [T].
 * Returns null if decoding fails.
 */
@InternalJetWhaleApi
public inline fun <reified T> Json.decodeFromStringOrNull(value: String): T? {
    return try {
        decodeFromString<T>(value)
    } catch (_: Throwable) {
        null
    }
}

/**
 * Tries to decode the given string to the specified type [T] using the provided [deserializer].
 * Returns null if decoding fails.
 */
@InternalJetWhaleApi
public fun <T> Json.decodeFromStringOrNull(deserializer: DeserializationStrategy<T>, value: String): T? {
    return try {
        decodeFromString(deserializer, value)
    } catch (_: Throwable) {
        null
    }
}

/**
 * Tries to encode the given value of type [T] to a string.
 * Returns null if encoding fails.
 */
@InternalJetWhaleApi
public inline fun <reified T> Json.encodeToStringOrNull(value: T): String? {
    return try {
        encodeToString(value)
    } catch (_: Throwable) {
        null
    }
}

/**
 * Tries to encode the given value of type [T] to a string using the provided [serializer].
 * Returns null if encoding fails.
 */
@InternalJetWhaleApi
public inline fun <reified T> Json.encodeToStringOrNull(serializer: SerializationStrategy<T>, value: T): String? {
    return try {
        encodeToString(value)
    } catch (_: Throwable) {
        null
    }
}
