package dev.koolest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelRowsTest {
    private val items = listOf("Subs" to "s1", "Recs" to "r1", "Subs" to "s2", "History" to "h1")

    @Test fun oneChannelStaysOneRow() {
        assertEquals(mapOf("tube" to listOf("a", "b")), channelRows("tube", listOf("Only"), listOf("Only" to "a", "Only" to "b")) { false })
    }

    @Test fun severalChannelsSplitInTheAppsOrder() {
        val rows = channelRows("tube", listOf("Subs", "Recs", "History"), items) { it != "Recs" }
        assertEquals(listOf("tube", "tube/Subs", "tube/History"), rows.keys.toList())
        assertEquals(emptyList<String>(), rows["tube"])
        assertEquals(listOf("s1", "s2"), rows["tube/Subs"])
        assertEquals(listOf("h1"), rows["tube/History"])
    }

    @Test fun koolestChoiceBeatsTheTv() {
        assertTrue(channelOn("t/A", tvShows = false, tvShowsAny = true, chosenOn = setOf("t/A"), chosenOff = emptySet()))
        assertFalse(channelOn("t/A", tvShows = true, tvShowsAny = true, chosenOn = emptySet(), chosenOff = setOf("t/A")))
        assertTrue(channelOn("t/A", tvShows = true, tvShowsAny = true, chosenOn = emptySet(), chosenOff = emptySet()))
        assertFalse(channelOn("t/B", tvShows = false, tvShowsAny = true, chosenOn = emptySet(), chosenOff = emptySet()))
    }

    @Test fun tvThatHidesEveryChannelIsNotChoosing() {
        assertTrue(channelOn("t/A", tvShows = false, tvShowsAny = false, chosenOn = emptySet(), chosenOff = emptySet()))
    }
}
