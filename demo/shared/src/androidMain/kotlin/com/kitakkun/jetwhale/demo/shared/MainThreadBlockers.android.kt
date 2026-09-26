package com.kitakkun.jetwhale.demo.shared

import android.app.Application
import android.content.Context
import android.os.NetworkOnMainThreadException
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

actual val platformMainThreadBlockers: List<MainThreadBlocker> = listOf(
    MainThreadBlocker("SharedPreferences commit on main") {
        val preferences = demoApplication().getSharedPreferences("main_thread_demo", Context.MODE_PRIVATE)
        repeat(50) { preferences.edit().putInt("counter", it).commit() }
        "Committed SharedPreferences 50 times on the main thread."
    },
    MainThreadBlocker("File read on main") {
        val file = File(demoApplication().cacheDir, "main_thread_demo.bin").apply { writeBytes(ByteArray(4 * 1024 * 1024)) }
        "Read ${file.readBytes().size} bytes on the main thread."
    },
    MainThreadBlocker("Sleep 400 ms on main") {
        Thread.sleep(400)
        "Slept 400 ms on the main thread."
    },
    MainThreadBlocker("Network call on main (guarded)") {
        // Android throws for network access on the main thread; the demo shows that rather than crash.
        try {
            (URL("http://127.0.0.1:1/").openConnection() as HttpURLConnection).responseCode.toString()
        } catch (e: NetworkOnMainThreadException) {
            "Android refused the network call on the main thread: ${e.javaClass.simpleName}"
        } catch (e: IOException) {
            "The network call ran on the main thread and failed: ${e.message}"
        }
    },
)

private fun demoApplication(): Application = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as Application
