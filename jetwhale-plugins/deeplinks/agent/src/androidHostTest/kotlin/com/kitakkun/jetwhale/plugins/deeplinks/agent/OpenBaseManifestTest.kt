package com.kitakkun.jetwhale.plugins.deeplinks.agent

import android.content.res.XmlResourceParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.FileNotFoundException
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OpenBaseManifestTest {
    @Test
    fun `the app's manifest is found past the START_DOCUMENT the platform's parser reports first`() {
        val base = FakeManifestParser("package" to "com.example.app")

        assertSame(base, openBaseManifest("com.example.app", apks(base)))
        assertEquals("com.example.app", base.getAttributeValue(null, "package"))
        assertFalse(base.closed)
    }

    @Test
    fun `another package's manifest is closed and skipped`() {
        val framework = FakeManifestParser("package" to "android")
        val base = FakeManifestParser("package" to "com.example.app")

        assertSame(base, openBaseManifest("com.example.app", apks(framework, base)))
        assertTrue(framework.closed)
    }

    @Test
    fun `a split's manifest is closed and skipped for the base manifest after it`() {
        val split = FakeManifestParser("package" to "com.example.app", "split" to "config.xxhdpi")
        val base = FakeManifestParser("package" to "com.example.app")

        assertSame(base, openBaseManifest("com.example.app", apks(split, base)))
        assertTrue(split.closed)
    }

    @Test
    fun `the lookup ends without a manifest when the cookies run out first`() {
        val framework = FakeManifestParser("package" to "android")

        assertNull(openBaseManifest("com.example.app", apks(framework)))
        assertTrue(framework.closed)
    }

    @Test
    fun `a manifest that fails to parse is closed`() {
        val corrupt = CorruptManifestParser()

        assertFailsWith<XmlPullParserException> { openBaseManifest("com.example.app", apks(corrupt)) }
        assertTrue(corrupt.closed)
    }
}

/** Opens [manifests] by asset cookie, from 1, and throws past the last one as the platform does. */
private fun apks(vararg manifests: XmlResourceParser): (Int) -> XmlResourceParser = { cookie ->
    manifests.getOrNull(cookie - 1) ?: throw FileNotFoundException("no APK has cookie $cookie")
}

/** A manifest read the way the platform's binary XML parser reads it, root element only. */
private class FakeManifestParser(vararg rootAttributes: Pair<String, String>) : XmlResourceParser by unsupportedParser() {
    private val rootAttributes = rootAttributes.toMap()
    private val events = ArrayDeque(listOf(XmlPullParser.START_DOCUMENT, XmlPullParser.START_TAG, XmlPullParser.END_TAG, XmlPullParser.END_DOCUMENT))
    private var eventType = XmlPullParser.START_DOCUMENT

    var closed = false
        private set

    override fun next(): Int {
        eventType = events.removeFirst()
        return eventType
    }

    override fun nextTag(): Int {
        val event = next()
        if (event != XmlPullParser.START_TAG && event != XmlPullParser.END_TAG) throw XmlPullParserException("expected start or end tag")
        return event
    }

    override fun getAttributeValue(namespace: String?, name: String?): String? = rootAttributes[name].takeIf { eventType == XmlPullParser.START_TAG }

    override fun close() {
        closed = true
    }
}

private class CorruptManifestParser : XmlResourceParser by unsupportedParser() {
    var closed = false
        private set

    override fun next(): Int = throw XmlPullParserException("Corrupt XML binary file")

    override fun close() {
        closed = true
    }
}

private fun unsupportedParser(): XmlResourceParser = Proxy.newProxyInstance(
    XmlResourceParser::class.java.classLoader,
    arrayOf(XmlResourceParser::class.java),
) { _, method, _ -> throw UnsupportedOperationException(method.name) } as XmlResourceParser
