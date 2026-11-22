package cz.filiprojek.phonemirror.core.camera

import kotlin.test.Test
import kotlin.test.assertEquals

class RotationTrackerTest {

    @Test
    fun `first reading snaps to the nearest quarter turn`() {
        assertEquals(90, RotationTracker().update(80))
        assertEquals(0, RotationTracker().update(350))
    }

    @Test
    fun `wobbling around a 45 degree boundary does not flap (regression)`() {
        val tracker = RotationTracker()
        tracker.update(10)
        val seen = listOf(44, 46, 44, 47, 43, 50, 46).map(tracker::update)
        assertEquals(List(7) { 0 }, seen)
    }

    @Test
    fun `switches once clearly within 30 degrees of the next quarter`() {
        val tracker = RotationTracker()
        tracker.update(90)
        assertEquals(90, tracker.update(130))
        assertEquals(90, tracker.update(149))
        assertEquals(180, tracker.update(150))
        assertEquals(180, tracker.update(140))
    }

    @Test
    fun `wraps around zero`() {
        val tracker = RotationTracker()
        tracker.update(270)
        assertEquals(270, tracker.update(310))
        assertEquals(0, tracker.update(350))
        assertEquals(0, tracker.update(20))
    }

    @Test
    fun `unknown orientation keeps the last value`() {
        val tracker = RotationTracker()
        assertEquals(0, tracker.update(-1))
        tracker.update(180)
        assertEquals(180, tracker.update(-1))
    }
}
