package com.kitakkun.jetwhale.plugins.soil.agent

import kotlin.reflect.KClass

/**
 * How many type arguments to hand the serializer lookup for an instance of [kClass].
 *
 * The JVM looks a generic class's serializer up by its exact type parameter count, which
 * reflection tells. The other platforms cannot tell the count, and index the arguments by
 * position: on Wasm, a generic class looked up with too few of them traps, which no `catch` stops,
 * so they are given more than any value class needs; extra arguments go unused.
 */
internal expect fun typeArgumentCountOf(kClass: KClass<*>): Int

/** More type parameters than a value class is expected to declare. */
internal const val MAX_ASSUMED_TYPE_ARGUMENT_COUNT = 8
