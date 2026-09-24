package com.kitakkun.jetwhale.demo.shared

import java.io.File

actual val platformMainThreadBlockers: List<MainThreadBlocker> = listOf(
    MainThreadBlocker("Sleep 400 ms on main") {
        Thread.sleep(400)
        "Slept 400 ms on the event dispatch thread."
    },
    MainThreadBlocker("File write on main") {
        val file = File.createTempFile("main-thread-demo", ".bin").apply { deleteOnExit() }
        repeat(20) { file.writeBytes(ByteArray(4 * 1024 * 1024)) }
        "Wrote 80 MB on the event dispatch thread."
    },
)
