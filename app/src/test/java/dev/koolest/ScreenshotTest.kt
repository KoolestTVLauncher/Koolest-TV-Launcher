package dev.koolest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotTest {
    @Test fun blackFramesAreBlank() {
        assertTrue(isBlank(IntArray(64 * 36) { 0xFF000000.toInt() }))
        assertTrue(isBlank(IntArray(64 * 36) { 0xFF101010.toInt() })) // near-black from scaling
    }

    @Test fun anythingOnTopIsKept() {
        val frame = IntArray(64 * 36) { 0xFF000000.toInt() }
        frame[100] = 0xFFFFFFFF.toInt() // a bit of player controls over the black video
        assertFalse(isBlank(frame))
        assertFalse(isBlank(IntArray(64 * 36) { 0xFF2A2F3D.toInt() })) // a dark menu, not black
    }
}
