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
        assertEquals(183, RemoteKeys.id(183, 999))
    }

    @Test fun coreKeysAreProtected() {
        listOf(19, 20, 21, 22, 23, 66, 160, 4, 3, 26, 24, 25, 164, 91).forEach { assertTrue("$it", RemoteKeys.isCore(it)) }
        listOf(82, 183, 4056).forEach { assertFalse("$it", RemoteKeys.isCore(it)) }
    }

    @Test fun readableNames() {
        assertEquals("Prog red", RemoteKeys.readable("KEYCODE_PROG_RED"))
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
}
