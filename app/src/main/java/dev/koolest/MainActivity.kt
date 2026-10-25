package dev.koolest

import android.animation.ObjectAnimator
import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.TransitionDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Calendar
import java.util.concurrent.Executors
import org.json.JSONObject

/** Light/dark from the launcher's theme setting; shared with the Gallery screensaver. */
internal fun prefersDark(prefs: SharedPreferences) = when (prefs.getString("theme", "light")) {
    "dark" -> true
    "auto" -> Calendar.getInstance().get(Calendar.HOUR_OF_DAY) !in 7..18
    else -> false
}

/**
 * An HDMI device's mark: the drawable, the tile colour it sits on (null: a plain tile), the mark's colour on it,
 * and whether it's a wordmark (fit by width).
 */
internal class DeviceLogo(val res: Int, val colour: Int? = null, val wordmark: Boolean = false, val ink: Int = Color.WHITE)

/**
 * The mark for an HDMI device, from the name it gives over CEC ("PlayStation 5", "NintendoSwitch", "SHIELD";
 * CEC names are at most 14 characters, so spaces are ignored). Most specific names first, so "Wii U" isn't a
 * Wii, "Xbox" isn't a set-top box and "Steam Deck" isn't Steam. A port with no named device ("HDMI 2") or a
 * device we don't know gets the HDMI mark. Logos: Simple Icons (CC0); generic marks: Material icons.
 */
internal fun deviceLogo(name: String): DeviceLogo {
    val n = name.lowercase().replace(" ", "")
    fun has(vararg keys: String) = keys.any { it in n }
    val ps = 0xFF0070D1.toInt() // PlayStation's brighter blue: the brand hex is too dark on dark cards
    val nintendo = 0xFFE60012.toInt()
    val steam = 0xFF171A21.toInt()
    return when {
        // Each in its own generation's colours: the white PS5, PlayStation blue for the PS4, the PS3's piano black.
        has("playstation5", "ps5") -> DeviceLogo(R.drawable.logo_dev_playstation5, 0xFFF4F4F6.toInt(), ink = 0xFF101014.toInt())
        has("playstation4", "ps4") -> DeviceLogo(R.drawable.logo_dev_playstation4, 0xFF003791.toInt())
        has("playstation3", "ps3") -> DeviceLogo(R.drawable.logo_dev_playstation3, 0xFF0B0B0D.toInt(), ink = 0xFFE4E4E8.toInt())
        has("vita") -> DeviceLogo(R.drawable.logo_dev_playstationvita, ps, wordmark = true)
        has("psp") -> DeviceLogo(R.drawable.logo_dev_playstationportable, ps, wordmark = true)
        has("playstation") -> DeviceLogo(R.drawable.logo_playstation, ps)
        has("xbox") -> DeviceLogo(R.drawable.logo_dev_xbox, 0xFF107C10.toInt())
        has("switch") -> DeviceLogo(R.drawable.logo_dev_nintendoswitch, nintendo)
        has("wiiu") -> DeviceLogo(R.drawable.logo_dev_wiiu, 0xFF009AC7.toInt(), wordmark = true)
        has("wii") -> DeviceLogo(R.drawable.logo_dev_wii, 0xFFF4F4F6.toInt(), wordmark = true, ink = 0xFF8A8A90.toInt())
        has("gamecube") -> DeviceLogo(R.drawable.logo_dev_nintendogamecube, 0xFF6A5FBB.toInt())
        has("3ds") -> DeviceLogo(R.drawable.logo_dev_nintendo3ds, nintendo)
        has("nintendo") -> DeviceLogo(R.drawable.logo_dev_nintendo, nintendo, wordmark = true)
        has("steamdeck") -> DeviceLogo(R.drawable.logo_dev_steamdeck, steam)
        has("steam") -> DeviceLogo(R.drawable.logo_dev_steam, steam)
        has("sega", "dreamcast", "genesis", "megadrive") -> DeviceLogo(R.drawable.logo_dev_sega, 0xFF0089CF.toInt())
        has("appletv") -> DeviceLogo(R.drawable.logo_dev_appletv, 0xFF000000.toInt())
        has("roku") -> DeviceLogo(R.drawable.logo_dev_roku, 0xFF8A3FD1.toInt(), wordmark = true)
        has("firetv", "amazon") -> DeviceLogo(R.drawable.logo_dev_amazonfiretv, 0xFFFF9900.toInt(), wordmark = true)
        has("chromecast", "googletv") -> DeviceLogo(R.drawable.logo_dev_chromecast, 0xFFF4F4F6.toInt(), ink = 0xFF3C4043.toInt())
        has("shield", "nvidia") -> DeviceLogo(R.drawable.logo_dev_nvidia, 0xFF76B900.toInt())
        has("sonos") -> DeviceLogo(R.drawable.logo_dev_sonos, 0xFF000000.toInt(), wordmark = true)
        has("soundbar", "speaker", "audio", "receiver", "avr", "bose", "denon", "yamaha", "onkyo", "marantz") ->
            DeviceLogo(R.drawable.logo_dev_speaker)
        has("bluray", "blu-ray", "bdp", "dvd", "disc") -> DeviceLogo(R.drawable.logo_dev_disc)
        has("cable", "settop", "stb", "tivo", "satellite", "dish", "directv", "xfinity", "box") -> DeviceLogo(R.drawable.logo_dev_settopbox)
        has("pc", "computer", "windows", "mac", "laptop") -> DeviceLogo(R.drawable.logo_dev_computer)
        else -> DeviceLogo(R.drawable.logo_dev_hdmi)
    }
}

/** "1.10" > "1.9" > "1.2.1" > "1.2": compared number by number, missing parts count as 0. */
internal fun newerVersion(a: String, b: String): Boolean {
    val x = a.split('.').map { it.toIntOrNull() ?: 0 }
    val y = b.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(x.size, y.size)) {
        val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
        if (d != 0) return d > 0
    }
    return false
}

/** "external" covers every attached volume (USB drives too) from Android 10; before that only the main one. */
internal val PHOTOS: Uri by lazy { // lazy: unit tests load this file without Android
    if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
}

/** Photo ids in a "folder:<bucket id>:<name>" choice. */
internal fun folderPhotos(cr: ContentResolver, choice: String): List<Long> =
    cr.query(PHOTOS, arrayOf(MediaStore.Images.Media._ID), "${MediaStore.Images.Media.BUCKET_ID} = ?", arrayOf(choice.split(':')[1]), null)
        ?.use { c -> List(c.count) { c.moveToNext(); c.getLong(0) } } ?: emptyList()

/**
 * Continue watching order: the apps you use most first (ties: the app with the newest show first), each with its
 * newest [perApp] shows, so one app with ten shows can't bury the rest.
 */
internal fun <T> byAppUse(items: List<Pair<Long, T>>, perApp: Int, uses: (String?) -> Int, app: (T) -> String?): List<T> =
    items.sortedByDescending { it.first }.groupBy { app(it.second) }.entries
        .sortedByDescending { uses(it.key) } // stable: equal use keeps newest-show order
        .flatMap { (_, shows) -> shows.take(perApp).map { it.second } }

/** Layout lock PIN, stored as "salt:sha256". Keeps it out of plain sight in a prefs backup; not real security. */
internal fun hashPin(pin: String, salt: String = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }) =
    salt + ":" + MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }

internal fun pinMatches(stored: String, pin: String) = hashPin(pin, stored.substringBefore(':')) == stored

/** Allowed to start an activity from the background because the Home button service is bound. */
class Updated : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        HomeOverride.revive(c) // the update unbound it
        c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

class MainActivity : Activity() {

    /** [banner] is the app's TV banner (16:9), what Android TV apps ship for exactly this kind of tile. */
    private class App(val pkg: String, val label: String, val launch: Intent, val icon: Drawable, val banner: Drawable? = null)
    private class Prog(val title: String, val sub: String, val progress: Int, val image: String?, val intent: String?, val pkg: String?, val plot: String = "")

    private val prefs by lazy { getSharedPreferences("home", MODE_PRIVATE) }
    private val io = Executors.newSingleThreadExecutor()
    // Posters get their own threads: one slow image server must not hold up the rest (or the row data).
    private val posterIo = Executors.newFixedThreadPool(3)
    private val loading = HashMap<String, MutableList<ImageView>>() // posters on their way, and every card waiting for each (UI thread)
    private val ui = Handler(Looper.getMainLooper())
    private val posters = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    // Kept across activity rebuilds (theme switch), so the new home screen shows its apps at once.
    private var apps = APPS ?: emptyList()
    private var appsDirty = APPS == null
    private var editing = false
    private var grabbed: View? = null
    private var holdFired = false
    private var dark = false
    private var fg = Color.BLACK
    private var ground = Color.WHITE
    private var cardRest = intArrayOf(Color.WHITE, Color.WHITE)
    private var cardLit = cardRest
    private var focusFill = Color.WHITE // 30nama: focus is white (dark ink on it); in light mode it turns dark
    private var focusInk = Color.BLACK

