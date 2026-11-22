package cz.filiprojek.phonemirror.core.session

import cz.filiprojek.phonemirror.core.net.ViewerInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalBrokerTest {
    private val alice = ViewerInfo("alice-id", "Alice's phone", "10.0.0.2")
    private val bob = ViewerInfo("bob-id", "Bob's phone", "10.0.0.3")

    private fun TestScope.broker() = ApprovalBroker(clock = { testScheduler.currentTime })

    @Test
    fun `request is pending until the user allows it`() = runTest {
        val broker = broker()
        val result = async { broker.request(alice) }
        runCurrent()
        assertEquals(alice, broker.pending.value)
        broker.decide(allow = true)
        assertTrue(result.await())
        assertNull(broker.pending.value)
    }

    @Test
    fun `request resolves false when the user denies it`() = runTest {
        val broker = broker()
        val result = async { broker.request(alice) }
        runCurrent()
        broker.decide(allow = false)
        assertFalse(result.await())
        assertNull(broker.pending.value)
    }

    @Test
    fun `request is denied automatically after thirty seconds without a decision`() = runTest {
        val broker = broker()
        val result = async { broker.request(alice) }
        advanceTimeBy(29_999)
        runCurrent()
        assertEquals(alice, broker.pending.value)
        advanceTimeBy(2)
        assertFalse(result.await())
        assertNull(broker.pending.value)
    }

    @Test
    fun `deciding with nothing pending is a no-op`() = runTest {
        val broker = broker()
        broker.decide(allow = true)
        assertNull(broker.pending.value)
    }

    @Test
    fun `an approved viewer that dropped is re-approved silently within two minutes`() = runTest {
        val broker = broker()
        approve(broker, alice)
        broker.viewerLeft(alice.deviceId)
        advanceTimeBy(119_000)
        val result = async { broker.request(alice) }
        runCurrent()
        assertNull(broker.pending.value)
        assertTrue(result.await())
    }

    @Test
    fun `an approved viewer returning after the grace period is asked again`() = runTest {
        val broker = broker()
        approve(broker, alice)
        broker.viewerLeft(alice.deviceId)
        advanceTimeBy(121_000)
        val result = async { broker.request(alice) }
        runCurrent()
        assertEquals(alice, broker.pending.value)
        broker.decide(false)
        assertFalse(result.await())
    }

    @Test
    fun `a different device never inherits the grace period`() = runTest {
        val broker = broker()
        approve(broker, alice)
        broker.viewerLeft(alice.deviceId)
        val result = async { broker.request(bob) }
        runCurrent()
        assertEquals(bob, broker.pending.value)
        broker.decide(true)
        assertTrue(result.await())
    }

    @Test
    fun `a denied device gets no grace period`() = runTest {
        val broker = broker()
        val denied = async { broker.request(alice) }
        runCurrent()
        broker.decide(false)
        denied.await()
        broker.viewerLeft(alice.deviceId)
        val again = async { broker.request(alice) }
        runCurrent()
        assertEquals(alice, broker.pending.value)
        broker.decide(false)
        again.await()
    }

    private suspend fun TestScope.approve(broker: ApprovalBroker, viewer: ViewerInfo) {
        val result = async { broker.request(viewer) }
        runCurrent()
        broker.decide(true)
        assertTrue(result.await())
    }
}
