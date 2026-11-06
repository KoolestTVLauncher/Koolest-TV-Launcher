package dev.koolest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteKeysTest {
    @Test fun roundTrips() {
        val maps = listOf(
            KeyMap(183, RemoteKeys.APP, "com.spotify.tv.android", true, "Red"),
            KeyMap(1_000_240, RemoteKeys.NONE),
            KeyMap(4056, RemoteKeys.ALL_APPS, name = "Net\tflix\nkey\\"),
        )
        assertEquals(maps, RemoteKeys.decode(RemoteKeys.encode(maps)))
    }

    @Test fun decodeIgnoresGarbage() {
        assertEquals(emptyList<KeyMap>(), RemoteKeys.decode(null))
        assertEquals(emptyList<KeyMap>(), RemoteKeys.decode(""))
        assertEquals(listOf(KeyMap(183, RemoteKeys.APP)), RemoteKeys.decode("x\ty\n183\tapp"))
    }

    @Test fun decodeKeepsMissingApps() {
        val m = KeyMap(184, RemoteKeys.APP, "com.gone.app")
        assertEquals(listOf(m), RemoteKeys.decode(RemoteKeys.encode(listOf(m))))
    }

    @Test fun unknownKeysUseScanCode() {
        assertEquals(1_000_240, RemoteKeys.id(0, 240))
        assertEquals(0, RemoteKeys.keyCode(1_000_240))
        assertEquals(240, RemoteKeys.scanCode(1_000_240))
    }

    @Test fun coreKeysAreProtected() {
        listOf(19, 20, 21, 22, 23, 66, 160, 4, 3, 26, 24, 25, 164, 91).forEach { assertTrue("$it", RemoteKeys.isCore(it)) }
        listOf(82, 183, 4056).forEach { assertFalse("$it", RemoteKeys.isCore(it)) }
    }

    @Test fun readableNames() {
        assertEquals("Red", RemoteKeys.readable("KEYCODE_PROG_RED"))
        assertEquals("Channel up", RemoteKeys.readable("KEYCODE_CHANNEL_UP"))
        assertNull(RemoteKeys.readable("4056"))
    }

    @Test fun firesOnlyOnCleanUp() {
        assertTrue(RemoteKeys.fires(1, 0, false))
        assertFalse(RemoteKeys.fires(0, 0, false))
        assertFalse(RemoteKeys.fires(0, 3, false))
        assertFalse(RemoteKeys.fires(1, 0, true))
    }

    @Test fun pendingIsTakenOnce() {
        assertNull(RemoteKeys.takePending())
        RemoteKeys.pending = KeyMap(184, RemoteKeys.ALL_APPS)
        assertEquals(KeyMap(184, RemoteKeys.ALL_APPS), RemoteKeys.takePending())
        assertNull(RemoteKeys.takePending())
    }

    @Test fun everywhereByDefault() {
        assertTrue(RemoteKeys.everywhereFor(KeyMap(4062, RemoteKeys.TV), RemoteKeys.APP)) // first job
        assertTrue(RemoteKeys.everywhereFor(KeyMap(82, RemoteKeys.MENU), RemoteKeys.ALL_APPS)) // Menu's default job
        assertFalse(RemoteKeys.everywhereFor(KeyMap(4062, RemoteKeys.APP, everywhere = false), RemoteKeys.SEARCH)) // chosen: stays
        assertTrue(RemoteKeys.everywhereFor(KeyMap(4062, RemoteKeys.APP, everywhere = true), RemoteKeys.SEARCH))
        assertFalse(RemoteKeys.everywhereFor(KeyMap(4062, RemoteKeys.APP, everywhere = true), RemoteKeys.MENU))
        assertFalse(RemoteKeys.everywhereFor(KeyMap(4062, RemoteKeys.APP, everywhere = true), RemoteKeys.TV))
    }

    @Test fun digitsStayInKoolest() {
        assertFalse(RemoteKeys.everywhereFor(KeyMap(12, RemoteKeys.TV), RemoteKeys.APP)) // the 5 key
        assertFalse(RemoteKeys.canBeEverywhere(148, RemoteKeys.ALL_APPS)) // numpad 4
        assertTrue(RemoteKeys.canBeEverywhere(183, RemoteKeys.ALL_APPS))
    }

    @Test fun scanCodeTellsSameKeyCodesApart() {
        val rakuten = RemoteKeys.id(249, 472)
        val ambilight = RemoteKeys.id(249, 186)
        assertTrue(rakuten != ambilight)
        assertEquals(249, RemoteKeys.keyCode(rakuten))
        assertEquals(472, RemoteKeys.scanCode(rakuten))
        assertEquals(183, RemoteKeys.keyCode(RemoteKeys.id(183, 999)))
        assertEquals(183, RemoteKeys.id(183, 0)) // no scan code: plain, as before
        assertEquals(183, RemoteKeys.keyCode(183))
        assertEquals(0, RemoteKeys.scanCode(183))
    }

    @Test fun findPrefersTheExactButtonThenTheKeyCode() {
        val rakuten = KeyMap(RemoteKeys.id(249, 472), RemoteKeys.APP, "rakuten")
        val ambilight = KeyMap(RemoteKeys.id(249, 186), RemoteKeys.NONE)
        val maps = listOf(rakuten, ambilight)
        assertEquals(ambilight, RemoteKeys.find(maps, 249, 186))
        assertEquals(rakuten, RemoteKeys.find(maps, 249, 472))
        assertEquals(rakuten, RemoteKeys.find(maps, 249, 9)) // another remote: same key code still works
        assertEquals(KeyMap(183, RemoteKeys.APP, "x"), RemoteKeys.find(listOf(KeyMap(183, RemoteKeys.APP, "x")), 183, 77)) // set up before
        assertNull(RemoteKeys.find(listOf(KeyMap(1_000_240, RemoteKeys.NONE)), 0, 241)) // unknown keys match exactly
    }

    @Test fun learningAgainUpgradesAnOldSetting() {
        val full = RemoteKeys.id(183, 77)
        assertEquals(listOf(KeyMap(full, RemoteKeys.APP, "x")), RemoteKeys.adopt(listOf(KeyMap(183, RemoteKeys.APP, "x")), full))
        val other = KeyMap(RemoteKeys.id(183, 78), RemoteKeys.NONE)
        val both = listOf(KeyMap(183, RemoteKeys.APP, "x"), other)
        assertEquals(both, RemoteKeys.adopt(both, full)) // already split: leave the old one as the fallback
        assertEquals(listOf(KeyMap(5, RemoteKeys.NONE)), RemoteKeys.adopt(listOf(KeyMap(5, RemoteKeys.NONE)), 183))
    }
}
