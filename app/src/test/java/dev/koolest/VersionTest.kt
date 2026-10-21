package dev.koolest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test fun compares() {
        assertTrue(newerVersion("1.2", "1.1"))
        assertTrue(newerVersion("1.10", "1.9"))
        assertTrue(newerVersion("1.2.1", "1.2"))
        assertTrue(newerVersion("2.0", "1.9.9"))
        assertFalse(newerVersion("1.2", "1.2"))
        assertFalse(newerVersion("1.2", "1.2.0"))
        assertFalse(newerVersion("1.1", "1.2"))
    }
}
