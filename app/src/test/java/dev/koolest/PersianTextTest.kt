package dev.koolest

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class PersianTextTest {
    /** Persian letters only: no hamza (ٔ أ إ ؤ ئ ء), no Arabic ي ك ة, no diacritics such as tanwin. */
    @Test fun onlyPersianLetters() {
        val persian = "آابپتثجچحخدذرزژسشصضطظعغفقکگلمنوهی" + "۰۱۲۳۴۵۶۷۸۹" + "،؛؟«»" + "‌"
        val text = File("src/main/res/values-fa/strings.xml").readText()
        val bad = text.filter { (it in '؀'..'ۿ' || it in 'ﭐ'..'﻿') && it !in persian }
            .toSet().joinToString(" ") { "U+%04X".format(it.code) }
        assertEquals("", bad)
    }
}
