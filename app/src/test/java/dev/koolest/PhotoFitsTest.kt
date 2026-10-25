package dev.koolest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoFitsTest {
    private val w = 120
    private fun photo(f: (x: Int, y: Int) -> Int) = IntArray(w * 68) { f(it % w, it / w) }

    @Test fun flatOrBlurryIsRejected() {
        val fog = photo { x, _ -> 60 + x / 8 } // a smooth gradient: no detail
        assertFalse(photoFits(fog, w, dark = true))
        assertFalse(photoFits(fog, w, dark = false))
    }

    @Test fun brightnessMustSuitTheTheme() {
        val darkScene = photo { x, y -> if ((x / 3 + y / 3) % 2 == 0) 30 else 90 }
        val brightScene = photo { x, y -> if ((x / 3 + y / 3) % 2 == 0) 150 else 230 }
        assertTrue(photoFits(darkScene, w, dark = true))
        assertFalse(photoFits(darkScene, w, dark = false))
        assertTrue(photoFits(brightScene, w, dark = false))
        assertFalse(photoFits(brightScene, w, dark = true))
    }
}
