package dev.koolest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinTest {
    @Test fun matchesOnlyItsPin() {
        val stored = hashPin("0420")
        assertTrue(pinMatches(stored, "0420"))
        assertFalse(pinMatches(stored, "0421"))
        assertNotEquals(stored, hashPin("0420")) // fresh salt each time
    }
}
