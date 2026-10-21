package dev.koolest

import org.junit.Assert.assertEquals
import org.junit.Test

class ContinueWatchingTest {
    @Test fun mostUsedAppsFirstNewestShowsEach() {
        val netflix = (1..10).map { 100L - it to "netflix/$it" } // newest shows overall
        val nama = (1..3).map { 50L - it to "nama/$it" }
        val crave = (1..2).map { 10L - it to "crave/$it" }
        val uses = mapOf("nama" to 9, "netflix" to 2)
        val shown = byAppUse(crave + netflix + nama, 3, { uses[it] ?: 0 }) { it.substringBefore('/') }
        assertEquals(listOf("nama/1", "nama/2", "nama/3", "netflix/1", "netflix/2", "netflix/3", "crave/1", "crave/2"), shown)
    }

    @Test fun equalUseFallsBackToNewestShow() {
        val shown = byAppUse(listOf(1L to "a/old", 5L to "b/new"), 3, { 0 }) { it.substringBefore('/') }
        assertEquals(listOf("b/new", "a/old"), shown)
    }
}
