package com.kitakkun.jetwhale.agent.runtime

import android.os.Build

internal actual fun getDeviceModelName(): String = Build.MODEL ?: "Unknown Android Device"
