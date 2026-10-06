package com.kitakkun.jetwhale.plugins.mainthread.agent

import com.kitakkun.jetwhale.plugins.mainthread.protocol.ViolationKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StackFramesTest {
    @Test
    fun `a dispatching line becomes its handler and callback without identity noise`() {
        val line = ">>>>> Dispatching to Handler (android.view.ViewRootImpl\$ViewRootHandler) {3f1a2b} android.view.View\$PerformClick@9c0d1e: 0"

        assertEquals(
            LooperLogLine.Dispatching("Handler (android.view.ViewRootImpl\$ViewRootHandler) android.view.View\$PerformClick"),
            parseLooperLogLine(line),
        )
    }

    @Test
    fun `a finished line and an unrelated line are told apart`() {
        assertEquals(LooperLogLine.Finished, parseLooperLogLine("<<<<< Finished to Handler (android.os.Handler) {1a2b} null"))
        assertNull(parseLooperLogLine("something else"))
    }

    @Test
    fun `a label that is not a Looper line is kept as it is`() {
        assertEquals("InvocationEvent Foo", formatTaskLabel("InvocationEvent Foo"))
    }

    @Test
    fun `a signature names the innermost app frames and skips the platform`() {
        val stack = listOf(
            "java.io.FileOutputStream.write(FileOutputStream.java:1)",
            "android.app.SharedPreferencesImpl.commit(SharedPreferencesImpl.java:2)",
            "com.example.settings.SettingsStore.save(SettingsStore.kt:10)",
            "com.example.settings.SettingsScreen.onToggle(SettingsScreen.kt:20)",
            "com.example.MainActivity.onClick(MainActivity.kt:30)",
            "com.example.Unused.frame(Unused.kt:40)",
            "android.os.Looper.loop(Looper.java:3)",
        )

        assertEquals(
            "com.example.settings.SettingsStore.save(SettingsStore.kt:10) ← com.example.settings.SettingsScreen.onToggle(SettingsScreen.kt:20) ← com.example.MainActivity.onClick(MainActivity.kt:30)",
            stackSignature(stack),
        )
        assertEquals("com.example.settings.SettingsStore.save(SettingsStore.kt:10)", callSiteOf(stack))
    }

    @Test
    fun `JetWhale's agent frames are skipped but an app in the same namespace is not`() {
        val stack = listOf(
            "com.kitakkun.jetwhale.plugins.mainthread.agent.StackSampler.run(StackSampler.kt:1)",
            "com.kitakkun.jetwhale.demo.shared.Blockers.sleep(Blockers.kt:2)",
        )

        assertEquals("com.kitakkun.jetwhale.demo.shared.Blockers.sleep(Blockers.kt:2)", callSiteOf(stack))
    }

    @Test
    fun `a stack with only platform frames is named by its innermost frames`() {
        val stack = listOf("android.os.MessageQueue.nativePollOnce(Native Method)", "android.os.Looper.loop(Looper.java:3)")

        assertEquals("android.os.MessageQueue.nativePollOnce(Native Method) ← android.os.Looper.loop(Looper.java:3)", stackSignature(stack))
        assertEquals("android.os.MessageQueue.nativePollOnce(Native Method)", callSiteOf(stack))
    }

    @Test
    fun `StrictMode violation classes map to their kinds`() {
        assertEquals(ViolationKind.DiskRead, violationKindOf("android.os.strictmode.DiskReadViolation"))
        assertEquals(ViolationKind.DiskWrite, violationKindOf("android.os.strictmode.DiskWriteViolation"))
        assertEquals(ViolationKind.Network, violationKindOf("android.os.strictmode.NetworkViolation"))
        assertEquals(ViolationKind.CustomSlowCall, violationKindOf("android.os.strictmode.CustomViolation"))
        assertEquals(ViolationKind.Other, violationKindOf("android.os.strictmode.SomethingNew"))
    }
}
