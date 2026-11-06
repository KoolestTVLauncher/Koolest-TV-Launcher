package dev.koolest

/**
 * What a remote button does, by key id (see [RemoteKeys.id]). Buttons are learned from what the remote sends:
 * there are thousands of remotes, so nothing here knows any brand's keys.
 */
data class KeyMap(val id: Int, val action: String, val arg: String = "", val everywhere: Boolean = false, val name: String = "")

/** Plain Kotlin (no Android classes) so it's unit tested on the JVM. */
object RemoteKeys {
    const val APP = "app"
    const val MENU = "menu"
    const val ALL_APPS = "all_apps"
    const val HOME = "home"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val SCREENSAVER = "screensaver"
    const val ANDROID_SETTINGS = "android_settings"
    const val TV = "tv" // let the TV handle it
    const val NONE = "none" // swallow it
    val ACTIONS = setOf(APP, MENU, ALL_APPS, HOME, SEARCH, SETTINGS, SCREENSAVER, ANDROID_SETTINGS, TV, NONE)

    /**
     * The Home button service hands a Koolest action over in memory (same process), with only a flag in the intent:
     * Koolest's screen is exported, so other apps could send it any extra. A flag with nothing pending does nothing.
     */
    const val EXTRA = "remote_action"
    @Volatile var pending: KeyMap? = null

    /** The action the service just handed over, once. */
    fun takePending(): KeyMap? = pending.also { pending = null }

    /** Ids for keys Android has no key code for (KEYCODE_UNKNOWN): this plus the scan code. */
    const val UNKNOWN_BASE = 1_000_000
    /** Marks an id that holds a scan code too (see [id]): bit 30, so it can't meet plain or [UNKNOWN_BASE] ids. */
    const val SCAN_FLAG = 1 shl 30
    /** Linux KEY_UNKNOWN: every unmapped HID usage arrives with it, so such buttons can't be told apart. */
    const val GENERIC_SCAN = 240

    /**
     * Never remapped: moving around, OK, Back, Home, Power, Volume, Mute. A safety rule, not a remote list.
     * D-pad 19-23, Enter 66, Numpad Enter 160, Back 4, Home 3, Power 26, Volume 24/25, Volume mute 164, Mute 91.
     */
    private val CORE = setOf(19, 20, 21, 22, 23, 66, 160, 4, 3, 26, 24, 25, 164, 91)

    fun isCore(keyCode: Int) = keyCode in CORE

    /** 0-9 (7-16) and numpad 0-9 (144-153): people type these in other apps and the PIN pad. */
    fun isDigit(keyCode: Int) = keyCode in 7..16 || keyCode in 144..153

    /** The item menu only exists in Koolest, "as usual" changes nothing, and digits must stay typeable elsewhere. */
    fun canBeEverywhere(id: Int, action: String) = action != MENU && action != TV && !isDigit(keyCode(id))

    /** A button's first job works everywhere; after that, the person's own choice stays. */
    fun everywhereFor(old: KeyMap, action: String) = canBeEverywhere(old.id, action) &&
        (old.everywhere || old.action == TV || old.action == MENU)

    /**
     * A button's id: its key code and scan code together ([SCAN_FLAG] set), so two buttons that send the same key
     * code (Philips' Rakuten and Ambilight both send 249) are told apart. A plain key code is how buttons were
     * stored before, and stays for a key with no usable scan code; [UNKNOWN_BASE] + scan code for KEYCODE_UNKNOWN.
     */
    fun id(keyCode: Int, scanCode: Int) = when {
        keyCode == 0 -> UNKNOWN_BASE + scanCode
        keyCode in 1..0xFFF && scanCode in 1 until (1 shl 18) -> SCAN_FLAG or (scanCode shl 12) or keyCode
        else -> keyCode
    }

    /** Android's key code in an id (0 for KEYCODE_UNKNOWN ones). */
    fun keyCode(id: Int) = when {
        id and SCAN_FLAG != 0 -> id and 0xFFF
        id >= UNKNOWN_BASE -> 0
        else -> id
    }

    /** The scan code in an id, or 0 for a plain key code. */
    fun scanCode(id: Int) = when {
        id and SCAN_FLAG != 0 -> (id ushr 12) and 0x3FFFF
        id >= UNKNOWN_BASE -> id - UNKNOWN_BASE
        else -> 0
    }

    /**
     * The setting for a press: the exact button first, else one set up by key code alone (before scan codes were
     * kept). A press with a scan code never takes another button's setting: an unset Ambilight mustn't fire the set
     * Rakuten. A press without one (HDMI-CEC, some virtual remotes) can't be told apart, so any button with that key
     * code answers. KEYCODE_UNKNOWN keys only match exactly.
     */
    fun find(maps: List<KeyMap>, keyCode: Int, scanCode: Int): KeyMap? {
        val exact = id(keyCode, scanCode)
        return maps.firstOrNull { it.id == exact } ?: when {
            keyCode == 0 -> null
            exact == keyCode -> maps.firstOrNull { keyCode(it.id) == keyCode } // no scan code to go by
            else -> maps.firstOrNull { it.id == keyCode }
        }
    }

    /** A button set up by key code alone, learned again: its setting moves to the full id instead of showing twice. */
    fun adopt(maps: List<KeyMap>, newId: Int): List<KeyMap> {
        val k = keyCode(newId)
        if (newId == k || maps.any { it.id == newId } || maps.any { it.id != k && keyCode(it.id) == k }) return maps
        return maps.map { if (it.id == k) it.copy(id = newId) else it }
    }

    /** Runs on a clean release only; downs and repeats are swallowed, so holding a button fires it once. */
    fun fires(action: Int, repeatCount: Int, canceled: Boolean) = action == 1 && !canceled && repeatCount == 0

    /** "KEYCODE_PROG_RED" -> "Red"; null for codes Android has no name for (keyCodeToString gives digits). */
    fun readable(raw: String): String? {
        if (!raw.startsWith("KEYCODE_")) return null
        return raw.removePrefix("KEYCODE_").removePrefix("PROG_").lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun encode(maps: List<KeyMap>) = maps.joinToString("\n") {
        listOf(it.id.toString(), it.action, esc(it.arg), it.everywhere.toString(), esc(it.name)).joinToString("\t")
    }

    fun decode(s: String?): List<KeyMap> = s.orEmpty().split('\n').mapNotNull { line ->
        val f = line.split('\t')
        val id = f[0].toIntOrNull() ?: return@mapNotNull null
        val action = f.getOrNull(1)?.takeIf { it in ACTIONS } ?: return@mapNotNull null
        KeyMap(id, action, unesc(f.getOrElse(2) { "" }), f.getOrNull(3) == "true", unesc(f.getOrElse(4) { "" }))
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unesc(s: String) = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; else -> s[i + 1] })
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
