package dev.plume

import org.junit.Assert.assertEquals
import org.junit.Test

class ContinueWatchingTest {
    @Test fun newestFirstAndThreePerApp() {
        // Netflix reports times for ten shows; Stremio's channel rows all get the time it was last opened.
        val netflix = (1..10).map { 100L - it to "netflix/$it" }
        val stremio = (1..4).map { 95L to "stremio/$it" }
        val nuvio = (1..2).map { 0L to "nuvio/$it" } // never opened from Koolest
        val shown = newestPerApp(netflix + stremio + nuvio, 3) { it.substringBefore('/') }
        assertEquals(listOf("netflix/1", "netflix/2", "netflix/3", "stremio/1", "stremio/2", "stremio/3", "nuvio/1", "nuvio/2"), shown)
    }
}
