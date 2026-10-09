package com.kitakkun.jetwhale.plugins.mirror.host

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class H264SubscriptionTest {
    @Test
    fun `a reader starts at the first key frame and then reads every access unit in order`() {
        val subscription = H264Subscription(maxQueuedBytes = 1_000)

        subscription.offer(AccessUnit(byteArrayOf(1), isKeyFrame = false))
        subscription.offer(AccessUnit(byteArrayOf(2), isKeyFrame = true))
        subscription.offer(AccessUnit(byteArrayOf(3), isKeyFrame = false))
        subscription.endStreamAfterQueued()

        assertContentEquals(byteArrayOf(2, 3), subscription.stream.readAllBytes())
    }

    @Test
    fun `an ended subscription is read to its end and then refuses more`() {
        val subscription = H264Subscription(maxQueuedBytes = 1_000)
        subscription.offer(AccessUnit(byteArrayOf(1, 2), isKeyFrame = true))

        subscription.endStreamAfterQueued()

        assertFalse(subscription.offer(AccessUnit(byteArrayOf(3), isKeyFrame = false)))
        assertContentEquals(byteArrayOf(1, 2), subscription.stream.readAllBytes())
        assertEquals(-1, subscription.stream.read())
    }

    @Test
    fun `closing drops what is queued and ends the stream at once`() {
        val subscription = H264Subscription(maxQueuedBytes = 1_000)
        subscription.offer(AccessUnit(byteArrayOf(1, 2), isKeyFrame = true))

        subscription.stream.close()

        assertEquals(-1, subscription.stream.read())
        assertFalse(subscription.offer(AccessUnit(byteArrayOf(3), isKeyFrame = false)))
    }

    @Test
    fun `a reader that falls behind past the limit has its stream ended after what it has queued`() {
        val subscription = H264Subscription(maxQueuedBytes = 4)

        assertTrue(subscription.offer(AccessUnit(byteArrayOf(1, 2, 3), isKeyFrame = true)))
        assertFalse(subscription.offer(AccessUnit(byteArrayOf(4, 5), isKeyFrame = false)))

        assertContentEquals(byteArrayOf(1, 2, 3), subscription.stream.readAllBytes())
    }

    @Test
    fun `what the reader has read no longer counts against the limit`() {
        val subscription = H264Subscription(maxQueuedBytes = 4)
        subscription.offer(AccessUnit(byteArrayOf(1, 2, 3), isKeyFrame = true))
        assertContentEquals(byteArrayOf(1, 2, 3), subscription.stream.readNBytes(3))

        assertTrue(subscription.offer(AccessUnit(byteArrayOf(4, 5, 6), isKeyFrame = false)))
    }
}