    private lateinit var rows: LinearLayout // the home rows, top to bottom; see makeRow
    private lateinit var rowStack: View
    private lateinit var appRow: LinearLayout // each row is known by its strip of items
    private lateinit var watchRow: LinearLayout
    private lateinit var consoleRow: LinearLayout
    private val cardRows = LinkedHashMap<String, LinearLayout>() // card rows by key (see queryContent), app rows added as found
    private val rowKeys = HashMap<View, String>() // what each card row shows, to skip unchanged rebuilds
    private var navigated = false // the remote has moved focus since launch: stop moving it for them
    private val appsTitle by lazy { findViewById<TextView>(R.id.appsTitle) }
    private val hero by lazy { findViewById<ImageView>(R.id.hero) }
    /** Everything behind All apps, which is opaque: not drawn while it's open. */
    private val homeLayers by lazy { listOf(R.id.wallpaper, R.id.hero, R.id.stageScrim, R.id.content).map { findViewById<View>(it) } }
    private val shelf by lazy { findViewById<View>(R.id.shelf) }
    private var shelfFor: View? = null
    private val railItems by lazy { findViewById<LinearLayout>(R.id.railItems) }
    private val railLabels = ArrayList<TextView>()
    private var railOpen = false
    private var lastRowItem: View? = null // where focus last was in the rows: the menu and top bar return to it
    private var lastTop: View? = null // last focused top-end button
    private var railKey: String? = null // the pinned apps the menu was built with
    private lateinit var allApps: View
    private lateinit var allAppsGrid: GridLayout
    private lateinit var wifi: ImageView

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            appsDirty = true
            i.data?.schemeSpecificPart?.let { GLYPHS.remove(it); ACCENTS.remove(it) } // an update may bring a new icon
        }
    }

    private val net = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) {
            ui.post {
                showNet(caps)
                // Online again (e.g. Wi-Fi coming up after boot): fetch any posters that failed meanwhile.
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) retryPosters()
            }
        }

        override fun onLost(n: Network) {
            ui.post { showNet(null) }
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        allApps = findViewById(R.id.allApps)
        allAppsGrid = findViewById(R.id.allAppsGrid)
        applyTheme()
        loadWallpaper()
        rows = findViewById(R.id.rows)
        rowStack = findViewById(R.id.rowStack)
        // Top to bottom; rows for other apps that publish to the home screen follow Spotify as they're found.
        watchRow = makeRow("Continue watching")
        appRow = makeRow("Apps").apply { id = R.id.appRow }
        consoleRow = makeRow("Consoles & inputs")
        cardRows["watch"] = watchRow
        cardRows["nama"] = makeRow("30nama")
        cardRows["youtube"] = makeRow("YouTube")
        cardRows["spotify"] = makeRow("Spotify")
        // Room under the last row so it too can scroll up to the top line, like every other row.
        rowStack.post { rows.setPadding(0, 0, 0, rowStack.height) }
        wifi = findViewById(R.id.wifi)
        buildRail()
        // Top end: network, Android settings, Koolest settings. Round, lit on focus.
        for ((id, action) in listOf<Pair<Int, () -> Unit>>(
            R.id.wifi to { openSettings(Settings.ACTION_WIFI_SETTINGS) },
            R.id.systemSettings to { openSettings(Settings.ACTION_SETTINGS) },
            R.id.launcherSettings to { unlock { openLauncherSettings() } },
        )) findViewById<ImageView>(id).apply {
            setColorFilter(fg)
            val ring = GradientDrawable().apply { shape = GradientDrawable.OVAL }
            background = ring
            setOnFocusChangeListener { v, focused ->
                if (focused) lastTop = v
                ring.setColor(if (focused) focusFill else 0)
                setColorFilter(if (focused) focusInk else fg) // no grow: the circle already fills its slot in the capsule
            }
            setOnClickListener { action() }
        }
        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { old, now -> onHomeFocus(old, now) }

        if (prefs.getBoolean("reopen_settings", false)) {
            prefs.edit().remove("reopen_settings").apply()
            ui.post { openLauncherSettings(focus = 1) }
        } else if (!homeServiceOn() && !prefs.getBoolean("setup_shown", false)) {
            // Fresh install: open settings on the Home button row, the one step that has to happen by hand.
            prefs.edit().putBoolean("setup_shown", true).apply()
            ui.post { openLauncherSettings(focus = HOME_BUTTON_ROW) }
        }

        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        // Application context: ConnectivityManager keeps its first instance (and that instance's context) in a
        // static callback handler forever, so an Activity context here leaks the whole first home screen.
        applicationContext.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(net)
        getSystemService(TvInputManager::class.java)?.registerCallback(inputCallback, ui)

        if (checkSelfPermission(TV_LISTINGS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(TV_LISTINGS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        ensureHomeService()
        if (wantDark() != dark) {
            recreate()
            return
        }
        // Only rebuild what changed, so focus stays where it was when returning from an app.
        if (appsDirty) refreshApps() else if (appRow.childCount == 0) renderApps()
        ui.removeCallbacks(inputPoll)
        ui.post(inputPoll)
        refreshWatchNext()
        showWeather()
        ui.removeCallbacks(weatherTick)
        ui.post(weatherTick)
        ui.postDelayed(themeTick, 60_000L)
        ui.removeCallbacks(wallpaperTick)
        ui.post(wallpaperTick)
        // Gallery chosen but its paintings missing (updated from a version that bundled them): fetch them.
        if (galleryOn() && photoScreensaver() == null && !paintingsReady(this)) downloadPaintings {}
        checkUpdate(force = false)
    }

    /** Auto theme flips at 7:00 and 19:00 even while the home screen just sits there. */
    private val themeTick = object : Runnable {
        override fun run() {
            if (wantDark() != dark) recreate() else ui.postDelayed(this, 60_000L)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        if (code != MEDIA_REQUEST) return refreshWatchNext()
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) afterPhotoAccess?.invoke()
        else Toast.makeText(this, "Koolest needs access to photos to show your folders", Toast.LENGTH_LONG).show()
    }

    @Deprecated("Only used for the default-home role request")
    override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        if (code == ROLE_REQUEST && settingsScrim.visibility == View.VISIBLE) renderSettings(DEFAULT_HOME_ROW)
    }

    override fun onPause() {
        ui.removeCallbacks(inputPoll)
        ui.removeCallbacks(weatherTick)
        ui.removeCallbacks(themeTick)
        ui.removeCallbacks(wallpaperTick)
        if (editing) endEdit()
        super.onPause()
    }

    override fun onStop() {
        unlockedUntil = 0L // leaving the home screen locks it again (onPause would also fire for permission dialogs)
        // "Add from phone" serves the network only while someone is looking at it: leaving closes it.
        if (upload != null) closeLauncherSettings()
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        posters.evictAll()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        applicationContext.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(net)
        getSystemService(TvInputManager::class.java)?.unregisterCallback(inputCallback)
        upload?.stop()
        io.shutdownNow()
        posterIo.shutdownNow()
        super.onDestroy()
    }

    /**
     * Keeps the Home service bound (see [HomeOverride.revive]): Android drops it when the app is force-stopped and
     * doesn't rebind it after the process dies. Needs the adb-granted permission; respects the user turning it off.
     */
    private fun ensureHomeService() {
        if (homeServiceOn()) prefs.edit().remove("home_off").apply() // what Android says wins
        // Once, from 1.3: it saved "off" whenever Android unbound the service (a force-stop does), not only from the switch.
        if (!prefs.getBoolean("home_off_fixed", false)) prefs.edit().remove("home_off").putBoolean("home_off_fixed", true).apply()
        HomeOverride.keepAlive(this)
        ui.postDelayed({ HomeOverride.revive(this) }, 1500) // a process just started may still be binding it
    }

    private fun homeServiceOn() = HomeOverride.listed(this)

    private fun toggleHomeButton() = if (homeServiceOn()) turnOffHomeButton() else turnOnHomeButton()

    /** Off hands Home back to the TV's own launcher, and Koolest leaves it off until switched on again. */
    private fun turnOffHomeButton() {
        prefs.edit().putBoolean("home_off", true).apply()
        HomeOverride.running?.disableSelf()
        if (homeServiceOn() && checkSelfPermission(WRITE_SECURE) == PackageManager.PERMISSION_GRANTED) {
            val me = ComponentName(this, HomeOverride::class.java)
            val on = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            Settings.Secure.putString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                on.split(':').filter { ComponentName.unflattenFromString(it) != me }.joinToString(":"))
        }
        if (homeServiceOn()) {
            Toast.makeText(this, "Turn off \"Koolest: Home button\" in Settings → System → Accessibility", Toast.LENGTH_LONG).show()
            openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        renderSettings(HOME_BUTTON_ROW)
    }

    /** The Home key service is an accessibility service, which Android only lets the user switch on. */
    private fun turnOnHomeButton() {
        prefs.edit().remove("home_off").apply()
        if (checkSelfPermission(WRITE_SECURE) == PackageManager.PERMISSION_GRANTED) { // the adb permission: switch it on ourselves
            return HomeOverride.revive(this) { ui.post { renderSettings(HOME_BUTTON_ROW) } }
        }
        // The written path is the safety net: some Google TVs (a Xiaomi stick) answer the Accessibility link
        // with "You don't have an app that can do this", which we can't detect from here.
        Toast.makeText(this, "Turn on \"Koolest: Home button\" in Settings → System → Accessibility", Toast.LENGTH_LONG).show()
        val service = ComponentName(this, HomeOverride::class.java).flattenToString()
        val tries = listOf(
            // Straight to our own toggle (Android 12+, where the TV supports it), then the list, then Settings.
            Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME, service),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in tries) {
            try {
                return startActivity(intent) // not resolveActivity(): Android 11+ hides other apps from that
            } catch (e: Exception) {
            }
        }
    }

    // ---------- adb shortcuts (sideload build only, see SelfAdb) ----------
    // The README's adb lines, run through the TV's own network debugging so nobody needs a computer.

    /** Grants what "Recommended" in the README grants: the Home service then switches itself back on. */
    private fun adbSetup() = if (!SelfAdb.AVAILABLE) Unit else viaAdb(ADB_SETUP_ROW, grants()) { // the play build compiles none of this
        if (checkSelfPermission(WRITE_SECURE) != PackageManager.PERMISSION_GRANTED) return@viaAdb "Setup didn't finish. Try again."
        HomeOverride.revive(this)
        null
    }

    private fun grants() = listOf("pm grant $packageName $WRITE_SECURE", "appops set $packageName APP_AUTO_START allow") // the second is TCL's; others ignore it

    /** Every other home screen on the TV (Google TV's and its setup screen), not Android's fallback. */
    private fun otherHomes() = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
        .map { it.activityInfo.packageName }.filter { it != packageName && it != "com.android.tv.settings" }.toSet()

    private fun homesOff() = prefs.getStringSet("homes_off", emptySet())!!

    private fun onlyLauncher() = homesOff().isNotEmpty() && homesOff().all { pkg ->
        try {
            packageManager.getApplicationEnabledSetting(pkg) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        } catch (e: IllegalArgumentException) {
            true // uninstalled since
        }
    }

    /** On switches the other home screens off (and does the setup too); off turns back on the ones Koolest switched off. */
    private fun toggleOnlyLauncher() {
        if (!SelfAdb.AVAILABLE) return
        if (onlyLauncher()) {
            val homes = homesOff()
            return viaAdb(ONLY_LAUNCHER_ROW, homes.map { "pm enable $it" }) {
                prefs.edit().remove("homes_off").apply()
                null
            }
        }
        val homes = otherHomes() + homesOff()
        if (homes.isEmpty()) return Toast.makeText(this, "Koolest is already the only launcher", Toast.LENGTH_SHORT).show()
        prefs.edit().putStringSet("homes_off", homes).apply()
        viaAdb(ONLY_LAUNCHER_ROW, grants() + homes.map { "pm disable-user --user 0 $it" }) {
            if (!onlyLauncher()) "The TV didn't switch its launcher off. Try again." else {
                HomeOverride.revive(this)
                null
            }
        }
    }

    /**
     * Runs [commands] through the TV's adb, then [check] on the UI thread: null when it worked, or what to say.
     * adb off: a page that walks through switching on network debugging.
     */
    private fun viaAdb(row: Int, commands: List<String>, check: () -> String?) {
        Toast.makeText(this, "If the TV asks, tick \"Always allow\" and choose Allow", Toast.LENGTH_LONG).show()
        SelfAdb.run(this, commands) { result ->
            result.onSuccess { android.util.Log.i("Koolest", "adb: $it") }.onFailure { android.util.Log.w("Koolest", "adb failed ($it)") }
            ui.post {
                val error = result.exceptionOrNull()
                when {
                    error is java.net.ConnectException -> return@post adbHelp(row, commands, check)
                    error != null -> Toast.makeText(this, "The TV didn't allow it. Try again and choose Allow.", Toast.LENGTH_LONG).show()
                    else -> Toast.makeText(this, check() ?: "Done", Toast.LENGTH_LONG).show()
                }
                if (settingsScrim.visibility == View.VISIBLE) renderSettings(row)
            }
        }
    }

    private fun adbHelp(row: Int, commands: List<String>, check: () -> String?) {
        val steps = label("Koolest does this through the TV's network debugging, which is off.\n\n" +
            "1. Settings → System → About: press OK on \"Android TV OS build\" 7 times.\n" +
            "2. Settings → System → Developer options: turn on USB debugging, and Network debugging if it's listed.\n" +
            "3. Come back and choose Try again.\n\n" +
            "You can switch debugging off again afterwards; the change stays.", 13f).apply {
            maxLines = 20
            alpha = 0.8f
            setPadding(dp(14), dp(8), dp(14), dp(12))
        }
        showPage("Turn on debugging", listOf(
            page("Open Developer options") { openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) },
            page("Try again") { viaAdb(row, commands, check) },
        ), top = steps, back = { renderSettings(row) })
    }

    private fun isDefaultHome() = Build.VERSION.SDK_INT >= 29 &&
        getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)

    /**
     * Asks Android to make us the default home app (its own confirmation dialog). On Google TV the system
     * launcher still wins Home intents, which is why the Home button service exists; on other Android TVs
     * this alone is enough.
     */
    private fun makeDefaultHome() {
        val roles = if (Build.VERSION.SDK_INT >= 29) getSystemService(RoleManager::class.java) else null
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            @Suppress("DEPRECATION")
            startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), ROLE_REQUEST)
        } else {
            openSettings(Settings.ACTION_HOME_SETTINGS) // Android lets apps ask for Home, not give it back
        }
    }

    /** Home pressed while already home: close whatever is open and go back to the start of the apps row. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == UPDATE_STATUS) return installStatus(intent)
        if (editing) endEdit()
        if (settingsScrim.visibility == View.VISIBLE) closeLauncherSettings()
        if (allApps.visibility == View.VISIBLE) closeAllApps()
        firstRowEntry()?.requestFocus()
    }

    @Deprecated("Home screen: Back only closes overlays")
    override fun onBackPressed() {
        when {
            settingsScrim.visibility == View.VISIBLE -> pageBack?.invoke() ?: closeLauncherSettings()
            allApps.visibility == View.VISIBLE -> closeAllApps()
            railOpen -> leaveRail()
            else -> currentFocus?.let(::backFrom)
        }
    }

    /** Back on the home screen: deep in a row, to its first item; from a first item, to the menu; from the top buttons, back to the rows. */
    private fun backFrom(v: View) {
        if (v.parent === wifi.parent) return leaveRail()
        if (rowOf(v) == null) return
        val item = itemOf(v)
        val strip = item.parent as ViewGroup
        if (strip.indexOfChild(item) > 0) focusTarget(strip.getChildAt(0))?.requestFocus()
        else findViewById<View>(R.id.railHome).requestFocus()
    }

    /** A row item's focusable view: a tile itself, or a card's picture inside its column. */
    private fun focusTarget(x: View?) = x?.let { if (it.isFocusable) it else (it as ViewGroup).getChildAt(0) }

    // ---------- theme ----------

    private fun wantDark() = prefersDark(prefs)

    private fun applyTheme() {
        dark = wantDark()
        // 30nama style guide tokens: warm white / ink text, surface cards, and focus that is a solid fill.
        fg = if (dark) 0xFFF4F1EA.toInt() else 0xFF14161C.toInt()
        focusFill = if (dark) Color.WHITE else 0xFF16181E.toInt()
        focusInk = if (dark) 0xFF0B0C10.toInt() else Color.WHITE
        cardRest = if (dark) intArrayOf(0xFF161922.toInt(), 0xFF161922.toInt()) else intArrayOf(0xD9F2F0EB.toInt(), 0xD9E5E2DC.toInt())
        cardLit = intArrayOf(focusFill, focusFill)
        // Must match the colour each wallpaper fades into, or the bottom edge shows a seam
        // (the dark one ends at ~#0F1014, which is 30nama's bg-1).
        ground = if (dark) 0xFF0E1016.toInt() else 0xFFF3F5F7.toInt()
        // The ground colour is the window's background, not the root's too (one full-screen layer instead of two).
        // Keep the window's: without one, Android composites the screen the slow way and frames drop further.
        window.setBackgroundDrawable(ColorDrawable(ground))
        findViewById<ImageView>(R.id.wallpaper).let { if (it.drawable == null) it.setImageDrawable(ColorDrawable(ground)) }
        allApps.setBackgroundColor(ground)
        // The top bar's capsule: smoky grey glass with a light hairline edge in dark, frosted white in light.
        findViewById<View>(R.id.topBar).background = glass(dp(22), if (dark) 0x8C4A4D57.toInt() else 0xE6FFFFFF.toInt())
        for (id in intArrayOf(R.id.infoDivider, R.id.iconDivider)) findViewById<View>(id).setBackgroundColor(fg)
        for (id in intArrayOf(R.id.clock, R.id.date, R.id.wxTemp, R.id.wxDesc, R.id.appsTitle, R.id.allAppsTitle,
            R.id.shelfTitle, R.id.shelfMeta, R.id.shelfPlot)) {
            findViewById<TextView>(id).setTextColor(fg)
        }
        findViewById<View>(R.id.stageScrim).background = stageScrims(ground)
        // The menu: clear frosted glass (a faint white veil) round the icons at rest; open, a card of the same height,
        // a little denser so the labels read, over the dimmed screen.
        findViewById<View>(R.id.railRest).background = glass(dp(26), if (dark) 0x14FFFFFF else 0x80FFFFFF.toInt())
        findViewById<View>(R.id.railPanel).background = glass(dp(26), if (dark) 0xB333363E.toInt() else 0xCCFFFFFF.toInt())
        findViewById<View>(R.id.railDim).setBackgroundColor(ground)
    }

    /** A rounded glass panel: the fill with a hairline edge (light on dark, dark on light). */
    private fun glass(radius: Int, fill: Int) = GradientDrawable().apply {
        cornerRadius = radius.toFloat()
        setColor(fill)
        setStroke(dp(1), if (dark) 0x33FFFFFF else 0x14000000)
    }

    /**
     * 30nama's stage scrims, of the ground colour: from the start edge 96 % → 78 % (30 %) → 0 (68 %), up from the
     * bottom 100 % → 85 % (32 %) → 0 (62 %), and 70 % → 0 over the top 22 % for the clock and weather. Exact stops need
     * Android 10; before it they're spread evenly.
     */
    private fun stageScrims(ground: Int): Drawable {
        fun a(alpha: Float) = (ground and 0xFFFFFF) or ((alpha * 255).toInt() shl 24)
        fun scrim(o: GradientDrawable.Orientation, stops: FloatArray, alphas: FloatArray) = GradientDrawable(o, alphas.map { a(it) }.toIntArray()).apply {
            if (Build.VERSION.SDK_INT >= 29) setColors(alphas.map { a(it) }.toIntArray(), stops)
        }
        // Each covers only the part of the screen where it isn't clear (its stops rescaled to that part): blending
        // three full-screen layers every frame was a good share of what made the TV's GPU drop frames.
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        return LayerDrawable(arrayOf(
            scrim(GradientDrawable.Orientation.LEFT_RIGHT, floatArrayOf(0f, .30f / .68f, 1f), floatArrayOf(.96f, .78f, 0f)),
            scrim(GradientDrawable.Orientation.BOTTOM_TOP, floatArrayOf(0f, .32f / .62f, 1f), floatArrayOf(1f, .85f, 0f)),
            scrim(GradientDrawable.Orientation.TOP_BOTTOM, floatArrayOf(0f, 1f), floatArrayOf(.5f, 0f)),
        )).apply {
            setLayerGravity(0, Gravity.START or Gravity.FILL_VERTICAL)
            setLayerWidth(0, (w * .68f).toInt())
            setLayerGravity(1, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL)
            setLayerHeight(1, (h * .62f).toInt())
            setLayerGravity(2, Gravity.TOP or Gravity.FILL_HORIZONTAL)
            setLayerHeight(2, (h * .22f).toInt())
        }
    }

    // ---------- wallpapers ----------
    // Bundled ones have their fade baked in. Your own go in Android/data/dev.koolest/files/wallpapers/
    // (adb push, no permission needed) and get the fade painted once when loaded. A folder on the TV (MediaStore,
    // so USB drives too) shows its photos in shuffled turns, one decoded at a time.
    // Each theme remembers its own pick. Decoded off the UI thread, max 1080p (~8 MB). Full colour, not RGB_565:
    // 565 rounds a smooth sky's 1-2 level JPEG ripples into visible grey and green blocks.

    private fun wallpaperDir() = getExternalFilesDir("wallpapers")

    /** A plain choice first (just the theme's base colour, no image), then pictures. */
    private fun wallpaperChoices(forDark: Boolean): List<Pair<String, String>> =
        listOf(plainWallpaper(forDark).let { wallpaperName(it) to it }) +
            BUNDLED_WALLPAPERS.keys.map { it to "res:$it" } +
            (wallpaperDir()?.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
                ?.sortedBy { it.name }?.map { it.nameWithoutExtension to "file:${it.name}" } ?: emptyList()) +
            listOf(wallpaperName(UNSPLASH) to UNSPLASH) +
            listOfNotNull(currentWallpaper(forDark).takeIf { it.startsWith("folder:") }?.let { wallpaperName(it) to it })

    // Light gets a soft white (the light ground colour), not #FFFFFF: pure white glares on a TV.
    private fun plainWallpaper(forDark: Boolean) = if (forDark) "plain:Pure black" else "plain:Soft white"

    private fun defaultWallpaper(forDark: Boolean) = if (forDark) "res:Rockies (dusk)" else "res:Rockies (day)"

    private fun currentWallpaper(forDark: Boolean = dark) =
        prefs.getString(if (forDark) "wp_dark" else "wp_light", null) ?: defaultWallpaper(forDark)

    /** Fade your own photos into the background at the bottom. Bundled wallpapers have it baked in either way. */
    private fun wallpaperFade() = prefs.getBoolean("wp_fade", true)

    private fun wallpaperName(choice: String) =
        if (choice.startsWith("folder:")) "Folder: " + choice.substringAfter(':').substringAfter(':')
        else choice.substringAfter(':').substringBeforeLast('.')

    private fun loadWallpaper() {
        val view = findViewById<ImageView>(R.id.wallpaper)
        val choice = currentWallpaper()
        if (choice.startsWith("plain:")) return view.setImageDrawable(ColorDrawable(ground))
        val fallback = defaultWallpaper(dark)
        io.execute {
            val bmp = try {
                decodeWallpaper(choice)
            } catch (e: Exception) {
                null
            } ?: decodeWallpaper(fallback)
            ui.post {
                val old = view.drawable
                if (bmp == null) return@post view.setImageDrawable(ColorDrawable(ground))
                if (old == null) return@post view.setImageBitmap(bmp)
                // Soft change from one photo to the next; then drop the old one so only one stays in memory.
                val fade = TransitionDrawable(arrayOf(old, BitmapDrawable(resources, bmp))).apply { isCrossFadeEnabled = true }
                view.setImageDrawable(fade)
                fade.startTransition(800)
                ui.postDelayed({ if (view.drawable === fade) view.setImageBitmap(bmp) }, 900)
            }
        }
    }

    private fun decodeWallpaper(choice: String): Bitmap? {
        BUNDLED_WALLPAPERS[choice.removePrefix("res:")]?.let { res ->
            return BitmapFactory.decodeResource(resources, res)
        }
        // fitWallpaper's bitmap is mutable: the fades are painted onto it.
        val bmp = when {
            choice.startsWith("folder:") -> {
                val uri = ContentUris.withAppendedId(PHOTOS, folderPhoto(choice) ?: return null)
                fitWallpaper { contentResolver.openInputStream(uri) ?: throw IOException("gone: $uri") }
            }
            choice == UNSPLASH -> {
                val file = onlinePhoto() ?: return null
                fitWallpaper { file.inputStream() }
            }
            else -> {
                val file = File(wallpaperDir(), choice.removePrefix("file:"))
                fitWallpaper { file.inputStream() }
            }
        } ?: return null
        // Same fade as the bundled ones: clear above 28% height, into the background colour by 50%.
        val h = bmp.height.toFloat()
        val base = ground and 0x00FFFFFF
        if (wallpaperFade()) Canvas(bmp).drawRect(0f, 0f, bmp.width.toFloat(), h, Paint().apply {
            shader = LinearGradient(0f, h * 0.28f, 0f, h * 0.5f, base, base or 0xE6000000.toInt(), Shader.TileMode.CLAMP)
        })
        // Photos rarely have the bundled ones' empty sky: a soft veil behind the clock and top-bar icons keeps them readable.
        // Strong over the clock and icons (top ~20%), gone by 30% so the photo still shows above the apps.
        Canvas(bmp).drawRect(0f, 0f, bmp.width.toFloat(), h * 0.3f, Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h * 0.3f, intArrayOf(base or 0xC0000000.toInt(), base or 0xA0000000.toInt(), base),
                floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        })
        return bmp
    }

    /** Wallpapers page, framed by "Add from phone…" first and, once there are photos, "Remove a photo…" last. */
    private fun pickWallpaper(forDark: Boolean) {
        val row = if (forDark) 3 else 2
        val choices = wallpaperChoices(forDark)
        val own = choices.filter { it.second.startsWith("file:") }
        val current = currentWallpaper(forDark)
        val rows = listOf(page(ADD_FROM_PHONE) { uploadWallpaper(forDark) }, page(CHOOSE_FROM_TV) { pickFolder(forDark) }) +
            choices.map { (name, choice) -> page(name, if (choice == current) CHOSEN else "") { setWallpaper(forDark, choice); renderSettings(row) } } +
            if (own.isEmpty()) emptyList() else listOf(page(REMOVE_PHOTO) { removeWallpaper(own, forDark) })
        showPage(if (forDark) "Dark mode wallpaper" else "Light mode wallpaper", rows, 2 + choices.indexOfFirst { it.second == current }) {
            renderSettings(row)
        }
    }

    private fun setWallpaper(forDark: Boolean, choice: String) {
        prefs.edit().putString(if (forDark) "wp_dark" else "wp_light", choice)
            .remove(photoKey(forDark)).putLong(photoAtKey(forDark), System.currentTimeMillis()).apply()
        if (forDark == dark) loadWallpaper()
        ui.removeCallbacks(wallpaperTick) // a changing wallpaper starts its clock now, not on the next visit home
        ui.post(wallpaperTick)
    }

    /**
     * The online photo on show, downloaded again when the photo is due to change (photoKey cleared). Offline, the
     * last one stays. Runs on the io thread.
     * shortcut: Lorem Picsum's random Unsplash photos (no key needed, any subject); with an Unsplash API key,
     * api.unsplash.com/photos/random?query=landscape would let it pick a theme.
     */
    private fun onlinePhoto(): File? {
        val file = File(filesDir, "online_wallpaper.jpg")
        if (!prefs.contains(photoKey()) || !file.exists()) try {
            val m = resources.displayMetrics
            val tmp = File(filesDir, "online_wallpaper.part")
            // Random photos can be anything: try a few until one suits the theme (see photoFits). If none does,
            // keep the one on show; with none on show yet, take the last try.
            for (attempt in 1..PHOTO_TRIES) {
                openStream("https://picsum.photos/${m.widthPixels}/${m.heightPixels}").use { input -> tmp.outputStream().use { input.copyTo(it) } }
                val small = BitmapFactory.decodeFile(tmp.path, BitmapFactory.Options().apply { inSampleSize = 8 })
                    ?: throw IOException("not an image") // e.g. a captive-portal page
                val grey = Bitmap.createScaledBitmap(small, 120, 68, true).let { b ->
                    IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
                        .map { (Color.red(it) * 299 + Color.green(it) * 587 + Color.blue(it) * 114) / 1000 }.toIntArray()
                        .also { if (b !== small) b.recycle() }
                }
                small.recycle()
                if (photoFits(grey, 120, dark) || (attempt == PHOTO_TRIES && !file.exists())) {
                    tmp.renameTo(file)
                    break
                }
            }
            tmp.delete()
            prefs.edit().putLong(photoKey(), System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            android.util.Log.w("Koolest", "online wallpaper: $e") // keep the last one
        }
        return file.takeIf { it.exists() }
    }

    // ---------- folder wallpapers ----------

    private var afterPhotoAccess: (() -> Unit)? = null // what asked, while Android's permission prompt is up
    private val deck = ArrayDeque<Long>() // photos not shown yet this round; io thread only

    private fun photoKey(forDark: Boolean = dark) = if (forDark) "wp_photo_dark" else "wp_photo_light"
    private fun photoAtKey(forDark: Boolean = dark) = if (forDark) "wp_photo_at_dark" else "wp_photo_at_light"
    private fun photoEvery() = prefs.getInt("wp_every", 30)

    private fun pickPhotoEvery() {
        val rows = PHOTO_EVERY.map { (minutes, name) ->
            page(name, if (minutes == photoEvery()) CHOSEN else "") {
                prefs.edit().putInt("wp_every", minutes).apply()
                ui.removeCallbacks(wallpaperTick)
                ui.post(wallpaperTick)
                renderSettings(5)
            }
        }
        showPage("Folder photo changes", rows, PHOTO_EVERY.keys.indexOf(photoEvery())) { renderSettings(5) }
    }

    /** While the home screen shows, a folder wallpaper moves on to its next photo when its time is up. */
    private val wallpaperTick = object : Runnable {
        override fun run() {
            val every = photoEvery() * 60_000L
            if (!currentWallpaper().startsWith("folder:") && currentWallpaper() != UNSPLASH) return
            val wait = prefs.getLong(photoAtKey(), 0) + every - System.currentTimeMillis()
            if (wait <= 0) nextPhoto()
            ui.postDelayed(this, if (wait <= 0) every else wait)
        }
    }

    private fun nextPhoto() {
        prefs.edit().remove(photoKey()).putLong(photoAtKey(), System.currentTimeMillis()).apply()
        loadWallpaper()
    }

    /** The folder's photo on show now, or the next one in this round's shuffle. Runs on the io thread. */
    private fun folderPhoto(choice: String): Long? {
        val ids = folderPhotos(contentResolver, choice)
        val all = ids.toHashSet()
        prefs.getLong(photoKey(), -1).takeIf { it in all }?.let { return it }
        deck.removeAll { it !in all } // deleted, or another folder's
        if (deck.isEmpty()) deck.addAll(ids.shuffled())
        return deck.removeFirstOrNull()?.also { prefs.edit().putLong(photoKey(), it).apply() }
    }

    /** Every folder with photos in it, internal storage and USB drives alike: (bucket id, name, photo count). */
    private fun photoFolders(): List<Triple<String, String, Int>> {
        val found = LinkedHashMap<String, Pair<String, Int>>()
        contentResolver.query(PHOTOS, arrayOf(MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
            null, null, "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} COLLATE NOCASE")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                found[id] = (c.getString(1) ?: "Photos") to (found[id]?.second ?: 0) + 1
            }
        }
        return found.map { (id, v) -> Triple(id, v.first, v.second) }
    }

    private fun pickFolder(forDark: Boolean) = pickFolder({ pickWallpaper(forDark) }) { choice ->
        setWallpaper(forDark, choice)
        renderSettings(if (forDark) 3 else 2)
    }

    /** Folders page for wallpapers and the screensaver; [onPick] gets "folder:<bucket id>:<name>". */
    private fun pickFolder(back: () -> Unit, onPick: (String) -> Unit) {
        if (checkSelfPermission(READ_PHOTOS) != PackageManager.PERMISSION_GRANTED) {
            afterPhotoAccess = { pickFolder(back, onPick) }
            return requestPermissions(arrayOf(READ_PHOTOS), MEDIA_REQUEST)
        }
        val folders = photoFolders()
        if (folders.isEmpty()) {
            return Toast.makeText(this, "No photos found. Copy some to the TV or plug in a USB drive with photos.", Toast.LENGTH_LONG).show()
        }
        val rows = folders.map { (id, name, n) ->
            page(name, "$n ${if (n == 1) "photo" else "photos"}") { onPick("folder:$id:$name") }
        }
        showPage("Choose a folder", rows, back = back)
    }

    private fun removeWallpaper(own: List<Pair<String, String>>, forDark: Boolean) {
        showPage("Remove a photo", own.map { (name, choice) ->
            page(name) {
                showPage("Delete \"$name\"?", listOf(
                    page("Cancel") { removeWallpaper(own, forDark) },
                    page("Delete") {
                        File(wallpaperDir(), choice.removePrefix("file:")).delete()
                        // A theme showing it falls back to its default.
                        prefs.edit().apply {
                            if (prefs.getString("wp_light", null) == choice) remove("wp_light")
                            if (prefs.getString("wp_dark", null) == choice) remove("wp_dark")
                        }.apply()
                        loadWallpaper()
                        pickWallpaper(forDark)
                    },
                )) { removeWallpaper(own, forDark) }
            }
        }) { pickWallpaper(forDark) }
    }

    private var upload: UploadServer? = null

    /**
     * "Add from phone": a QR code for a page served from this TV. The phone posts a photo, we turn it into a
     * wallpaper and show it at once. The server runs only while this panel is open.
     */
    private fun uploadWallpaper(forDark: Boolean) {
        val ip = lanAddress() ?: return Toast.makeText(this, "Connect the TV to Wi-Fi or Ethernet first", Toast.LENGTH_LONG).show()
        val dir = wallpaperDir() ?: return
        val status = label("Waiting for a photo…", 14f).apply { gravity = Gravity.CENTER; maxLines = 2 }
        val server = try {
            // 64 random bits: unguessable by others on the Wi-Fi, and only ever scanned, never typed.
            UploadServer(ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }, File(cacheDir, "upload.tmp")) { file ->
                val saved = importWallpaper(file, dir) ?: return@UploadServer null
                ui.post {
                    setWallpaper(forDark, "file:${saved.name}")
                    status.text = "Added \"${saved.nameWithoutExtension}\". Send another, or press Done."
                }
                saved.nameWithoutExtension
            }
        } catch (e: IOException) {
            return Toast.makeText(this, "Couldn't start the upload page", Toast.LENGTH_LONG).show()
        }
        upload = server
        val url = "http://$ip:${server.port}/${server.code}"
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(4), 0, dp(12))
            addView(ImageView(this@MainActivity).apply { setImageBitmap(qrBitmap(url, dp(200))) })
            addView(label(url, 14f).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(4)) })
            addView(label("Scan with your phone. It must be on the TV's Wi-Fi.", 12f).apply {
                gravity = Gravity.CENTER
                alpha = 0.6f
                maxLines = 3
            })
            addView(status.apply { setPadding(0, dp(12), 0, 0) })
        }
        showPage("Add from your phone", listOf(page("Done") { pickWallpaper(forDark) }), top = box) { pickWallpaper(forDark) }
        pageExit = {
            server.stop()
            if (upload === server) upload = null
        }
    }

    /** The TV's own address on the home network (IPv4: easy to read and type). */
    private fun lanAddress(): String? {
        val cm = getSystemService(ConnectivityManager::class.java)
        return cm.getLinkProperties(cm.activeNetwork)?.linkAddresses?.map { it.address }
            ?.firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }

    /** Black on white with the standard 4-module quiet zone, whole pixels per module so it stays sharp. */
    private fun qrBitmap(text: String, maxPx: Int): Bitmap {
        val m = Qr.encode(text)
        val n = m.size + 8
        val px = maxOf(1, maxPx / n)
        val bmp = Bitmap.createBitmap(n * px, n * px, Bitmap.Config.RGB_565)
        bmp.eraseColor(Color.WHITE)
        val canvas = Canvas(bmp)
        val paint = Paint().apply { color = Color.BLACK }
        for (r in m.indices) for (c in m.indices) if (m[r][c]) {
            canvas.drawRect(((c + 4) * px).toFloat(), ((r + 4) * px).toFloat(), ((c + 5) * px).toFloat(), ((r + 5) * px).toFloat(), paint)
        }
        return bmp
    }

    // ---------- launcher settings panel (top-bar button; the Settings tile is Android's) ----------

    private val settingsScrim by lazy { findViewById<View>(R.id.settingsScrim) }
    private val settingsList by lazy { findViewById<LinearLayout>(R.id.settingsList) }

    /** Opens on the groups, or straight on one setting (by its place in [renderSettings]'s list). */
    private fun openLauncherSettings(focus: Int? = null) {
        showPanel()
        if (focus == null) settingsHome() else renderSettings(focus)
    }

    private fun showPanel() {
        panelFrom = currentFocus
        findViewById<View>(R.id.settingsPanel).setBackgroundColor(if (dark) 0xF707080B.toInt() else 0xFFF7F8FA.toInt())
        findViewById<TextView>(R.id.settingsTitle).setTextColor(fg)
        // Slides in from the end edge over a fading scrim (closing stays instant, see closeLauncherSettings).
        val panel = findViewById<View>(R.id.settingsPanel)
        if (settingsScrim.visibility != View.VISIBLE) {
            settingsScrim.alpha = 0f
            panel.translationX = dp(380).toFloat()
        }
        settingsScrim.visibility = View.VISIBLE
        settingsScrim.animate().alpha(1f).setDuration(240).setInterpolator(EASE_OUT).start()
        panel.animate().translationX(0f).setDuration(420).setInterpolator(EMPH).start()
        homeFocusable(false)
    }

    private fun closeLauncherSettings() {
        pageExit?.invoke()
        pageExit = null
        pageBack = null
        settingsScrim.visibility = View.GONE // instant: a dozen key paths read "visible" as "open"
        homeFocusable(allApps.visibility != View.VISIBLE) // the PIN can open over All apps
        (panelFrom?.takeIf { it.isAttachedToWindow } ?: findViewById(R.id.launcherSettings)).requestFocus()
        panelFrom = null
    }

    private var panelFrom: View? = null // focus to go back to when the panel closes

    private fun renderSettings(focus: Int) {
        val theme = prefs.getString("theme", "light")!!
        val sort = prefs.getString("sort", "manual")!!
        val hidden = prefs.getStringSet("hidden_watch", emptySet())!!.size
        val weather = prefs.getString("weather", "off")!!
        val unit = if (weather == "off") prefs.getString("wx_unit", "c")!! else weather
        val cwOff = prefs.getStringSet("cw_off", emptySet())!!.count { pkg -> apps.any { it.pkg == pkg } } // uninstalled ones don't count
        val rowHidden = rowHiddenSet().count { pkg -> apps.any { it.pkg == pkg } }
        val names = rowNames()
        val colour = colourIcons()
        val ssDates = prefs.getString("ss_dates", "g")!!
        val rows = listOf<Triple<String, String, () -> Unit>>(
            Triple("Rearrange apps", "") { closeLauncherSettings(); startEdit(grab = null) },
            Triple("Theme", mapOf("light" to "Light", "dark" to "Dark", "auto" to "Auto")[theme]!!) {
                prefs.edit().putString("theme", mapOf("light" to "dark", "dark" to "auto", "auto" to "light")[theme])
                    .putBoolean("reopen_settings", true).apply()
                recreate() // colours are applied once per activity; reopen the panel after
            },
            Triple("Light wallpaper", wallpaperName(currentWallpaper(false))) { pickWallpaper(false) },
            Triple("Dark wallpaper", wallpaperName(currentWallpaper(true))) { pickWallpaper(true) },
            Triple("Soften photos", toggle(wallpaperFade())) {
                prefs.edit().putBoolean("wp_fade", !wallpaperFade()).apply()
                loadWallpaper()
                renderSettings(4)
            },
            Triple("Photo changes", PHOTO_EVERY[photoEvery()]!!) { pickPhotoEvery() },
            Triple("App order", if (sort == "manual") "Your order" else "Most used") {
                prefs.edit().putString("sort", if (sort == "manual") "usage" else "manual").apply()
                renderApps()
                renderSettings(6)
            },
            Triple("Apps shown", if (rowHidden == 0) "All" else "${apps.size - rowHidden} of ${apps.size}") { pickRowApps() },
            Triple("App names", mapOf("focus" to "On focus", "always" to "Always", "never" to "Never")[names]!!) {
                prefs.edit().putString("names", mapOf("focus" to "always", "always" to "never", "never" to "focus")[names]).apply()
                renderApps()
                renderSettings(8)
            },
            Triple("Logo colours", if (colour) "Colour" else "Plain") {
                prefs.edit().putBoolean("colour_icons", !colour).apply()
                renderApps()
                renderSettings(9)
            },
            Triple("Hidden shows", if (hidden == 0) "None" else "Show $hidden") {
                prefs.edit().remove("hidden_watch").apply()
                rowKeys.clear()
                refreshWatchNext()
                renderSettings(10)
            },
            Triple("Apps", if (cwOff == 0) "All" else "$cwOff off") { pickWatchSources() },
            // "weather" is off/c/f as before; "wx_unit" keeps the unit while it's off.
            Triple("Weather", toggle(weather != "off")) {
                if (weather == "off") prefs.edit().putString("weather", unit).apply()
                else prefs.edit().putString("wx_unit", weather).putString("weather", "off").apply()
                showWeather()
                ui.removeCallbacks(weatherTick)
                ui.post(weatherTick)
                renderSettings(12)
            },
            Triple("Units", if (unit == "f") "°F" else "°C") {
                val next = if (unit == "f") "c" else "f"
                prefs.edit().putString("wx_unit", next).putString("weather", if (weather == "off") "off" else next).apply()
                showWeather()
                renderSettings(13)
            },
            Triple("Location", if (manualCity()) prefs.getString("geo_city", null) ?: "" else "Automatic") { pickCity() },
            Triple("Screensaver", when {
                downloading -> "Downloading…"
                !galleryOn() -> screensaverName()
                else -> photoScreensaver()?.let { wallpaperName(it) } ?: "Gallery"
            }) { pickScreensaver() },
            Triple("Preview", "") { testScreensaver() },
            Triple("When it starts", "") { openScreensaverSettings() },
            Triple("Home button", toggle(homeServiceOn())) { toggleHomeButton() },
            Triple("Default home", if (isDefaultHome()) "Yes" else "Set") { makeDefaultHome() },
            Triple("Updates", when {
                updating -> "Updating…"
                checking -> "Checking…"
                availableUpdate() != null -> "Get ${availableUpdate()}"
                else -> "${myVersion()}, latest"
            }) { availableUpdate()?.let { installUpdate(it) } ?: checkUpdate(force = true) },
            Triple("PIN lock", toggle(lockOn())) {
                if (!lockOn()) return@Triple setLock()
                prefs.edit().remove("lock_pin").apply()
                renderSettings(LOCK_ROW)
            },
            Triple("Menu apps", railApps().count { pkg -> apps.any { it.pkg == pkg } }.let { if (it == 1) "1 app" else "$it apps" }) { pickRailApps() },
            Triple("Rows", rowChoices().count { it.second in rowsOff() }.let { if (it == 0) "All on" else "$it off" }) { pickRows() },
            Triple("Keep Home working", if (checkSelfPermission(WRITE_SECURE) == PackageManager.PERMISSION_GRANTED) "Done" else "Set up") { adbSetup() },
            Triple("Only launcher", toggle(onlyLauncher())) { toggleOnlyLauncher() },
            // The screensaver's dates: "g" Gregorian, "both", or "fa" Persian (Shamsi) only.
            Triple("Dates", mapOf("g" to "Gregorian", "both" to "Gregorian and Persian", "fa" to "Persian")[ssDates]!!) {
                prefs.edit().putString("ss_dates", mapOf("g" to "both", "both" to "fa", "fa" to "g")[ssDates]).apply()
                renderSettings(DATES_ROW)
            },
            // Music needs notification access (that's how Android shares what's playing); tapping it sets that up.
            Triple("Music", if (!musicAccess(this)) "Set up" else toggle(prefs.getBoolean("ss_music", true))) {
                if (!musicAccess(this)) allowMusic() else prefs.edit().putBoolean("ss_music", !prefs.getBoolean("ss_music", true)).apply()
                renderSettings(MUSIC_ROW)
            },
        )
        val (name, _, picks) = SETTINGS_GROUPS.first { focus in it.third }
        showPage(name, picks.map { rows[it] }, picks.indexOf(focus), back = { settingsHome(SETTINGS_GROUPS.indexOfFirst { it.first == name }) })
    }

    /** The first settings page: a few groups, each a short list. */
    private fun settingsHome(focus: Int = 0) = showPage("Koolest settings", SETTINGS_GROUPS.map { (name, hint, picks) ->
        page(name, hint) { renderSettings(picks.first()) }
    }, focus, back = null)

    // Pages inside the settings panel (wallpaper list, app toggles…) instead of system dialogs, so they
    // match Koolest's look. Back goes up one page; from the main list it closes the panel.

    private var pageBack: (() -> Unit)? = null
    private var pageExit: (() -> Unit)? = null // cleanup when the page goes away (stops the upload server)

    private fun page(title: String, value: String = "", action: () -> Unit) = Triple(title, value, action)

    private fun showPage(title: String, rows: List<Triple<String, String, () -> Unit>>, focus: Int = 0, top: View? = null, back: (() -> Unit)?) {
        pageExit?.invoke()
        pageExit = null
        pageBack = back
        findViewById<TextView>(R.id.settingsTitle).text = title
        settingsList.removeAllViews()
        top?.let { settingsList.addView(it) }
        val first = settingsList.childCount
        rows.forEach { (name, value, action) ->
            settingsList.addView(settingsRow(name, value, action), LinearLayout.LayoutParams(MATCH_PARENT, dp(44)).apply {
                topMargin = dp(4)
            })
        }
        // Keep focus inside the panel: Up from the first row and Down from the last go nowhere.
        (settingsList.parent as View).scrollTo(0, 0)
        if (rows.isEmpty()) return // a page that's all [top], like the PIN boxes, focuses itself
        settingsList.getChildAt(first).let { it.nextFocusUpId = it.id }
        settingsList.getChildAt(first + rows.size - 1).let { it.nextFocusDownId = it.id }
        settingsList.getChildAt(first + focus.coerceIn(0, rows.size - 1)).requestFocus()
    }

    // ---------- menu apps ----------
    // The apps pinned to the menu, between Home and All apps: rearrange, remove, add.

    private fun pickRailApps(focus: Int = 0) {
        val pinned = railApps().filter { pkg -> apps.any { it.pkg == pkg } } // uninstalled ones drop out
        showPage("Menu apps", pinned.mapIndexed { i, pkg ->
            page(apps.first { it.pkg == pkg }.label, "Move or remove") { railAppPage(pinned, i) }
        } + page("Add an app…") { addRailApp(pinned) }, focus, back = { renderSettings(MENU_APPS_ROW) })
    }

    private fun railAppPage(pinned: List<String>, i: Int) {
        fun moved(to: Int) = pinned.toMutableList().apply { add(to, removeAt(i)) }
        showPage(apps.first { it.pkg == pinned[i] }.label, listOfNotNull(
            if (i == 0) null else page("Move up") { saveRailApps(moved(i - 1)); pickRailApps(i - 1) },
            if (i == pinned.lastIndex) null else page("Move down") { saveRailApps(moved(i + 1)); pickRailApps(i + 1) },
            page("Remove from menu") { saveRailApps(pinned - pinned[i]); pickRailApps(minOf(i, pinned.size - 2).coerceAtLeast(0)) },
        ), back = { pickRailApps(i) })
    }

    private fun addRailApp(pinned: List<String>) {
        val choices = apps.filter { it.pkg !in pinned }.sortedBy { it.label.lowercase() }
        showPage("Add to menu", choices.map { a ->
            page(a.label) { saveRailApps(pinned + a.pkg); pickRailApps(pinned.size) }
        }, back = { pickRailApps(pinned.size) })
    }

    // ---------- layout lock ----------
    // A PIN in front of Koolest settings and everything that changes the home screen; apps still open as usual.
    // It keeps the layout safe from small hands, it doesn't keep apps from kids: voice search and Android
    // settings get past it, so it's called a layout lock, not parental controls.

    private fun lockOn() = prefs.contains("lock_pin")

    private var unlockedUntil = 0L // one PIN covers a few minutes of changes, until the home screen is left
    private var pinFails = 0
    private var pinWaitUntil = 0L // ponytail: in memory, so restarting Koolest resets the wait; fine for kids at a remote
    private var pinBox: View? = null

    private fun locked() = lockOn() && SystemClock.elapsedRealtime() > unlockedUntil

    /** Runs [then] at once when unlocked, otherwise after the right PIN. */
    private fun unlock(then: () -> Unit) {
        if (!locked()) return then()
        showPanel()
        pinPage("Enter PIN", back = null) { pin ->
            val now = SystemClock.elapsedRealtime()
            if (now < pinWaitUntil) return@pinPage "Too many tries. Try again in ${(pinWaitUntil - now) / 1000 + 1} s."
            if (!pinMatches(prefs.getString("lock_pin", null)!!, pin)) {
                if (++pinFails < PIN_TRIES) return@pinPage "Wrong PIN"
                pinFails = 0
                pinWaitUntil = now + 30_000L
                return@pinPage "Too many tries. Try again in 30 s."
            }
            pinFails = 0
            unlockedUntil = now + UNLOCKED_MS
            closeLauncherSettings()
            then()
            null
        }
    }

    private fun setLock(): Unit = pinPage("Choose a PIN", { renderSettings(LOCK_ROW) }) { first ->
        pinPage("Enter it again", { renderSettings(LOCK_ROW) }) { again ->
            if (again != first) {
                Toast.makeText(this, "Those PINs didn't match. Choose one again.", Toast.LENGTH_LONG).show()
                setLock()
                return@pinPage null
            }
            prefs.edit().putString("lock_pin", hashPin(first)).apply()
            unlockedUntil = SystemClock.elapsedRealtime() + UNLOCKED_MS // no PIN straight away for the one who set it
            renderSettings(LOCK_ROW)
            Toast.makeText(this, "Layout locked. Koolest settings and home screen changes now need the PIN.", Toast.LENGTH_LONG).show()
            null
        }
        null
    }

    /**
     * Four digit boxes: ▲ ▼ change a digit, ◀ ▶ move between boxes, number keys type, OK enters.
     * [done] gets the PIN and returns null when it's accepted, or a message to show before trying again.
     */
    private fun pinPage(title: String, back: (() -> Unit)?, done: (String) -> String?) {
        val digits = arrayOfNulls<Int>(4)
        var at = 0
        val boxes = List(4) { label("", 24f).apply { gravity = Gravity.CENTER } }
        val status = label("", 13f).apply { gravity = Gravity.CENTER; alpha = 0.6f }
        val row = LinearLayout(this).apply {
            id = View.generateViewId()
            gravity = Gravity.CENTER
            isFocusable = true
            boxes.forEach { addView(it, LinearLayout.LayoutParams(dp(48), dp(56)).apply { marginStart = dp(6); marginEnd = dp(6) }) }
        }
        fun draw() = boxes.forEachIndexed { i, b ->
            // Only the box being changed shows its digit.
            b.text = if (digits[i] == null) "" else if (i == at) digits[i].toString() else "•"
            b.background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(if (dark) 0xFF161922.toInt() else Color.WHITE)
                if (i == at) setStroke(dp(2), ACCENT)
            }
        }
        fun submit() {
            val error = done(digits.joinToString("")) ?: return
            digits.fill(null)
            at = 0
            draw()
            status.text = error
            ObjectAnimator.ofFloat(row, View.TRANSLATION_X, 0f, -dp(12).toFloat(), dp(12).toFloat(), -dp(8).toFloat(), dp(8).toFloat(), 0f).setDuration(300).start()
        }
        row.setOnKeyListener { _, code, e ->
            val digit = when (code) {
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> code - KeyEvent.KEYCODE_0
                in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> code - KeyEvent.KEYCODE_NUMPAD_0
                else -> null
            }
            if (digit == null && code !in PIN_KEYS) return@setOnKeyListener false // Back still leaves
            if (e.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener true
            when (code) {
                KeyEvent.KEYCODE_DPAD_UP -> digits[at] = ((digits[at] ?: 0) + 1) % 10
                KeyEvent.KEYCODE_DPAD_DOWN -> digits[at] = ((digits[at] ?: 0) + 9) % 10
                KeyEvent.KEYCODE_DPAD_LEFT -> at = maxOf(0, at - 1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (digits[at] != null) at = minOf(3, at + 1)
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                    if (digits.all { it != null }) return@setOnKeyListener true.also { submit() }
                    else if (digits[at] != null) at = minOf(3, at + 1)
                else -> {
                    digits[at] = digit
                    if (at == 3) return@setOnKeyListener true.also { draw(); submit() }
                    at++
                }
            }
            draw()
            true
        }
        draw()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, dp(8))
            addView(row)
            addView(status.apply { setPadding(0, dp(12), 0, 0) })
            addView(label("▲ ▼ change  ·  ◀ ▶ move  ·  OK enter. Number keys work too.", 12f).apply {
                maxLines = 2
                gravity = Gravity.CENTER
                alpha = 0.6f
                setPadding(0, dp(8), 0, 0)
            })
            if (lockOn()) addView(label("Forgot it? Clearing Koolest's data in Android Settings → Apps removes it, and resets everything.", 12f).apply {
                gravity = Gravity.CENTER
                alpha = 0.6f
                maxLines = 3
                setPadding(0, dp(24), 0, 0)
            })
        }
        showPage(title, emptyList(), top = box, back = back)
        pinBox = row
        row.requestFocus()
    }

    private fun settingsRow(title: String, value: String, action: () -> Unit): View {
        val bg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0, 0)).apply { cornerRadius = dp(8).toFloat() }
        return LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = bg
            isFocusable = true
            isClickable = true
            val icon = SETTING_ICONS[title]?.let { getDrawable(it)!!.mutate() }
            val app = if (icon == null) apps.firstOrNull { it.label == title } else null
            val img = (icon ?: app?.let { railIcon(it).constantState?.newDrawable(resources) })?.let { d ->
                ImageView(context).apply {
                    setImageDrawable(d)
                    if (icon != null) setColorFilter(fg) else {
                        outlineProvider = object : android.view.ViewOutlineProvider() { // an app's icon, softly rounded like the menu's
                            override fun getOutline(view: View, o: android.graphics.Outline) = o.setRoundRect(0, 0, view.width, view.height, view.width * 0.28f)
                        }
                        clipToOutline = true
                    }
                }
            }
            img?.let { addView(it, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(12) }) }
            addView(label(title, 14f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (value == TOGGLE_ON || value == TOGGLE_OFF) addView(switch(value == TOGGLE_ON), LinearLayout.LayoutParams(dp(36), dp(20)))
            else addView(label(value, 13f).apply { alpha = 0.6f })
            setOnClickListener { action() }
            setOnFocusChangeListener { _, focused ->
                bg.colors = if (focused) cardLit else intArrayOf(0, 0)
                for (i in 0 until childCount) (getChildAt(i) as? TextView)?.setTextColor(if (focused) focusInk else fg)
                if (SETTING_ICONS[title] != null) (getChildAt(0) as? ImageView)?.setColorFilter(if (focused) focusInk else fg)
            }
        }
    }

    private fun toggle(on: Boolean) = if (on) TOGGLE_ON else TOGGLE_OFF

    /** On/off switch: accent track with a white knob right when on, outlined and left when off. */
    private fun switch(on: Boolean) = FrameLayout(this).apply {
        val faint = 0xFF8A8C92.toInt() // mid grey: reads on the row at rest and on its focus fill, in both themes
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            if (on) setColor(ACCENT) else setStroke(dp(2), faint)
        }
        addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (on) Color.WHITE else faint)
            }
        }, FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER_VERTICAL or if (on) Gravity.END else Gravity.START).apply {
            marginStart = dp(3)
            marginEnd = dp(3)
        })
    }

    private fun showNet(caps: NetworkCapabilities?) {
        val ethernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        wifi.setImageResource(if (ethernet) R.drawable.ic_ethernet else R.drawable.rail_wifi)
        // Dim only the icon: the focus pill is the background and should stay full strength.
        wifi.imageAlpha = if (caps == null) 90 else 255
    }

    private fun openSettings(action: String, data: Uri? = null) {
        try {
            startActivity(Intent(action, data))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    // ---------- apps row ----------

    /** Labels and icons come from each app's resources, ~200 ms on this TV: done off the UI thread. */
    private fun refreshApps() {
        appsDirty = false
        io.execute {
            val list = try {
                loadApps()
            } catch (e: Exception) {
                return@execute
            }
            ui.post {
                apps = list
                APPS = list
                renderApps()
            }
        }
    }

    private fun loadApps(): List<App> {
        val pm = packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
            .map { it.activityInfo }
            .filter { it.packageName != packageName && it.packageName != TV_SETTINGS }
            .distinctBy { it.packageName }
            .map { a ->
                App(
                    a.packageName,
                    a.loadLabel(pm).toString(),
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                        .setComponent(ComponentName(a.packageName, a.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    a.loadIcon(pm),
                    a.loadBanner(pm),
                )
            } + consoleInputs()
    }

    /**
     * Devices on HDMI that name themselves over CEC (the PS5 shows up as "PlayStation 5"), as app tiles.
     * Opening one switches the TV to that HDMI input, which also wakes the device over CEC.
     */
    private fun consoleInputs(): List<App> = try {
        val hdmi = getSystemService(TvInputManager::class.java).tvInputList.filter { it.type == TvInputInfo.TYPE_HDMI }
        val devices = hdmi.filter { it.parentId != null }
        // A port with a named device on it shows as that device; an empty or unnamed port still gets a tile ("HDMI 2").
        (devices + hdmi.filter { port -> port.parentId == null && devices.none { it.parentId == port.id } })
            .map { input ->
                val label = input.loadLabel(this).toString()
                val port = input.parentId ?: input.id
                App(
                    HDMI + port,
                    label,
                    Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(port))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    getDrawable(deviceLogo(label).res)!!,
                )
            }
            .distinctBy { it.pkg }
    } catch (e: Exception) {
        android.util.Log.w("Koolest", "HDMI inputs failed", e) // a bad mark once hid every console
        emptyList()
    }

    // After the TV turns on, a console often shows first as a bare port ("HDMI 3") and gets its name over CEC
    // ("PlayStation 5") seconds later: that arrives as an update, not as a new input.
    private val inputCallback = object : TvInputManager.TvInputCallback() {
        override fun onInputAdded(id: String) = inputsChanged()
        override fun onInputRemoved(id: String) = inputsChanged()
        override fun onInputUpdated(id: String) = inputsChanged()
        override fun onTvInputInfoUpdated(info: TvInputInfo) = inputsChanged()
    }

    private val inputRefresh = Runnable { refreshApps() }

    /** Several of these come at once while the TV settles; rebuild once they stop. */
    private fun inputsChanged() {
        ui.removeCallbacks(inputRefresh)
        ui.postDelayed(inputRefresh, 600)
    }

    /** The HDMI inputs and the names they go by, to notice a console being recognised. */
    private fun inputNames() = try {
        getSystemService(TvInputManager::class.java).tvInputList.filter { it.type == TvInputInfo.TYPE_HDMI }
            .joinToString("|") { "${it.id}=${it.loadLabel(this)}" }
    } catch (e: Exception) {
        ""
    }

    // shortcut: also checked every 15 s while home is on screen, as some TVs (TCL) don't report a name arriving.
    private var lastInputNames: String? = null
    private val inputPoll = object : Runnable {
        override fun run() {
            val now = inputNames()
            if (lastInputNames != null && now != lastInputNames) inputsChanged()
            lastInputNames = now
            ui.postDelayed(this, 15_000L)
        }
    }

    private fun sortedApps(): List<App> {
        // Streaming apps first by default, then alphabetical; manual order or usage overrides.
        val byDefault = compareBy<App> { DEFAULT_ORDER.indexOf(it.pkg).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            .thenBy { it.label.lowercase() }
        if (prefs.getString("sort", "manual") == "usage") {
            return apps.sortedWith(compareByDescending<App> { prefs.getInt("n_" + it.pkg, 0) }.then(byDefault))
        }
        val order = prefs.getString("order", "")!!.split(',')
        // A console not yet placed by hand goes first, so it's visible without scrolling.
        return apps.sortedWith(compareBy<App> {
            order.indexOf(it.pkg).let { i -> if (i >= 0) i else if (it.pkg.startsWith(HDMI)) -1 else Int.MAX_VALUE }
        }.then(byDefault))
    }

    /**
     * Every app, logo only, on the same 8-column grid as the Continue Watching cards (one card = two
     * tiles), so 8 show at once and the row scrolls for the rest. All apps and Settings are in the top bar.
     */
    private fun renderApps() {
        val focusedTag = currentFocus?.tag
        // Rebuilds can happen in the background (an app or HDMI device added); only take focus if a tile row had it.
        val takeFocus = currentFocus == null || currentFocus?.parent === appRow || currentFocus?.parent === consoleRow
        buildRail() // its pinned apps may have just loaded or changed
        val gap = dp(ITEM_GAP) // as cards: tiles are card-sized, so app rows line up with card rows
        val shown = sortedApps().filter { it.pkg !in rowHiddenSet() }
        val off = rowsOff()
        for ((row, list) in listOf(
            consoleRow to shown.filter { it.pkg.startsWith(HDMI) && "consoles" !in off },
            appRow to shown.filter { !it.pkg.startsWith(HDMI) && "apps" !in off },
        )) {
            row.removeAllViews()
            list.forEachIndexed { i, a ->
                row.addView(appTile(a, dp(200), labeled = rowNames() == "always").apply { id = View.generateViewId() },
                    LinearLayout.LayoutParams(dp(200), WRAP_CONTENT).apply { if (i > 0) marginStart = gap })
            }
            rowOf(row)!!.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        }
        if (takeFocus) (kids(consoleRow).plus(kids(appRow)).firstOrNull { it.tag == focusedTag } ?: firstRowEntry())?.requestFocus()
        if (!prefs.getBoolean("tip_move", false)) {
            prefs.edit().putBoolean("tip_move", true).apply()
            Toast.makeText(this, "Tip: hold OK on an app to rearrange the row", Toast.LENGTH_LONG).show()
        }
    }

    /** App names: "focus" (the shelf names the focused app), "always" (under every tile too) or "never". */
    private fun rowNames() = prefs.getString("names", "focus")!!

    /** Each app's own shipped icon instead of the mono mark, in the row and All apps. */
    private fun colourIcons() = prefs.getBoolean("colour_icons", false)

    /** The home row shows marks only by default (see rowNames); the All apps grid always labels them. */
    private fun appTile(a: App, width: Int, labeled: Boolean = true): View {
        val logo = LOGOS[a.pkg]
        val custom = customIcon(a.pkg)
        val name = a.label.takeIf { labeled }
        // Colour mode keeps the same marks, tinted with the brand colour (or the colour the app's icon is mostly).
        val colour = colourIcons()
        val inset = width * 2 / 9 // a wordmark fills the middle of the tile's width
        val t = when {
            custom != null -> tile(name, custom, false, a.pkg, width) { launch(a) }
            // Consoles: the mark on the brand's colour (PS5 white, PlayStation blue, Xbox green), the way Apple TV tiles look.
            a.pkg.startsWith(HDMI) -> deviceLogo(a.label).let { d ->
                tile(name.takeIf { !d.wordmark }, a.icon, true, a.pkg, width, wordmarkInset = if (d.wordmark) inset else null,
                    tint = d.ink.takeIf { d.colour != null }, fill = d.colour) { launch(a) }
            }
            a.banner != null -> tile(name, a.icon, false, a.pkg, width, banner = a.banner) { launch(a) }
            logo != null -> tile(name.takeIf { !logo.second }, getDrawable(logo.first)!!, true, a.pkg, width,
                wordmarkInset = if (!logo.second) null else inset,
                tint = BRAND_COLOURS[a.pkg]?.takeIf { colour }) { launch(a) }
            else -> cachedGlyph(a)?.let { tile(name, it, true, a.pkg, width, tint = if (colour) cachedAccent(a) else null) { launch(a) } }
                ?: if (colour) tile(name, a.icon.constantState?.newDrawable(resources) ?: a.icon, false, a.pkg, width) { launch(a) }
                // A copy: greying the shared icon itself would keep it grey after switching to colour.
                else tile(name, (a.icon.constantState?.newDrawable(resources) ?: a.icon).mutate().apply { colorFilter = GREYSCALE }, false, a.pkg, width) { launch(a) }
        }
        t.contentDescription = a.label
        return t
    }
    /**
     * An app tile the Apple TV way: a rounded 16:9 picture, card-sized. Apps show their own TV banner; without
     * one, the mark sits centred on a plain tile, or on [fill] (a console's brand colour). A hairline rim of
     * light (tvOS's glass edge); focus lifts it like a card. The name, when shown, sits underneath.
     */
    private fun tile(label: String?, icon: Drawable, mono: Boolean, tag: String, width: Int, wordmarkInset: Int? = null, tint: Int? = null,
                     banner: Drawable? = null, fill: Int? = null, onClick: () -> Unit): View {
        val radius = dp(8).toFloat()
        val img = ImageView(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = radius
                setColor(fill ?: if (dark) 0xFF20242F.toInt() else 0xFFE5E2DC.toInt())
            }
            clipToOutline = true
            if (banner != null) {
                setImageDrawable(banner)
                scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                setImageDrawable(icon.mutate())
                if (mono) setColorFilter(tint ?: fg)
                // Icons share one height. Wordmarks sit in a square viewport, so fit them by width instead
                // (crop the empty top and bottom) or they shrink to a sliver.
                if (wordmarkInset != null) {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setPadding(wordmarkInset, 0, wordmarkInset, 0)
                } else {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    // A console's symbol-over-model mark sits in a square with room round it: give it more of the tile.
                    val pad = if (fill != null) width / 14 else width / 7
                    setPadding(0, pad, 0, pad)
                }
            }
            foreground = GradientDrawable().apply {
                cornerRadius = radius
                setStroke(dp(1), if (dark) 0x2EFFFFFF else 0x1A000000)
            }
        }
        val frame = FrameLayout(this).apply { addView(img, MATCH_PARENT, MATCH_PARENT) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            addView(frame, LinearLayout.LayoutParams(width, width * 9 / 16))
        }
        val text = label?.let {
            label(it, 13f).apply {
                gravity = Gravity.CENTER
                alpha = 0.72f
                setPadding(dp(4), dp(14), dp(4), 0)
                col.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
        }
        // The column takes focus (row code knows tiles by it); the picture lifts, the name brightens.
        val lift = cardFocus(frame, radius)
        style(col, 0, card = false, lift = 1f, bare = true) { focused ->
            lift(focused)
            text?.animate()?.alpha(if (focused) 1f else 0.72f)?.setDuration(180)?.start()
        }
        col.tag = tag
        col.setOnClickListener { onClick() }
        return col
    }

    /**
     * The card focus look on [frame], for its own or a parent's focus: scale, a 4 px white ring 6 px outside it
     * (drawn past its bounds, nothing up the tree clips) and a lift shadow.
     */
    private fun cardFocus(frame: FrameLayout, radius: Float, lift: Float = FOCUS_LIFT): (Boolean) -> Unit {
        frame.clipChildren = false
        frame.outlineProvider = object : android.view.ViewOutlineProvider() { // for the lift shadow
            override fun getOutline(view: View, o: android.graphics.Outline) = o.setRoundRect(0, 0, view.width, view.height, radius)
        }
        val ring = GradientDrawable().apply {
            cornerRadius = radius + dp(3)
            setStroke(dp(2), focusFill)
            alpha = 0
        }
        frame.foreground = android.graphics.drawable.InsetDrawable(ring, -dp(5))
        return { focused ->
            ring.alpha = if (focused) 255 else 0
            frame.animate().scaleX(if (focused) lift else 1f).scaleY(if (focused) lift else 1f)
                .translationZ(if (focused) dp(18).toFloat() else 0f).setDuration(220).setInterpolator(EASE_OUT).start()
        }
    }

    /**
     * A mono mark from an adaptive icon's foreground layer (its symbol, without the coloured backplate),
     * cropped to the safe zone. Brightness becomes opacity: the light symbol becomes the mark, and dark
     * details in it (a note inside a circle) become holes, so the mono tint keeps them. Null for old
     * single-layer icons and for photo-like layers that fill the square, which keep the greyscale look.
     */
    private fun glyph(icon: Drawable): Bitmap? {
        val fg = (icon as? AdaptiveIconDrawable)?.foreground ?: return null
        val size = dp(96) // large, so a small symbol is still sharp after trimming and scaling up
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        // Layers are 108dp with the symbol inside the middle 72dp; drawing at 1.5x crops to that.
        fg.setBounds(-size / 4, -size / 4, size * 5 / 4, size * 5 / 4)
        fg.draw(Canvas(bmp))
        val px = IntArray(size * size)
        bmp.getPixels(px, 0, size, 0, 0, size, size)
        if (px.count { Color.alpha(it) > 200 } > px.size * 0.7) return null // a picture, not a symbol
        val alpha = FloatArray(px.size) { i ->
            val c = px[i]
            Color.alpha(c) * (0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)) / 255f
        }
        val peak = alpha.max().takeIf { it > 0f } ?: return null
        // Stretch so the brightest part is fully opaque: an orange symbol shouldn't look dimmer than a white one.
        for (i in px.indices) px[i] = Color.argb((alpha[i] * 255f / peak).toInt().coerceAtMost(255), 255, 255, 255)
        bmp.setPixels(px, 0, size, 0, 0, size, size)
        // Trim to the visible mark (as a square, centred), so small symbols fill the tile like the logos do.
        val on = px.indices.filter { Color.alpha(px[it]) > 40 }.ifEmpty { return null }
        val l = on.minOf { it % size }; val r = on.maxOf { it % size }
        val t = on.minOf { it / size }; val b = on.maxOf { it / size }
        val side = maxOf(r - l, b - t) + 1
        val x = ((l + r + 1 - side) / 2).coerceIn(0, size - side)
        val y = ((t + b + 1 - side) / 2).coerceIn(0, size - side)
        return Bitmap.createBitmap(bmp, x, y, side, side)
    }

    /** glyph() walks every pixel, too slow to redo on each row rebuild; kept per app until the app changes. */
    private fun cachedGlyph(a: App): Drawable? {
        val bmp = if (a.pkg in GLYPHS) GLYPHS[a.pkg] else glyph(a.icon).also { GLYPHS[a.pkg] = it }
        return bmp?.let { BitmapDrawable(resources, it) }
    }

    /**
     * The colour an icon is mostly, for tinting its mark: the biggest hue among its vivid pixels (backplate
     * included, which is where most apps keep their colour). Null if it's all greys, or too close to the
     * card colour to read on it; the mark then stays the theme's mono colour.
     */
    private fun accent(icon: Drawable): Int? {
        val size = 32
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.constantState?.newDrawable(resources)?.apply { setBounds(0, 0, size, size) }?.draw(Canvas(bmp)) ?: return null
        val px = IntArray(size * size).also { bmp.getPixels(it, 0, size, 0, 0, size, size) }
        bmp.recycle()
        val hsv = FloatArray(3)
        val buckets = px.filter { Color.alpha(it) > 200 }
            .filter { Color.colorToHSV(it, hsv); hsv[1] > 0.35f && hsv[2] > 0.3f }
            .groupBy { Color.colorToHSV(it, hsv); (hsv[0] / 30).toInt() } // 12 hue buckets
        val top = buckets.values.maxByOrNull { it.size }?.takeIf { it.size > px.size / 20 } ?: return null
        return Color.rgb(top.sumOf { Color.red(it) } / top.size, top.sumOf { Color.green(it) } / top.size, top.sumOf { Color.blue(it) } / top.size)
    }

    /** accent() per app, kept like GLYPHS; the contrast check is per theme, so it runs on every use. */
    private fun cachedAccent(a: App): Int? {
        val c = if (a.pkg in ACCENTS) ACCENTS[a.pkg] else accent(a.icon).also { ACCENTS[a.pkg] = it }
        val lum = c?.let { Color.luminance(it) } ?: return null
        return c.takeIf { if (dark) lum > 0.08f else lum < 0.6f }
    }

    /** Optional per-theme icons: assets/icons/<package>_dark.png, <package>_light.png, or <package>.png. */
    private fun customIcon(pkg: String): Drawable? =
        listOf("icons/${pkg}_${if (dark) "dark" else "light"}.png", "icons/$pkg.png").firstNotNullOfOrNull { path ->
            try {
                assets.open(path).use { Drawable.createFromStream(it, null) }
            } catch (e: IOException) {
                null
            }
        }

    // ---------- holding OK, and editing the apps row ----------

    /**
     * Hold detection lives here, not in View long-click: this TV's remote doesn't reliably trigger the
     * framework's long-press timer. We treat the first auto-repeat of OK as "held"; the long-click
     * listener in style() is a fallback for remotes that send no repeats. [holdFired] stops both firing.
     */
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val confirm = e.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || e.keyCode == KeyEvent.KEYCODE_ENTER ||
            e.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (confirm) {
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) holdFired = false
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount > 0) {
                if (!holdFired) currentFocus?.let { fireHold(it) }
                return true
            }
            if (e.action == KeyEvent.ACTION_UP && holdFired) {
                currentFocus?.isPressed = false
                return true
            }
        }
        // The settings panel is a vertical list: don't let Left/Right leak focus to the home screen behind it.
        // The PIN boxes and the city search box use them themselves.
        if (settingsScrim.visibility == View.VISIBLE && currentFocus !== pinBox && currentFocus !is EditText &&
            (e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
        ) return true
        if (editing && editKey(e, confirm)) return true
        if (e.action == KeyEvent.ACTION_DOWN && settingsScrim.visibility != View.VISIBLE && allApps.visibility != View.VISIBLE) {
            if (e.keyCode in EDIT_KEYS) navigated = true
            currentFocus?.let { homeNav(it, e.keyCode) }?.let { it.requestFocus(); return true }
        }
        return super.dispatchKeyEvent(e)
    }

    /**
     * The home screen is the menu rail beside a stack of rows (the 30nama layout), with buttons at the top end.
     * Left/Right step along a row; Left from its first item opens the menu, Right leaves it back to where you
     * were. Up/Down move between rows and land on the row's first item; Up from the first row goes to
     * the top buttons. Returns the view to focus (itself = stay), or null to let Android handle the key.
     */
    private fun homeNav(v: View, key: Int): View? {
        if ((v.parent as? View)?.parent === railItems) { // the menu
            val i = railItems.indexOfChild(v.parent as View)
            fun at(j: Int) = (railItems.getChildAt(j) as? ViewGroup)?.let { it.getChildAt(it.childCount - 1) }
            return when (key) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> lastRowItem?.takeIf { it.isAttachedToWindow } ?: firstRowEntry() ?: v
                KeyEvent.KEYCODE_DPAD_LEFT -> v
                KeyEvent.KEYCODE_DPAD_UP -> if (i <= 0) v else at(i - 1)
                KeyEvent.KEYCODE_DPAD_DOWN -> at(i + 1) ?: v
                else -> null
            }
        }
        if (v.parent === wifi.parent) return when (key) { // top end buttons
            KeyEvent.KEYCODE_DPAD_DOWN -> lastRowItem?.takeIf { it.isAttachedToWindow } ?: firstRowEntry() ?: v
            KeyEvent.KEYCODE_DPAD_UP -> v
            KeyEvent.KEYCODE_DPAD_LEFT -> if (v.id == R.id.wifi) findViewById(R.id.railHome) else null
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (v.id == R.id.launcherSettings) v else null
            else -> null
        }
        val row = rowOf(v) ?: return null
        val visible = kids(rows).filter { it.visibility == View.VISIBLE }
        val i = visible.indexOf(row)
        val item = itemOf(v)
        val strip = item.parent as ViewGroup
        val at = strip.indexOfChild(item)
        return when (key) {
            KeyEvent.KEYCODE_DPAD_UP -> if (i <= 0) lastTop?.takeIf { it.isAttachedToWindow } ?: wifi else entry(visible[i - 1])
            KeyEvent.KEYCODE_DPAD_DOWN -> if (i == visible.lastIndex) v else entry(visible[i + 1])
            KeyEvent.KEYCODE_DPAD_LEFT -> if (at > 0) focusTarget(strip.getChildAt(at - 1)) else if (editing) v else findViewById(R.id.railHome)
            KeyEvent.KEYCODE_DPAD_RIGHT -> focusTarget(strip.getChildAt(at + 1)) ?: v
            else -> null
        }
    }

    /** The row's direct item [v] belongs to: a tile itself, or a card's column around its focusable picture. */
    private fun itemOf(v: View): View {
        var item = v
        while ((item.parent as View).parent !is SnapScrollView) item = item.parent as View
        return item
    }
    /** Overlays (All apps, launcher settings) sit on top of the home rows; keep focus from slipping behind them. */
    private fun homeFocusable(on: Boolean) {
        findViewById<ViewGroup>(R.id.content).descendantFocusability =
            if (on) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    private fun fireHold(v: View) {
        holdFired = true
        v.cancelLongPress()
        v.isPressed = false
        onHold(v)
    }

    /** What holding OK does depends on what's focused. */
    private fun onHold(v: View) {
        val tag = v.tag as? String
        when {
            allApps.visibility == View.VISIBLE && tag != null -> unlock { // grid: pin to the front of the row (unhiding it)
                prefs.edit().putStringSet("row_hidden", rowHiddenSet() - tag).apply()
                saveOrder(listOf(tag) + sortedApps().map { it.pkg }.filter { it != tag })
                closeAllApps()
                appRow.getChildAt(0)?.requestFocus() // show the app just pinned
            }
            v.parent === appRow || v.parent === consoleRow -> appMenu(v)
            v.getTag(R.id.watchRow) is Prog -> watchMenu(v, v.getTag(R.id.watchRow) as Prog)
        }
    }

    /** Hold OK on a row app: Move (OK places it), Remove from row (stays in All apps), App info. Locked: Open only. */
    private fun appMenu(v: View) {
        val pkg = v.tag as String
        if (locked()) return holdMenu(v, listOf(
            "Open" to { v.performClick(); Unit },
            UNLOCK_TO_EDIT to { unlock { v.requestFocus(); appMenu(v) } },
        ))
        holdMenu(v, listOfNotNull(
            if (v.parent !== appRow) null else "Move" to { startEdit(grab = v, once = true) }, // consoles keep their own order
            "Remove from row" to {
                val row = v.parent as ViewGroup
                val i = row.indexOfChild(v)
                prefs.edit().putStringSet("row_hidden", rowHiddenSet() + pkg).apply()
                renderApps()
                (row.getChildAt(minOf(i, row.childCount - 1)) ?: firstRowEntry())?.requestFocus()
                Toast.makeText(this, "Removed from the row. It's still in All apps.", Toast.LENGTH_SHORT).show()
            },
            if (pkg.startsWith(HDMI)) null else "App info" to {
                openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
            },
        ))
    }

    /** Hold OK on a Continue Watching card: hide the show, or open its app rather than the episode. */
    private fun watchMenu(v: View, p: Prog) {
        holdMenu(v, listOfNotNull(
            if (locked()) null else "Hide from home" to { hideWatch(p) },
            if (!locked()) null else UNLOCK_TO_EDIT to { unlock { v.requestFocus(); watchMenu(v, p) } },
            p.pkg?.let { pkg ->
                "Open app" to {
                    (packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg))
                        ?.let { startActivity(it) }
                    Unit
                }
            },
        ))
    }

    /** A small Koolest-styled menu just above [anchor]; Back closes it. */
    private fun holdMenu(anchor: View, items: List<Pair<String, () -> Unit>>) {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (dark) 0xFF161922.toInt() else 0xFFF7F8FA.toInt())
            }
            elevation = dp(8).toFloat()
        }
        val menu = PopupWindow(list, dp(260), LinearLayout.LayoutParams.WRAP_CONTENT, true)
        items.forEachIndexed { i, (name, action) ->
            list.addView(settingsRow(name, "") { menu.dismiss(); action() }, LinearLayout.LayoutParams(MATCH_PARENT, dp(40)).apply {
                if (i > 0) topMargin = dp(2)
            })
        }
        list.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val at = IntArray(2).also { anchor.getLocationInWindow(it) }
        menu.elevation = dp(8).toFloat()
        menu.showAtLocation(anchor, Gravity.NO_GRAVITY, at[0], maxOf(dp(8), at[1] - list.measuredHeight - dp(8)))
        list.getChildAt(0).requestFocus()
    }

    private fun appTileCount() = appRow.childCount

    private var moveOnce = false // moving one app from the hold menu: OK places it and editing ends

    private fun startEdit(grab: View?, once: Boolean = false) {
        if (appRow.childCount == 0) return Toast.makeText(this, "Turn the Apps row on first", Toast.LENGTH_SHORT).show()
        editing = true
        moveOnce = once
        appsTitle.text = if (once) "Moving:  ◀ ▶ move  ·  OK place" else "Editing apps row:  OK pick up / drop  ·  ◀ ▶ move  ·  Back done"
        appsTitle.visibility = View.VISIBLE
        (grab ?: appRow.getChildAt(0)).requestFocus()
        setGrabbed(grab)
    }

    private fun setGrabbed(v: View?) {
        grabbed?.alpha = 1f
        grabbed = v
        v?.alpha = 0.6f
    }

    private fun endEdit() {
        setGrabbed(null)
        editing = false
        moveOnce = false
        appsTitle.visibility = View.GONE
        val row = (0 until appTileCount()).map { appRow.getChildAt(it).tag as String }
        saveOrder(row + sortedApps().map { it.pkg }.filter { it !in row })
        shelfFor = null
        focusShelf(currentFocus) // the billboard was hidden while editing
    }

    /** While editing, keys only rearrange the row; nothing launches and focus can't leave it. */
    private fun editKey(e: KeyEvent, confirm: Boolean): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_BACK) {
            if (e.action == KeyEvent.ACTION_UP) endEdit()
            return true
        }
        val v = currentFocus ?: return false
        val i = appRow.indexOfChild(v)
        if (confirm) {
            if (e.action == KeyEvent.ACTION_UP && i in 0 until appTileCount()) {
                if (moveOnce) endEdit() else setGrabbed(if (grabbed === v) null else v)
            }
            return true
        }
        if (e.action != KeyEvent.ACTION_DOWN) return e.keyCode in EDIT_KEYS
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> return true
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val j = if (e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) i - 1 else i + 1
                if (j !in 0 until appTileCount()) return true
                if (grabbed === v) {
                    // Swap layout params too, so the first tile keeps no leading margin.
                    val other = appRow.getChildAt(j)
                    val lp = v.layoutParams
                    v.layoutParams = other.layoutParams
                    other.layoutParams = lp
                    appRow.removeViewAt(i)
                    appRow.addView(v, j)
                    v.requestFocus()
                    v.post { glideStrip(v) } // focus didn't change, so nothing else moves the track
                    return true
                }
                return false // not holding anything: normal focus movement within the row
            }
        }
        return false
    }

    /** Apps left out of the home row; they stay in All apps. New installs show until hidden. */
    /** Every home row that can be turned off: the fixed ones, then the app rows found so far. */
    private fun rowChoices() = listOf(
        "Continue watching" to "watch", "Apps" to "apps", "Consoles & inputs" to "consoles",
        "30nama" to "nama", "YouTube" to "youtube", "Spotify" to "spotify",
    ) + cardRows.keys.mapNotNull { key -> apps.firstOrNull { it.pkg == key }?.let { it.label to key } }

    private fun pickRows() = toggles("Rows", rowChoices(), "rows_off", "On", "Off") {
        rowKeys.clear()
        refreshWatchNext()
        renderApps()
        renderSettings(ROWS_ROW)
    }

    private fun rowHiddenSet() = prefs.getStringSet("row_hidden", emptySet())!!

    private fun pickRowApps() {
        val list = sortedApps()
        toggles("Apps in row", list.map { it.label to it.pkg }, "row_hidden", "Shown", "Hidden") {
            renderApps()
            renderSettings(7)
        }
    }

    /** A page of on/off rows saved as the set of keys that are off; [done] runs on Back. */
    private fun toggles(title: String, items: List<Pair<String, String>>, pref: String, on: String, off: String, focus: Int = 0, done: () -> Unit) {
        val offSet = prefs.getStringSet(pref, emptySet())!!
        showPage(title, items.mapIndexed { i, (name, key) ->
            page(name, if (key in offSet) off else on) {
                prefs.edit().putStringSet(pref, if (key in offSet) offSet - key else offSet + key).apply()
                toggles(title, items, pref, on, off, i, done)
            }
        }, focus, back = done)
    }

    private fun saveOrder(pkgs: List<String>) {
        prefs.edit().putString("order", pkgs.joinToString(",")).putString("sort", "manual").apply()
    }

    private fun launch(a: App) {
        prefs.edit().putInt("n_" + a.pkg, prefs.getInt("n_" + a.pkg, 0) + 1).putLong("t_" + a.pkg, System.currentTimeMillis()).apply()
        if (prefs.getString("sort", "manual") == "usage") appsDirty = true
        try {
            startActivity(a.launch, zoomFromFocus())
        } catch (e: Exception) {
            packageManager.getLeanbackLaunchIntentForPackage(a.pkg)?.let { startActivity(it, zoomFromFocus()) }
        }
    }

    /** Apps open by growing out of the tile or card that launched them. */
    private fun zoomFromFocus() = currentFocus?.let { ActivityOptions.makeScaleUpAnimation(it, 0, 0, it.width, it.height).toBundle() }

    /** Back from an app: the home screen rises into place instead of popping in. */
    override fun onRestart() {
        super.onRestart()
        val c = findViewById<View>(R.id.content)
        c.alpha = 0f
        c.translationY = dp(18).toFloat()
        c.animate().alpha(1f).translationY(0f).setDuration(520).setInterpolator(EMPH).start()
    }

    // ---------- all apps ----------

    private fun openAllApps() {
        val sort = findViewById<LinearLayout>(R.id.allAppsSort)
        sort.background = glass(dp(18), if (dark) 0x14FFFFFF else 0x14000000) // the segmented control's track
        fillSorts(sort)
        fillAllApps()
        allApps.scrollTo(0, 0)
        allApps.alpha = 0f
        allApps.scaleX = 0.97f
        allApps.scaleY = 0.97f
        allApps.visibility = View.VISIBLE
        allApps.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(340).setInterpolator(EMPH)
            .withEndAction { if (allApps.visibility == View.VISIBLE) homeLayers.forEach { it.visibility = View.INVISIBLE } }.start()
        homeFocusable(false)
        allAppsGrid.getChildAt(0)?.requestFocus()
    }

    /** The grid, five card-size tiles across with names, in the chosen order. */
    private fun fillAllApps() {
        allAppsGrid.removeAllViews()
        val gap = dp(16)
        val width = (resources.displayMetrics.widthPixels - dp(96) - gap * 4) / 5
        for (a in allAppsSorted()) {
            val t = appTile(a, width) // holding OK here pins it to the row (see onHold)
            allAppsGrid.addView(t, GridLayout.LayoutParams().apply {
                this.width = width
                height = GridLayout.LayoutParams.WRAP_CONTENT
                setMargins(0, 0, gap, gap)
            })
        }
    }

    private fun allSort() = prefs.getString("all_sort", "home")!!

    private fun allAppsSorted(): List<App> = when (allSort()) {
        "name" -> apps.sortedBy { it.label.lowercase() }
        "used" -> apps.sortedByDescending { prefs.getInt("n_" + it.pkg, 0) }
        "recent" -> apps.sortedByDescending { prefs.getLong("t_" + it.pkg, 0) }
        "installed" -> apps.sortedByDescending { installedAt(it.pkg) }
        else -> sortedApps()
    }

    /** First install time; 0 for HDMI devices, which sort last. */
    private fun installedAt(pkg: String) = try {
        packageManager.getPackageInfo(pkg, 0).firstInstallTime
    } catch (e: PackageManager.NameNotFoundException) {
        0L
    }

    private fun fillSorts(sort: LinearLayout) {
        sort.removeAllViews()
        for ((key, name) in ALL_SORTS) sort.addView(sortPill(key, name), LinearLayout.LayoutParams(WRAP_CONTENT, dp(28)))
    }

    /**
     * One segment of the iOS-style segmented control: clear at rest, the current one a raised lighter segment,
     * the focused one white with dark ink. Picking one reorders the grid and keeps focus on the segment.
     */
    private fun sortPill(key: String, name: String): View {
        val current = key == allSort()
        val rest = if (!current) 0 else if (dark) 0x33FFFFFF else Color.WHITE
        val bg = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(rest)
        }
        return label(name, 13f).apply {
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(16), 0, dp(16), 0)
            alpha = if (current) 1f else 0.75f
            background = bg
            isFocusable = true
            isClickable = true
            setOnFocusChangeListener { _, focused ->
                bg.setColor(if (focused) focusFill else rest)
                setTextColor(if (focused) focusInk else fg)
                alpha = if (focused || current) 1f else 0.75f
            }
            setOnClickListener {
                prefs.edit().putString("all_sort", key).apply()
                val sort = parent as LinearLayout
                val at = sort.indexOfChild(this)
                fillSorts(sort)
                fillAllApps()
                sort.getChildAt(at).requestFocus()
            }
        }
    }
    private fun closeAllApps() {
        allApps.visibility = View.GONE // instant, like the settings panel: key handling reads "visible" as "open"
        homeLayers.forEach { it.visibility = View.VISIBLE }
        if (hero.alpha == 1f && hero.drawable != null) findViewById<View>(R.id.wallpaper).visibility = View.INVISIBLE // under the art, see showArt
        homeFocusable(true)
        renderApps()
        findViewById<View>(R.id.allAppsButton).requestFocus() // back where it was opened from
    }

    // ---------- screensaver ----------
    // Gallery on: remember whatever screensaver was set (Aerial Views here) and switch to ours.
    // Gallery off: put that one back. Needs the adb-granted WRITE_SECURE_SETTINGS, like the Home service.

    private fun gallery() = ComponentName(this, GalleryDream::class.java).flattenToString()

    private fun currentScreensaver() = Settings.Secure.getString(contentResolver, "screensaver_components").orEmpty()

    // Compared as components: set by hand (adb, Android's settings) it may be stored in the short form.
    private fun galleryOn() = ComponentName.unflattenFromString(currentScreensaver()) == ComponentName(this, GalleryDream::class.java)

    private fun screensaverName(component: String = currentScreensaver()): String {
        val pkg = ComponentName.unflattenFromString(component)?.packageName ?: return "Off"
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            "System default"
        }
    }

    /** The folder the Gallery screensaver shows instead of paintings, or null for paintings. */
    private fun photoScreensaver() = prefs.getString("ss_folder", null)

    private fun pickScreensaver(focus: Int = 0) {
        val paintings = galleryOn() && photoScreensaver() == null
        val photos = galleryOn() && photoScreensaver() != null
        val other = if (galleryOn()) prefs.getString("prev_screensaver", "").orEmpty() else currentScreensaver()
        showPage("Screensaver", listOf(
            page("Gallery paintings", if (paintings) CHOSEN else if (downloading) "Downloading…" else PAINTINGS_MB) {
                useGallery(null)
                pickScreensaver(0)
            },
            page("Your photos", if (photos) "${wallpaperName(photoScreensaver()!!)} $CHOSEN" else "") {
                pickFolder({ pickScreensaver(1) }) { choice ->
                    useGallery(choice)
                    pickScreensaver(1)
                }
            },
            page(screensaverName(other), if (galleryOn()) "" else CHOSEN) {
                if (galleryOn()) stopGallery()
                pickScreensaver(2)
            },
        ), focus) { renderSettings(SCREENSAVER_ROW) }
    }

    /** Gallery on, with paintings ([folder] null, downloaded first if needed) or a folder of photos. */
    private fun useGallery(folder: String?) {
        prefs.edit().putString("ss_folder", folder).apply()
        if (folder == null && !paintingsReady(this)) return downloadPaintings { useGallery(null) }
        if (folder != null) paintingsDir(this).deleteRecursively() // not needed for photos: give the space back
        if (galleryOn()) return
        if (checkSelfPermission(WRITE_SECURE) != PackageManager.PERMISSION_GRANTED) {
            // Without the adb permission Koolest can't switch it itself; Android's own list has "Gallery".
            Toast.makeText(this, "Choose \"Gallery\" as the screensaver here", Toast.LENGTH_LONG).show()
            return openScreensaverSettings()
        }
        prefs.edit().putString("prev_screensaver", currentScreensaver()).apply()
        Settings.Secure.putString(contentResolver, "screensaver_components", gallery())
        Settings.Secure.putInt(contentResolver, "screensaver_enabled", 1)
    }

    /**
     * Switches on Koolest's notification listener so the screensaver can show what's playing. Since Android 10 the
     * system keeps its own list of allowed listeners, so the secure setting alone does nothing: the sideload build
     * asks through the TV's adb, the Play build opens Android's notification access page (which some TVs don't have).
     */
    private fun allowMusic() {
        prefs.edit().putBoolean("ss_music", true).apply()
        if (SelfAdb.AVAILABLE) return viaAdb(MUSIC_ROW, listOf("cmd notification allow_listener ${ComponentName(this, MediaListener::class.java).flattenToString()}")) {
            if (musicAccess(this)) null else "The TV didn't allow it. Try again."
        }
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "This TV has no notification access page", Toast.LENGTH_LONG).show()
        }
    }

    /** Back to whatever screensaver was set before Gallery. */
    private fun stopGallery() {
        if (checkSelfPermission(WRITE_SECURE) != PackageManager.PERMISSION_GRANTED) return openScreensaverSettings()
        Settings.Secure.putString(contentResolver, "screensaver_components", prefs.getString("prev_screensaver", "") ?: "")
        paintingsDir(this).deleteRecursively() // another screensaver is in charge: give the space back
    }

    /** Android's screensaver settings (timing, sleep): plain Android TV and Google TV name them differently. */
    private fun openScreensaverSettings() {
        for (action in listOf(Settings.ACTION_DREAM_SETTINGS, "com.google.android.tv.settings.ambient")) {
            try {
                return startActivity(Intent(action))
            } catch (e: Exception) {
            }
        }
        openSettings(Settings.ACTION_SETTINGS)
    }

    @Volatile private var downloading = false

    /**
     * Fetches the Gallery paintings into a temporary folder and only swaps it in once every file arrived,
     * so a dropped connection never leaves a half-filled Gallery behind.
     */
    private fun downloadPaintings(then: () -> Unit) {
        if (downloading) return
        downloading = true
        Toast.makeText(this, "Downloading Gallery paintings ($PAINTINGS_MB)…", Toast.LENGTH_SHORT).show()
        Thread {
            val part = File(filesDir, "paintings.part").apply { deleteRecursively(); mkdirs() }
            val ok = try {
                for (f in PAINTING_FILES) {
                    val have = File(paintingsDir(this), f) // after an update that adds paintings, keep the ones already here
                    if (have.exists()) have.copyTo(File(part, f)) else openStream(PAINTINGS_URL + f).use { input ->
                        File(part, f).outputStream().use { input.copyTo(it) }
                    }
                }
                paintingsDir(this).deleteRecursively()
                part.renameTo(paintingsDir(this))
            } catch (e: Exception) {
                android.util.Log.w("Koolest", "paintings download failed ($e)")
                part.deleteRecursively()
                false
            }
            ui.post {
                downloading = false
                if (ok) then() else Toast.makeText(this, "Couldn't download the paintings. Check the connection and try again.", Toast.LENGTH_LONG).show()
                if (settingsScrim.visibility == View.VISIBLE) renderSettings(SCREENSAVER_ROW)
            }
        }.start()
    }

    /** Starts whichever screensaver is set, through SystemUI's "dream now" entry point. */
    private fun testScreensaver() {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui", "com.android.systemui.Somnambulator"))
        } catch (e: Exception) {
            Toast.makeText(this, "This TV can't start the screensaver on demand", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- updates (GitHub releases) ----------
    // Checked once a day from the home screen; a new version is announced once, then waits in settings.
    // The APK streams straight into an install session, so there's no temporary file to clean up.

    @Volatile private var checking = false
    @Volatile private var updating = false

    private fun myVersion() = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

    /** The newer version a check found, if any (null again once it's installed). */
    private fun availableUpdate() = prefs.getString("upd_ver", null)?.takeIf { newerVersion(it, myVersion()) }

    private fun checkUpdate(force: Boolean) {
        if (checking || !force && System.currentTimeMillis() - prefs.getLong("upd_time", 0) < 24 * 3600_000L) return
        checking = true
        if (force) renderSettings(UPDATE_ROW)
        io.execute {
            val latest = try {
                getJson("https://api.github.com/repos/$REPO/releases/latest").getString("tag_name").removePrefix("v")
            } catch (e: Exception) {
                android.util.Log.w("Koolest", "update check failed ($e)")
                null
            }
            if (latest != null) prefs.edit().putString("upd_ver", latest).putLong("upd_time", System.currentTimeMillis()).apply()
            ui.post {
                checking = false
                val ver = availableUpdate()
                if (ver != null && (force || prefs.getString("upd_told", null) != ver)) {
                    prefs.edit().putString("upd_told", ver).apply()
                    if (!force) Toast.makeText(this, "Koolest $ver is available. Update it in Koolest settings.", Toast.LENGTH_LONG).show()
                } else if (force) {
                    Toast.makeText(this, if (latest == null) "Couldn't check for updates. Check the connection." else "Koolest is up to date", Toast.LENGTH_SHORT).show()
                }
                if (settingsScrim.visibility == View.VISIBLE) renderSettings(UPDATE_ROW)
            }
        }
    }

    private fun installUpdate(ver: String) {
        if (updating) return
        if (!packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, "Turn on Koolest in the next list, then press Updates again", Toast.LENGTH_LONG).show()
            return openSettings(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
        }
        updating = true
        renderSettings(UPDATE_ROW)
        val installer = packageManager.packageInstaller
        io.execute {
            val ok = try {
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setAppPackageName(packageName)
                // Android 12+ can skip the confirm box when we're the app's installer; otherwise Android asks.
                if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    try {
                        openStream("https://github.com/$REPO/releases/download/v$ver/Koolest.apk").use { input ->
                            session.openWrite("Koolest.apk", 0, -1).use { out ->
                                input.copyTo(out)
                                session.fsync(out)
                            }
                        }
                    } catch (e: Exception) {
                        session.abandon()
                        throw e
                    }
                    val done = PendingIntent.getActivity(this, 0,
                        Intent(this, MainActivity::class.java).setAction(UPDATE_STATUS),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                    session.commit(done.intentSender)
                }
                true
            } catch (e: Exception) {
                android.util.Log.w("Koolest", "update download failed ($e)")
                false
            }
            ui.post {
                if (!ok) {
                    updating = false
                    Toast.makeText(this, "Couldn't download the update. Check the connection and try again.", Toast.LENGTH_LONG).show()
                }
                if (settingsScrim.visibility == View.VISIBLE) renderSettings(UPDATE_ROW)
            }
        }
    }

    /** The install session reports back here: show Android's confirm box, or say why it failed. */
    private fun installStatus(intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.let { startActivity(it) }
            return
        }
        updating = false
        if (status != PackageInstaller.STATUS_SUCCESS) {
            val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            android.util.Log.w("Koolest", "update install failed: $status $why")
            if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                Toast.makeText(this, "Update failed to install", Toast.LENGTH_LONG).show()
            }
        }
        if (settingsScrim.visibility == View.VISIBLE) renderSettings(UPDATE_ROW)
    }

    // ---------- weather (under the date) ----------
    // Location comes from the public IP (ipwho.is): a TV has no GPS and Android TV no location service.
    // Weather is Open-Meteo. Both are free and keyless. We always fetch Celsius and convert, so
    // switching units needs no refetch. The last reading is kept in prefs and shows instantly.

    private val weatherTick = object : Runnable {
        override fun run() {
            refreshWeather()
            ui.postDelayed(this, 30 * 60_000L)
        }
    }

    private fun refreshWeather() {
        if (prefs.getString("weather", "off") == "off") return
        if (System.currentTimeMillis() - prefs.getLong("wx_time", 0) < 25 * 60_000L && prefs.contains("wx_feel")) return
        io.execute {
            try {
                locate(prefs)
                if (!prefs.contains("geo_lat")) return@execute
                val now = getJson("https://api.open-meteo.com/v1/forecast?latitude=${prefs.getFloat("geo_lat", 0f)}" +
                    "&longitude=${prefs.getFloat("geo_lon", 0f)}&current=temperature_2m,apparent_temperature,is_day,weather_code").getJSONObject("current")
                prefs.edit()
                    .putFloat("wx_temp", now.getDouble("temperature_2m").toFloat())
                    .putFloat("wx_feel", now.getDouble("apparent_temperature").toFloat())
                    .putBoolean("wx_day", now.getInt("is_day") == 1)
                    .putInt("wx_code", now.getInt("weather_code"))
                    .putLong("wx_time", System.currentTimeMillis()).apply()
                ui.post { showWeather() }
            } catch (e: Exception) {
                android.util.Log.w("Koolest", "weather failed ($e)") // keep the last reading; next tick retries
            }
        }
    }

    // An IP's location is wherever the provider routes it, sometimes a whole region off (Rome showing as
    // Sicily), so you can pick the city instead. Open-Meteo's geocoding, also free and keyless.

    private fun manualCity() = prefs.getBoolean("geo_manual", false)

    private class City(val name: String, val where: String, val lat: Float, val lon: Float)

    /** Automatic first, then the search box; a search lists what it found under them. */
    private fun pickCity(query: String = "", found: List<City>? = null, status: String = "") {
        val field = EditText(this).apply {
            id = View.generateViewId()
            setText(query)
            setSelection(query.length)
            hint = "Type a city, then Search"
            textSize = 14f
            setTextColor(fg)
            setHintTextColor(Color.argb(120, Color.red(fg), Color.green(fg), Color.blue(fg)))
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(14), 0, dp(14), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.argb(80, Color.red(fg), Color.green(fg), Color.blue(fg)))
            }
            setOnEditorActionListener { v, _, _ -> searchCity(v.text.toString().trim()); true }
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, 0)
            addView(field, LinearLayout.LayoutParams(MATCH_PARENT, dp(44)))
            if (status.isNotEmpty()) addView(label(status, 12f).apply { alpha = 0.6f; setPadding(dp(14), dp(8), 0, 0) })
        }
        val rows = listOf(page("Automatic", if (manualCity()) "" else CHOSEN) {
            prefs.edit().remove("geo_manual").remove("geo_time").remove("wx_time").apply()
            refreshWeather()
            renderSettings(CITY_ROW)
        }) + found.orEmpty().map { c ->
            page(c.name, c.where) {
                prefs.edit().putBoolean("geo_manual", true).putString("geo_city", c.name)
                    .putFloat("geo_lat", c.lat).putFloat("geo_lon", c.lon).remove("wx_time").apply()
                refreshWeather()
                renderSettings(CITY_ROW)
            }
        }
        showPage("Weather location", rows, if (found.isNullOrEmpty()) 0 else 1, top = top) { renderSettings(CITY_ROW) }
        // showPage pins Up on the first row to itself; here Up goes to the search box.
        settingsList.getChildAt(1).nextFocusUpId = field.id
        if (found.isNullOrEmpty()) field.requestFocus()
        // Results in: put the keyboard away, it would cover them.
        else getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(field.windowToken, 0)
    }

    private fun searchCity(query: String) {
        if (query.isEmpty()) return
        pickCity(query, status = "Searching…")
        io.execute {
            val found = try {
                val r = getJson("https://geocoding-api.open-meteo.com/v1/search?count=8&format=json" +
                    "&language=${java.util.Locale.getDefault().language}&name=${URLEncoder.encode(query, "UTF-8")}")
                val list = r.optJSONArray("results")
                (0 until (list?.length() ?: 0)).map { i ->
                    val c = list!!.getJSONObject(i)
                    City(c.getString("name"), listOf(c.optString("admin1"), c.optString("country")).filter { it.isNotEmpty() }.joinToString(", "),
                        c.getDouble("latitude").toFloat(), c.getDouble("longitude").toFloat())
                }
            } catch (e: Exception) {
                null
            }
            ui.post {
                // Only if the page is still open: Back may have left it while the search ran.
                if (settingsScrim.visibility != View.VISIBLE || findViewById<TextView>(R.id.settingsTitle).text.toString() != "Weather location") return@post
                when {
                    found == null -> pickCity(query, status = "Couldn't search. Check the connection and try again.")
                    found.isEmpty() -> pickCity(query, status = "No places called \"$query\".")
                    else -> pickCity(query, found)
                }
            }
        }
    }

    private fun showWeather() {
        val view = findViewById<View>(R.id.weather)
        val unit = prefs.getString("weather", "off")
        if (unit == "off" || !prefs.contains("wx_temp")) {
            view.visibility = View.GONE
            return
        }
        fun deg(c: Float) = "${Math.round(if (unit == "f") c * 9 / 5 + 32 else c)}°"
        val code = prefs.getInt("wx_code", 0)
        findViewById<View>(R.id.weather).contentDescription = weatherWord(code)
        findViewById<ImageView>(R.id.wxIcon).setImageResource(weatherIcon(code, prefs.getBoolean("wx_day", true)))
        findViewById<TextView>(R.id.wxTemp).text = deg(prefs.getFloat("wx_temp", 0f)) // the icon says what the sky is doing
        findViewById<TextView>(R.id.wxDesc).text = listOfNotNull(
            if (prefs.contains("wx_feel")) "Feels ${deg(prefs.getFloat("wx_feel", 0f))}" else null,
            prefs.getString("geo_city", null),
        ).joinToString(" · ")
        view.visibility = View.VISIBLE
    }

    private fun getJson(url: String) = JSONObject(openStream(url).use { it.readBytes().decodeToString() })

    // ---------- continue watching (Android TV Watch Next) ----------

    /** Fills every card row (Continue watching, 30nama, YouTube, Spotify, other apps' rows) from the TV's listings. */
    private fun refreshWatchNext() {
        if (checkSelfPermission(TV_LISTINGS) != PackageManager.PERMISSION_GRANTED) {
            cardRows.values.forEach { showCards(it, emptyList()) }
            return
        }
        io.execute {
            val lists = try {
                queryContent()
            } catch (e: Exception) {
                android.util.Log.w("Koolest", "TV listings failed", e)
                return@execute
            }
            ui.post {
                val off = rowsOff()
                for ((key, list) in lists) {
                    val row = cardRows.getOrPut(key) { makeRow(apps.firstOrNull { it.pkg == key }?.label ?: key) }
                    showCards(row, if (key in off) emptyList() else list)
                }
                for ((key, row) in cardRows) if (key !in lists) showCards(row, emptyList()) // stopped publishing
            }
        }
    }

    /**
     * Every card row, by key: "watch", "nama", "youtube", "spotify", then one per other app (its package) that
     * publishes to the home screen, most used first. Continue watching is the system Watch Next list plus any
     * app's own "Continue Watching" channel (Stremio publishes one instead of using Watch Next; its rows carry
     * no times, so they use the time the app was last opened from Koolest). It leaves out browsers, which
     * list web pages there. The apps used most come first, each with its newest [PER_APP] shows.
     */
    private fun queryContent(): Map<String, List<Prog>> {
        val hidden = prefs.getStringSet("hidden_watch", emptySet())!!
        val channels = HashMap<Long, Pair<String, String>>() // id → package, name
        contentResolver.query(TvContract.Channels.CONTENT_URI, arrayOf("_id", "package_name", "display_name"), null, null, null)?.use { c ->
            while (c.moveToNext()) c.getString(1)?.takeIf { it != packageName }?.let { channels[c.getLong(0)] = it to c.getString(2).orEmpty() }
        }
        // One read for every app's programs. Where the TV only allows reading them channel by channel, do that.
        val previews = readPrograms(TvContract.PreviewPrograms.CONTENT_URI, hidden).takeIf { it.isNotEmpty() || channels.isEmpty() }
            ?: channels.keys.flatMap { readPrograms(TvContract.buildPreviewProgramsUriForChannel(it), hidden) }
        val (resume, shelves) = previews.filter { it.channel in channels }.partition { channels[it.channel]!!.second.lowercase() in RESUME_CHANNELS }
        val watchNext = readPrograms(TvContract.WatchNextPrograms.CONTENT_URI, hidden).map { it.time to it.prog } +
            resume.map { prefs.getLong("t_${it.prog.pkg}", 0) to it.prog }
        publishingPkgs = watchNext.mapNotNull { it.second.pkg }.toSet()
        val browsers = browsers()
        val off = prefs.getStringSet("cw_off", emptySet())!!
        val watching = watchNext.filter { it.second.pkg !in off && it.second.pkg !in browsers }.distinctBy { it.second.intent ?: it.second.title }
        val uses = { pkg: String? -> prefs.getInt("n_$pkg", 0) }
        // Each app's own shelves, in the order the app made its channels.
        val shelf = shelves.sortedBy { it.channel }.groupBy({ it.prog.pkg }, { it.prog })
        fun shelfOf(pkgs: Set<String>) = pkgs.flatMap { shelf[it].orEmpty() }.distinctBy { it.intent ?: it.title }.take(20)
        val installed = apps.map { it.pkg }.toSet()
        val others = shelf.keys.filterNotNull()
            .filter { it in installed && it !in YOUTUBE && it !in NAMA && it !in SPOTIFY && it !in browsers }
            .sortedByDescending(uses)
        return linkedMapOf(
            "watch" to byAppUse(watching, PER_APP, uses) { it.pkg }.take(20),
            "nama" to (watching.filter { it.second.pkg in NAMA }.sortedByDescending { it.first }.map { it.second } + shelfOf(NAMA))
                .distinctBy { it.intent ?: it.title }.take(20),
            "youtube" to shelfOf(YOUTUBE),
            "spotify" to shelfOf(SPOTIFY),
        ) + others.associateWith { shelfOf(setOf(it)) }
    }

    /** Apps that open any web page: their Watch Next entries are tabs, not shows. */
    private fun browsers(): Set<String> =
        packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE), PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }.toSet() - YOUTUBE - NAMA - SPOTIFY

    /** Home rows turned off in settings: "watch", "apps", "consoles", "nama", "youtube", "spotify" or an app's package. */
    private fun rowsOff() = prefs.getStringSet("rows_off", emptySet())!!

    /** Apps last seen publishing, so the settings list includes them even without the permission check. */
    @Volatile private var publishingPkgs = emptySet<String>()

    /**
     * Apps that can feed Continue Watching: anything allowed to write TV listings, plus anything
     * actually publishing now. Built from installed apps each time, so new apps appear on their own.
     */
    private fun watchSources(): List<App> = apps.filter { a ->
        a.pkg in publishingPkgs || try {
            packageManager.getPackageInfo(a.pkg, PackageManager.GET_PERMISSIONS)
                .requestedPermissions?.contains(WRITE_EPG) == true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }.sortedBy { it.label.lowercase() }

    private fun pickWatchSources() {
        val sources = watchSources()
        if (sources.isEmpty()) {
            Toast.makeText(this, "No installed apps support Continue watching", Toast.LENGTH_SHORT).show()
            return
        }
        toggles("Apps", sources.map { it.label to it.pkg }, "cw_off", "On", "Off") {
            rowKeys.clear()
            refreshWatchNext()
            renderSettings(11)
        }
    }

    /** A listing row: last engagement time (0 for channel rows, which don't have it), its channel (0 for Watch Next) and the item. */
    private class Listed(val time: Long, val channel: Long, val prog: Prog)

    private fun readPrograms(uri: Uri, hidden: Set<String>): List<Listed> {
        val out = ArrayList<Listed>()
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            fun str(col: String) = c.getColumnIndex(col).let { if (it < 0 || c.isNull(it)) null else c.getString(it) }
            fun num(col: String) = c.getColumnIndex(col).let { if (it < 0 || c.isNull(it)) 0L else c.getLong(it) }
            while (c.moveToNext()) {
                if (str("browsable") == "0") continue
                val title = str("title") ?: continue
                if (hideKey(str("package_name"), title) in hidden) continue
                val season = str("season_display_number")
                val episode = str("episode_display_number")
                val duration = num("duration_millis")
                val position = num("last_playback_position_millis")
                val details = listOfNotNull(
                    if (season != null && episode != null) "S$season · E$episode" else null,
                    // Under a second in means "not really started": no "time left", no bar.
                    if (duration > 0 && position in 1000 until duration) timeLeft(duration - position) else null,
                ).joinToString(" · ")
                val short = str("short_description").orEmpty()
                val sub = details.ifEmpty { short } // e.g. a video's channel and age
                val plot = str("long_description") ?: short.takeIf { details.isNotEmpty() }.orEmpty()
                val progress = if (duration > 0 && position >= 1000) (position * 100 / duration).toInt().coerceIn(0, 100) else 0
                val prog = Prog(title, sub, progress, str("thumbnail_uri") ?: str("poster_art_uri"), str("intent_uri"), str("package_name"), plot)
                out += Listed(num("last_engagement_time_utc_millis"), num("channel_id"), prog)
            }
        }
        return out
    }

    private fun hideKey(pkg: String?, title: String) = "$pkg/$title"

    /** We can't delete another app's Watch Next row, so hiding is a local filter. */
    private fun hideWatch(p: Prog) {
        val hidden = prefs.getStringSet("hidden_watch", emptySet())!! + hideKey(p.pkg, p.title)
        prefs.edit().putStringSet("hidden_watch", hidden).apply()
        Toast.makeText(this, "Hidden from the home screen", Toast.LENGTH_SHORT).show()
        refreshWatchNext()
    }

    /** Small source-app mark for the details line: black logo when we have one, else greyscale icon. */
    private fun sourceIcon(pkg: String?): Drawable? {
        if (pkg == null) return null
        LOGOS[pkg]?.takeIf { !it.second }?.let { return getDrawable(it.first)!!.mutate().apply { setTint(fg) } }
        return try {
            packageManager.getApplicationIcon(pkg).mutate().apply { colorFilter = GREYSCALE }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun timeLeft(ms: Long): String {
        val min = ms / 60_000
        return if (min >= 60) "${min / 60} hr ${min % 60} min left" else if (min > 0) "$min min left" else "Almost done"
    }

    private fun showCards(row: LinearLayout, list: List<Prog>) {
        val key = list.joinToString("|") { "${it.title}/${it.sub}/${it.progress}/${it.image}" }
        if (key == rowKeys[row]) { // unchanged: keep views (and focus) as they are, just fill in missing posters
            retryPosters()
            return
        }
        rowKeys[row] = key
        val focusedAt = row.indexOfChild(currentFocus?.parent as? View)
        row.removeAllViews()
        rowOf(row)!!.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        // ~16 ms per card here and only four show at once: build them four per frame, not all 20 in one.
        fun addCards(from: Int) {
            if (rowKeys[row] != key) return // a newer list replaced this one
            list.subList(from, minOf(from + 4, list.size)).forEach { row.addView(watchCard(it, row.childCount)) }
            if (from + 4 < list.size) ui.post { addCards(from + 4) }
        }
        addCards(0)
        if (focusedAt >= 0) {
            ((row.getChildAt(focusedAt.coerceAtMost(row.childCount - 1)) as? LinearLayout)?.getChildAt(0) ?: firstRowEntry())?.requestFocus()
        } else if (!navigated && list.isNotEmpty()) {
            firstRowEntry()?.requestFocus() // still on the opening screen: start at the top row
        }
    }
    /**
     * 30nama's landscape card, 400 × 225 at 1080p: the art with a 6 px progress bar inset 12 inside it, and the
     * details under it ("S1 · E5 · 23 min left"). The title is on the billboard. Focus: scale 1.06, a 4 px white
     * ring 6 px outside the card, and a lift shadow.
     */
    private fun watchCard(p: Prog, index: Int): View {
        val gapBefore = index > 0
        val w = dp(200)
        val radius = dp(6).toFloat()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            layoutParams = LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                if (gapBefore) marginStart = dp(ITEM_GAP)
            }
        }
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                cornerRadius = radius
                setColor(if (dark) 0xFF20242F.toInt() else 0xFFDDE1E5.toInt())
            }
            clipToOutline = true
        }
        val frame = FrameLayout(this).apply {
            addView(img, MATCH_PARENT, MATCH_PARENT)
            if (p.progress > 0) addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = p.progress
                progressDrawable = progressBarDrawable()
            }, FrameLayout.LayoutParams(MATCH_PARENT, dp(3), Gravity.BOTTOM).apply { setMargins(dp(6), 0, dp(6), dp(6)) })
        }
        style(frame, 0, card = false, lift = 1f, bare = true, onFocus = cardFocus(frame, radius))
        frame.contentDescription = p.title
        frame.setOnClickListener { open(p) }
        frame.setTag(R.id.watchRow, p) // holding OK hides it (see onHold)
        col.addView(frame, LinearLayout.LayoutParams(w, w * 9 / 16))
        col.addView(label(p.sub.ifEmpty { p.title }, 13f).apply {
            alpha = 0.72f
            setPadding(0, dp(14), 0, 0)
        })
        img.tag = p.image
        if (index < EAGER_POSTERS) loadPoster(img, p.image) // the rest load as focus comes near (loadNear)
        return col
    }

    /** A card's picture (column → frame → image), or null for an app tile. */
    private fun posterOf(card: View?) = ((card as? ViewGroup)?.getChildAt(0) as? ViewGroup)?.getChildAt(0) as? ImageView

    /** Loads the pictures of the cards from [at] to a screenful past it, so they're there before focus arrives. */
    private fun loadNear(strip: ViewGroup, at: Int) {
        for (i in at until minOf(at + EAGER_POSTERS, strip.childCount)) {
            val img = posterOf(strip.getChildAt(i)) ?: return
            if (img.drawable == null) loadPoster(img, img.tag as? String)
        }
    }
    // ---------- rows, menu and billboard (the 30nama home layout) ----------
    // The rows stack scrolls so the focused row's title sits at y 640 px, the rows above fading out, and
    // each track glides so its focused item sits at the content edge. Above them the billboard names
    // what's focused; a card's picture, once focus rests on it, fades in behind everything.

    /** One home row: its name over a sideways strip of tiles or cards. Hidden until it has something; returns the strip. */
    private fun makeRow(title: String): LinearLayout {
        // The strip holds the room for focus lifts and rings (FOCUS_ROOM round it): its track clips, and a clipping
        // parent also clips each child to its own bounds.
        val strip = LinearLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(dp(FOCUS_ROOM), dp(14), dp(FOCUS_ROOM), dp(18))
        }
        rows.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            // 32 px bold, text-2 at rest and text-1 on the focused row (see glideRows).
            addView(label(title, 16f).apply {
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                alpha = 0.72f
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(92) })
            // The track starts at the content edge (x 184 px) and glides so the focused item sits there,
            // the ones before it sliding on under the menu; it runs to the screen's end edge.
            addView(SnapScrollView(context, null).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
                setPadding(dp(92 - FOCUS_ROOM), 0, dp(48 - FOCUS_ROOM), 0) // items still start at x 92
                addView(strip)
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        })
        return strip
    }
    private fun kids(g: ViewGroup) = (0 until g.childCount).map { g.getChildAt(it) }

    /** The row (child of [rows]) that [v] sits in, or null outside the rows. */
    private fun rowOf(v: View?): View? {
        var x = v
        while (x != null && x.parent !== rows) x = x.parent as? View
        return x
    }

    /** Where focus lands entering [row]: always its first tile or card (a row starts over each time you come back). */
    private fun entry(row: View): View? {
        val first = (((row as ViewGroup).getChildAt(1) as ViewGroup).getChildAt(0) as ViewGroup).getChildAt(0) ?: return null
        return if (first.isFocusable) first else (first as ViewGroup).getChildAt(0)
    }

    private fun firstRowEntry() = kids(rows).firstOrNull { it.visibility == View.VISIBLE }?.let { entry(it) }

    /** Focus moved: open or close the menu, glide its row and track into place, rewind the row left, update the billboard. */
    private fun onHomeFocus(@Suppress("UNUSED_PARAMETER") old: View?, v: View?) {
        val inRail = (v?.parent as? View)?.parent === railItems
        if (inRail != railOpen) setRail(inRail)
        val row = rowOf(v)
        if (row != null) {
            // Moving to another row: the one left slides back to its start, where focus will land on return.
            rowOf(lastRowItem)?.takeIf { it !== row }?.let { left ->
                ObjectAnimator.ofInt((left as ViewGroup).getChildAt(1), "scrollX", 0).apply {
                    setAutoCancel(true)
                    duration = 320
                    interpolator = EMPH
                }.start()
            }
            lastRowItem = v
            rowStack.post { // posted: a row just shown has no position yet
                glideRows(row)
                glideStrip(v!!)
                val item = itemOf(v)
                (item.parent as? ViewGroup)?.let { loadNear(it, it.indexOfChild(item)) }
            }
        }
        if (!inRail) focusShelf(v.takeIf { row != null }) // the billboard stays up behind the open menu
    }

    private fun glideRows(row: View) {
        ObjectAnimator.ofInt(rowStack, "scrollY", row.top).apply {
            setAutoCancel(true)
            duration = 420
            interpolator = EMPH
        }.start()
        for (r in kids(rows)) {
            val a = if (r.top < row.top) 0f else 1f
            if (r.alpha != a) r.animate().alpha(a).setDuration(240).withLayer().start()
            (r as ViewGroup).getChildAt(0).alpha = if (r === row) 1f else 0.72f
        }
    }

    /** Slides [v]'s track so its item sits at the content edge (clamped at the track's end). */
    private fun glideStrip(v: View) {
        val item = itemOf(v)
        val strip = item.parent as View
        val track = strip.parent as View
        val end = (strip.width - (track.width - track.paddingLeft - track.paddingRight)).coerceAtLeast(0)
        ObjectAnimator.ofInt(track, "scrollX", (item.left - strip.paddingLeft).coerceIn(0, end)).apply {
            setAutoCancel(true)
            duration = 320
            interpolator = EMPH
        }.start()
    }

    /**
     * The menu, a taskbar: Search, Home (here), the pinned apps (Koolest settings → Menu apps; YouTube, Spotify and
     * 30nama to start) and All apps last. Rebuilt only when the pinned apps change.
     */
    private fun buildRail() {
        // Once: a menu already set up by hand gets the consoles too (they joined the menu after it was first set).
        val consoles = apps.filter { it.pkg.startsWith(HDMI) && deviceLogo(it.label).res != R.drawable.logo_dev_hdmi }.sortedBy { it.label }.map { it.pkg }
        val saved = prefs.getString("rail_apps", null)
        if (saved != null && consoles.isNotEmpty() && !prefs.getBoolean("rail_consoles", false)) {
            prefs.edit().putBoolean("rail_consoles", true).apply()
            saveRailApps(railApps() + consoles.filter { it !in railApps() })
            return // saveRailApps built it
        }
        val pinned = railApps().mapNotNull { pkg -> apps.firstOrNull { it.pkg == pkg } }
        val key = pinned.joinToString(",") { "${it.pkg}=${it.label}" } // a console's mark follows its name
        if (key == railKey) return
        railKey = key
        railItems.removeAllViews()
        // 33 dp each, squeezed when a long menu would run off the screen (less the two gaps between groups).
        railItemHeight = minOf(dp(33), (resources.displayMetrics.heightPixels - dp(60 + 2 * RAIL_GAP)) / (pinned.size + 3))
        railLabels.clear()
        railItem(R.id.railSearch, getDrawable(R.drawable.rail_search)!!, "Search") { search() }
        railItem(R.id.railHome, getDrawable(R.drawable.rail_home)!!, "Home", current = true) { leaveRail() }
        pinned.forEachIndexed { i, a -> railItem(View.generateViewId(), railIcon(a), a.label, app = true, gap = i == 0) { launch(a) } }
        railItem(R.id.allAppsButton, getDrawable(R.drawable.rail_apps)!!, "All apps", gap = true) { openAllApps() }
        // The capsule and card hug the items (they're siblings of the list, not its parent): 6 dp above and below.
        val h = (pinned.size + 3) * railItemHeight + dp(2 * RAIL_GAP + 12)
        for (id in intArrayOf(R.id.railRest, R.id.railPanel)) findViewById<View>(id).let { it.layoutParams = it.layoutParams.apply { height = h } }
    }

    private var railItemHeight = 0

    /** The pinned menu apps; until changed in settings, the usual streaming apps and every named console. */
    private fun railApps() = prefs.getString("rail_apps", null)?.split(',')?.filter { it.isNotEmpty() }
        ?: (DEFAULT_RAIL.split(',') + apps.filter { it.pkg.startsWith(HDMI) && deviceLogo(it.label).res != R.drawable.logo_dev_hdmi }.sortedBy { it.label }.map { it.pkg })

    /** An app's own icon; a console, a tiny version of its tile (its mark on its colour). */
    private fun railIcon(a: App): Drawable {
        if (!a.pkg.startsWith(HDMI)) return a.icon
        val d = deviceLogo(a.label)
        val mark = getDrawable(d.res)!!.mutate().apply { setTint(if (d.colour != null) d.ink else fg) }
        return LayerDrawable(arrayOf(ColorDrawable(d.colour ?: 0xFF20242F.toInt()), android.graphics.drawable.InsetDrawable(mark, dp(1))))
    }

    private fun saveRailApps(pkgs: List<String>) {
        prefs.edit().putString("rail_apps", pkgs.joinToString(",")).apply()
        buildRail()
    }
    /**
     * One menu item, 68 px tall (a little under 30nama's 72, so a dozen fit): a 40 px icon (text-3, the current one text-1 with a 4 × 28 accent bar at
     * the edge) and its label, shown once the menu opens. Focused, it's a white 300 × 64 pill.
     */
    private fun railItem(id: Int, icon: Drawable, name: String, current: Boolean = false, app: Boolean = false, gap: Boolean = false, action: () -> Unit) {
        val rest = if (current) fg else fg and 0xFFFFFF or 0x75000000
        val img = ImageView(this).apply {
            setImageDrawable(icon)
            if (app) {
                alpha = 0.7f // apps keep their own colours, softened at rest, in a soft rounded square
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, o: android.graphics.Outline) = o.setRoundRect(0, 0, view.width, view.height, view.width * 0.28f)
                }
                clipToOutline = true
                // An iOS-style hairline round the icon, so dark ones (PS3, Crave) keep their edge on the glass.
                foreground = GradientDrawable().apply {
                    cornerRadius = dp(26) * 0.28f
                    setStroke(dp(1), if (dark) 0x33FFFFFF else 0x1A000000)
                }
            } else {
                setColorFilter(rest)
            }
        }
        val text = label(name, 14f).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            alpha = 0f
            railLabels += this
        }
        val pill = GradientDrawable().apply { cornerRadius = dp(16).toFloat() }
        val item = LinearLayout(this).apply {
            this.id = id
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            contentDescription = name
            background = pill
            setPadding(dp(8), 0, dp(10), 0)
            addView(img, LinearLayout.LayoutParams(dp(if (app) 26 else 20), dp(if (app) 26 else 20)).apply { if (!app) marginStart = dp(3) }) // glyphs centred on the icons
            addView(text, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(if (app) 12 else 15) })
            setOnClickListener { action() }
            setOnFocusChangeListener { _, focused ->
                pill.setColor(if (focused) focusFill else 0)
                if (app) img.alpha = if (focused) 1f else 0.7f else img.setColorFilter(if (focused) focusInk else rest)
                text.setTextColor(if (focused) focusInk else fg)
            }
        }
        railItems.addView(FrameLayout(this).apply {
            if (current) addView(View(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(1).toFloat()
                    setColor(ACCENT)
                }
            }, FrameLayout.LayoutParams(dp(2), dp(14), Gravity.CENTER_VERTICAL).apply { marginStart = dp(20) }) // inside the capsule
            addView(item, FrameLayout.LayoutParams(dp(150), railItemHeight - dp(2), Gravity.CENTER_VERTICAL).apply { marginStart = dp(21) })
        }, LinearLayout.LayoutParams(MATCH_PARENT, railItemHeight).apply { if (gap) topMargin = dp(RAIL_GAP) })
    }

    /** Focus entering the menu opens it: panel and labels fade in (labels 20 ms apart), the screen behind dims. */
    private fun setRail(open: Boolean) {
        railOpen = open
        val t = if (open) 1f else 0f
        findViewById<View>(R.id.railPanel).animate().alpha(t).setDuration(240).setInterpolator(EASE_OUT).start()
        findViewById<View>(R.id.railRest).animate().alpha(1 - t).setDuration(240).setInterpolator(EASE_OUT).start() // else it shows through the card
        findViewById<View>(R.id.railDim).animate().alpha(t * 0.7f).setDuration(240).setInterpolator(EASE_OUT).start()
        railLabels.forEachIndexed { i, l ->
            l.animate().alpha(t).setStartDelay(if (open) i * 20L else 0L).setDuration(240).setInterpolator(EASE_OUT).start()
        }
    }

    /** Back to the row item the menu was opened from (Right, Back, or Home in the menu). */
    private fun leaveRail() {
        (lastRowItem?.takeIf { it.isAttachedToWindow } ?: firstRowEntry())?.requestFocus()
    }

    /** The TV's own search (Google TV's search screen), or the assistant where that's what search is. */
    private fun search() {
        for (action in listOf("android.search.action.GLOBAL_SEARCH", Intent.ACTION_ASSIST)) {
            try {
                return startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                // try the next
            }
        }
        Toast.makeText(this, "Search isn't available on this TV", Toast.LENGTH_SHORT).show()
    }
    private val shelfTick = Runnable {
        val v = shelfFor
        val uri = (v?.getTag(R.id.watchRow) as? Prog)?.image
        // A screen-size copy of the picture when we have one; else the card's own while that one loads.
        uri?.let { heroArt.get(it) }?.let { return@Runnable showArt(it) }
        artOf(v)?.let { showArt(it) }
        if (uri == null) return@Runnable
        posterIo.execute {
            val big = try {
                hiRes(uri)?.let { decodeOrNull(it, resources.displayMetrics.widthPixels, Bitmap.Config.ARGB_8888) }
                    ?: decode(uri, resources.displayMetrics.widthPixels, Bitmap.Config.ARGB_8888)
            } catch (e: Exception) {
                null
            } ?: return@execute
            ui.post {
                heroArt.put(uri, big)
                if (shelfFor === v) showArt(big, swap = true)
            }
        }
    }

    /** Billboard pictures at screen size (ARGB: no banding in dark skies), the last couple kept for going back. */
    private val heroArt = object : LruCache<String, Bitmap>(20 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** A bigger version of the same picture where the source has one: YouTube's thumbnails come in sizes. */
    private fun hiRes(uri: String): String? =
        Regex("(https?://i\\d?\\.ytimg\\.com/vi/[^/]+/)(?:hq|mq|sd)default(\\.jpg)").find(uri)?.let { "${it.groupValues[1]}maxresdefault${it.groupValues[2]}" }

    private fun decodeOrNull(uri: String, minWidth: Int, config: Bitmap.Config) = try {
        decode(uri, minWidth, config)
    } catch (e: Exception) {
        null // e.g. no maxres version of this video: the caller falls back to the original
    }

    /**
     * The focused card's own poster. Not from the poster cache: with every card row loaded it holds far
     * fewer posters than are on screen, so the focused one has often been evicted already.
     */
    private fun artOf(v: View?) = ((v as? ViewGroup)?.getChildAt(0) as? ImageView)?.takeIf { it.getTag(R.id.hero) != true }
        ?.drawable.let { it as? android.graphics.drawable.BitmapDrawable }?.bitmap

    private fun focusShelf(v: View?) {
        if (v === shelfFor) return
        shelfFor = v
        ui.removeCallbacks(shelfTick)
        val p = v?.getTag(R.id.watchRow) as? Prog
        val title = p?.title ?: v?.contentDescription?.toString()?.takeIf { rowNames() != "never" }
        if (title == null || editing) {
            shelf.animate().alpha(0f).setDuration(160).setInterpolator(EASE_OUT).start()
        } else {
            val header = ((rowOf(v) as ViewGroup).getChildAt(0) as TextView).text.toString()
            val source = p?.pkg?.let { pkg -> apps.firstOrNull { it.pkg == pkg }?.label }?.takeIf { it != header }
            findViewById<TextView>(R.id.shelfEyebrow).text = listOfNotNull(header, source).joinToString(" · ")
            findViewById<TextView>(R.id.shelfTitle).text = title
            findViewById<TextView>(R.id.shelfMeta).text = p?.sub.orEmpty()
            findViewById<TextView>(R.id.shelfPlot).apply {
                text = p?.plot.orEmpty()
                visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
            }
            shelf.alpha = 0f
            shelf.translationY = dp(10).toFloat()
            shelf.animate().alpha(1f).translationY(0f).setDuration(320).setInterpolator(EMPH).start()
        }
        // The art waits for focus to settle, so moving along a row doesn't flash every poster. A card whose picture
        // is still on its way keeps the current art up until it arrives; only items without art (apps) clear it.
        if (p != null) ui.postDelayed(shelfTick, 280) else showArt(null)
    }

    /** Cross-fades the billboard art to [art] (no dip to the wallpaper between cards); [swap] sharpens the same picture in place. */
    private fun showArt(art: Bitmap?, swap: Boolean = false) {
        val wallpaper = findViewById<View>(R.id.wallpaper)
        if (art == null) {
            wallpaper.visibility = View.VISIBLE
            return run { hero.animate().alpha(0f).setDuration(420).setInterpolator(EASE_OUT).start() }
        }
        val now = (hero.drawable as? android.graphics.drawable.TransitionDrawable)?.getDrawable(1) ?: hero.drawable
        // Same picture already set (e.g. back on the first card after an app tile faded the art out): keep it, but
        // still stop that fade-out and bring it back up below. Returning early here left the wallpaper showing.
        if ((now as? android.graphics.drawable.BitmapDrawable)?.bitmap !== art) {
            val next = android.graphics.drawable.BitmapDrawable(resources, art)
            if (swap || now == null || hero.alpha == 0f) {
                hero.setImageDrawable(next)
            } else {
                hero.setImageDrawable(android.graphics.drawable.TransitionDrawable(arrayOf(now, next)).apply {
                    isCrossFadeEnabled = true
                    startTransition(360)
                })
            }
        }
        hero.animate().cancel()
        // Once the art is fully up it hides the wallpaper completely: stop drawing the wallpaper under it.
        if (hero.alpha < 1f) hero.animate().alpha(1f).setDuration(480).setInterpolator(EASE_OUT)
            .withEndAction { if (hero.alpha == 1f) wallpaper.visibility = View.INVISIBLE }.start()
        else wallpaper.visibility = View.INVISIBLE
    }

    private fun progressBarDrawable(): Drawable {
        fun bar(color: Int) = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(color) }
        return LayerDrawable(arrayOf(
            bar(0x38FFFFFF), // white 22 %: the bar sits on the art, in either theme
            ClipDrawable(bar(ACCENT), Gravity.START, ClipDrawable.HORIZONTAL),
        )).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
    }

    private fun open(p: Prog) {
        prefs.edit().putLong("t_${p.pkg}", System.currentTimeMillis()).putInt("n_${p.pkg}", prefs.getInt("n_${p.pkg}", 0) + 1).apply()
        try {
            startActivity(Intent.parseUri(p.intent, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), zoomFromFocus())
        } catch (e: Exception) {
            p.pkg?.let { packageManager.getLeanbackLaunchIntentForPackage(it) }?.let { startActivity(it, zoomFromFocus()) }
        }
    }

    private fun loadPoster(v: ImageView, uri: String?) {
        if (uri == null) return
        posters.get(uri)?.let { v.setTag(R.id.hero, null); v.setImageBitmap(it); return }
        v.tag = uri
        loading[uri]?.let { it += v; return } // already on its way (the same show in two rows)
        loading[uri] = mutableListOf(v)
        posterIo.execute {
            val bmp = try {
                decode(uri)
            } catch (e: Exception) {
                android.util.Log.w("Koolest", "poster failed: $uri ($e)") // retried by retryPosters()
                null
            }
            ui.post {
                val waiting = loading.remove(uri).orEmpty()
                if (bmp == null) return@post waiting.filter { it.tag == uri && it.drawable == null }.forEach(::posterFallback)
                posters.put(uri, bmp)
                for (w in waiting) if (w.tag == uri) {
                    w.setTag(R.id.hero, null)
                    w.setImageBitmap(bmp)
                    if (w.parent === shelfFor) ui.postDelayed(shelfTick, 0) // the focused card's poster just arrived
                }
            }
        }
    }

    /** Cards whose poster never arrived (offline, server timeout) try again; ones not yet near focus wait for it. */
    private fun retryPosters() {
        for (strip in cardRows.values) kids(strip).take(EAGER_POSTERS).forEach { card ->
            val img = posterOf(card) ?: return@forEach
            if (img.drawable == null || img.getTag(R.id.hero) == true) loadPoster(img, img.tag as? String)
        }
    }

    /** A card whose picture can't be had (its server is down): the app's own banner instead of a blank card. */
    private fun posterFallback(img: ImageView) {
        val pkg = ((img.parent as? View)?.getTag(R.id.watchRow) as? Prog)?.pkg ?: return
        val banner = apps.firstOrNull { it.pkg == pkg }?.banner ?: return
        img.setImageDrawable(banner.constantState?.newDrawable(resources) ?: return)
        img.setTag(R.id.hero, true) // not art: the billboard skips it and retryPosters tries again
    }

    /** Downsample to card size in RGB_565 so a dozen posters stay around 1-2 MB. */
    private fun decode(uri: String, minWidth: Int = dp(220), config: Bitmap.Config = Bitmap.Config.RGB_565): Bitmap? {
        val bytes = openStream(uri).use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= minWidth) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = config
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun openStream(uri: String): InputStream =
        if (uri.startsWith("http")) {
            (URL(uri).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
            }.inputStream
        } else {
            contentResolver.openInputStream(Uri.parse(uri)) ?: throw IOException("no stream for $uri")
        }

    // ---------- shared view bits ----------

    /**
     * 30nama focus: the focused card fills with the focus colour (posters keep their art and get a focus
     * ring instead), scales up and lifts. No resting shadow; depth appears only on focus.
     * Only transform/alpha/colour change, so it stays on the GPU and costs no re-layout.
     */
    private fun style(v: View, radius: Int, card: Boolean, lift: Float = 1.08f, bare: Boolean = false, onFocus: ((Boolean) -> Unit)? = null) {
        // shortcut: the ring is drawn inside the edge (the card clips to its outline), not 6 px outside as in 30nama.
        val ring = GradientDrawable().apply {
            cornerRadius = radius.toFloat()
            setStroke(dp(2), focusFill)
            alpha = 0
        }
        val bg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, if (card) cardRest else intArrayOf(0, 0)).apply {
            cornerRadius = radius.toFloat()
        }
        // Bare (app marks): no card, ring or clip; the caller draws its own focus, which may spill past the bounds.
        if (!bare) {
            v.background = bg
            v.foreground = ring
            v.clipToOutline = true
        }
        v.outlineSpotShadowColor = Color.BLACK
        v.outlineAmbientShadowColor = Color.BLACK
        v.elevation = 0f
        v.isFocusable = true
        v.isClickable = true
        v.setOnLongClickListener { if (!holdFired) fireHold(v); true }
        v.setOnFocusChangeListener { _, focused ->
            ring.alpha = if (focused) 255 else 0
            if (card) bg.colors = if (focused) cardLit else cardRest
            v.animate()
                .scaleX(if (focused) lift else 1f).scaleY(if (focused) lift else 1f)
                .translationZ(if (focused) dp(18).toFloat() else 0f)
                .setDuration(220).setInterpolator(EASE_OUT).start()
            onFocus?.invoke(focused)
        }
    }

    private fun label(text: String, size: Float) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(fg)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val TV_LISTINGS = "android.permission.READ_TV_LISTINGS"
        const val WRITE_SECURE = "android.permission.WRITE_SECURE_SETTINGS"
        const val ROLE_REQUEST = 2
        const val CITY_ROW = 14 // positions in the settings list (see renderSettings), not where they show
        /** Settings groups: name, a hint of what's in it, and its settings (positions in renderSettings' list) in show order. */
        val SETTINGS_GROUPS = listOf(
            Triple("Look", "Theme, wallpaper", listOf(1, 2, 3, 4, 5)),
            Triple("Home screen", "Rows, apps", listOf(ROWS_ROW, 0, 22, 6, 7, 8, 9)),
            Triple("Continue watching", "Shows", listOf(11, 10)),
            Triple("Weather", "Units, place", listOf(12, 13, 14)),
            Triple("Screensaver", "Gallery, clock, weather", listOf(15, DATES_ROW, MUSIC_ROW, 16, 17)),
            Triple("This TV", "Home button, updates",
                if (SelfAdb.AVAILABLE) listOf(18, ADB_SETUP_ROW, 19, ONLY_LAUNCHER_ROW, 21, 20) else listOf(18, 19, 21, 20)),
        )
        const val ADB_SETUP_ROW = 24
        const val ONLY_LAUNCHER_ROW = 25
        const val DATES_ROW = 26
        const val MUSIC_ROW = 27
        const val SCREENSAVER_ROW = 15
        const val HOME_BUTTON_ROW = 18
        const val DEFAULT_HOME_ROW = 19
        const val UPDATE_ROW = 20
        const val LOCK_ROW = 21
        const val MENU_APPS_ROW = 22
        const val ROWS_ROW = 23
        /** Icons beside setting names (groups and rows); app names get the app's own icon instead. */
        val SETTING_ICONS = mapOf(
            "Look" to R.drawable.ic_s_palette, "Home screen" to R.drawable.rail_home, "Continue watching" to R.drawable.ic_s_history,
            "Weather" to R.drawable.ic_s_sunny, "Screensaver" to R.drawable.ic_s_image, "This TV" to R.drawable.ic_s_tv,
            "Theme" to R.drawable.ic_s_theme, "Light wallpaper" to R.drawable.ic_s_image, "Dark wallpaper" to R.drawable.ic_s_image,
            "Soften photos" to R.drawable.ic_s_gradient, "Photo changes" to R.drawable.ic_s_timer, "Rows" to R.drawable.ic_s_rows,
            "Rearrange apps" to R.drawable.ic_s_move, "Menu apps" to R.drawable.ic_s_menu, "App order" to R.drawable.ic_s_sort,
            "Apps shown" to R.drawable.ic_s_eye, "App names" to R.drawable.ic_s_text, "Logo colours" to R.drawable.ic_s_palette,
            "Hidden shows" to R.drawable.ic_s_eye, "Apps" to R.drawable.rail_apps, "Consoles & inputs" to R.drawable.ic_s_gamepad,
            "Units" to R.drawable.ic_s_thermo, "Location" to R.drawable.ic_s_place, "Automatic" to R.drawable.ic_s_place,
            "Preview" to R.drawable.ic_s_play, "Dates" to R.drawable.ic_s_text, "Music" to R.drawable.ic_s_music, "When it starts" to R.drawable.ic_s_timer, "Home button" to R.drawable.rail_home,
            "Default home" to R.drawable.ic_s_tv, "PIN lock" to R.drawable.ic_s_lock, "Updates" to R.drawable.ic_s_update,
            "Keep Home working" to R.drawable.rail_home, "Only launcher" to R.drawable.ic_s_tv,
            "Open Developer options" to R.drawable.ic_s_tv, "Try again" to R.drawable.ic_s_update,
        )
        // The menu's apps to start with (any not installed are skipped), followed by the consoles (see railApps).
        const val DEFAULT_RAIL = "com.google.android.youtube.tv,com.netflix.ninja,com.amazon.amazonvideo.livingroom,com.spotify.tv.android,com.nama30.tv"
        val ALL_SORTS = linkedMapOf(
            "home" to "Home order", "name" to "A–Z", "used" to "Most used", "recent" to "Recently used", "installed" to "Recently installed",
        )
        const val TOGGLE_ON = "\u0000on" // row values drawn as a switch instead of text
        const val TOGGLE_OFF = "\u0000off"
        const val PIN_TRIES = 5 // wrong PINs before a 30 s wait
        const val UNLOCKED_MS = 5 * 60_000L
        const val UNLOCK_TO_EDIT = "Unlock to edit…"
        const val MEDIA_REQUEST = 3
        const val PER_APP = 5 // Continue Watching items per app
        // Channels that list what you're partway through, rather than recommendations (lower case).
        val RESUME_CHANNELS = setOf("continue watching", "keep watching", "resume watching", "continue listening")
        // Focus grows a 200 dp item by 6 dp a side, and its ring sits 5 dp past that: 11 dp, so a 20 dp gap keeps
        // the focused item clear of its neighbours.
        const val FOCUS_LIFT = 1.06f
        const val ITEM_GAP = 20
        const val FOCUS_ROOM = 14 // dp round a row's items for the focused one's lift and ring (11 dp)
        const val EAGER_POSTERS = 6 // card pictures loaded per row up front (a screenful); the rest as focus nears
        // Apps with their own home rows (what they publish to the TV home screen).
        val YOUTUBE = setOf("com.google.android.youtube.tv", "io.gh.reisxd.tizentube.cobalt")
        val NAMA = setOf("com.nama30.tv", "com.tv30namarn")
        val SPOTIFY = setOf("com.spotify.tv.android")
        val READ_PHOTOS = if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_IMAGES" else "android.permission.READ_EXTERNAL_STORAGE"
        /** Minutes between folder photos. */
        val PHOTO_EVERY = linkedMapOf(
            1 to "Every minute", 5 to "Every 5 min", 10 to "Every 10 min", 15 to "Every 15 min", 30 to "Every 30 min",
            60 to "Every hour", 1440 to "Every day",
        )
        const val ADD_FROM_PHONE = "Add from phone…"
        const val CHOOSE_FROM_TV = "Choose a folder on this TV…"
        const val CHOSEN = "✓"
        const val REMOVE_PHOTO = "Remove a photo…"
        const val REPO = "KoolestTVLauncher/Koolest-TV-Launcher" // this repo's releases, which the in-app updater installs
        const val ACCENT = 0xFFF0454A.toInt() // 30nama red: progress, switches on, the current PIN box
        const val UNSPLASH = "online:Unsplash photos" // a new photo every "Photo changes", see onlinePhoto
        const val PHOTO_TRIES = 6
        const val RAIL_GAP = 12 // dp between the menu's groups: Search/Home, the apps, All apps
        const val UPDATE_STATUS = "dev.koolest.UPDATE_STATUS"
        const val WRITE_EPG ="com.android.providers.tv.permission.WRITE_EPG_DATA"
        const val TV_SETTINGS = "com.android.tv.settings"
        const val HDMI = "hdmi:" // App.pkg prefix for HDMI device tiles; the rest is the TV input id
        val BUNDLED_WALLPAPERS = linkedMapOf(
            "Rockies (day)" to R.drawable.wallpaper,
            "Rockies (dusk)" to R.drawable.wallpaper_dark,
        )
        val PIN_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
        )
        val EDIT_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        )
        val GREYSCALE = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        // 30nama motion curves: EASE_OUT for focus and small moves, EMPH for big travel (screens, panels, the shelf).
        val EASE_OUT = PathInterpolator(.2f, .8f, .2f, 1f)
        val EMPH = PathInterpolator(.16f, 1f, .3f, 1f)
        @Volatile var APPS: List<App>? = null
        /** Mono marks made by glyph(), by package; null = icon has no usable symbol. Main thread only. */
        val GLYPHS = HashMap<String, Bitmap?>()
        /** accent() colours by package; null = no clear colour. Main thread only. */
        val ACCENTS = HashMap<String, Int?>()

        /**
         * Colour mode tints for the bundled marks: Simple Icons' brand hex, except where that's too dark to
         * read on dark cards (Prime Video) or grey (Google Play), where the brand's brighter blue/green is used.
         * HDMI devices carry their own (see deviceLogo).
         */
        val BRAND_COLOURS = mapOf(
            "com.netflix.ninja" to 0xFFE50914.toInt(),
            "com.stremio.one" to 0xFF8D3DAF.toInt(),
            "io.gh.reisxd.tizentube.cobalt" to 0xFFFF0000.toInt(),
            "com.google.android.youtube.tv" to 0xFFFF0000.toInt(),
            "com.google.android.youtube.tvmusic" to 0xFFFF0000.toInt(),
            "com.amazon.amazonvideo.livingroom" to 0xFF00A8E1.toInt(),
            "com.spotify.tv.android" to 0xFF1DB954.toInt(),
            "org.videolan.vlc" to 0xFFFF8800.toInt(),
            "com.android.vending" to 0xFF01875F.toInt(),
        )

        val DEFAULT_ORDER = listOf(
            "com.netflix.ninja",
            "com.stremio.one",
            "io.gh.reisxd.tizentube.cobalt",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.spotify.tv.android",
        )

        /** Monochrome brand marks (Simple Icons, CC0). `true` = wordmark, shown without a label. */
        val LOGOS = mapOf(
            "com.netflix.ninja" to (R.drawable.logo_netflix to false),
            "com.stremio.one" to (R.drawable.logo_stremio to false),
            "io.gh.reisxd.tizentube.cobalt" to (R.drawable.logo_youtube to false),
            "com.google.android.youtube.tv" to (R.drawable.logo_youtube to false),
            "com.google.android.youtube.tvmusic" to (R.drawable.logo_youtubemusic to false),
            "com.amazon.amazonvideo.livingroom" to (R.drawable.logo_primevideo to true),
            "com.spotify.tv.android" to (R.drawable.logo_spotify to false),
            "org.videolan.vlc" to (R.drawable.logo_vlcmediaplayer to false),
            "com.android.vending" to (R.drawable.logo_googleplay to false),
        )
    }
}
