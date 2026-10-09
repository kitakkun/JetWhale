package com.kitakkun.jetwhale.plugins.soil.agent

import kotlin.reflect.KClass

internal actual fun typeArgumentCountOf(kClass: KClass<*>): Int = UNKNOWN_TYPE_ARGUMENT_COUNT
