package dev.koolest

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.icu.util.ULocale
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateFormat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import java.util.Locale
import java.util.SimpleTimeZone
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun fetchJson(url: String) = JSONObject((URL(url).openConnection() as HttpURLConnection).apply {
    connectTimeout = 5000
    readTimeout = 5000
}.inputStream.use { it.readBytes().decodeToString() })

internal fun weatherIcon(code: Int, day: Boolean) = when (code) {
    0 -> if (day) R.drawable.ic_wx_clear_day else R.drawable.ic_wx_clear_night
    1, 2 -> if (day) R.drawable.ic_wx_partly_day else R.drawable.ic_wx_partly_night
    45, 48 -> R.drawable.ic_wx_fog
    in 51..67, in 80..82 -> R.drawable.ic_wx_rain
    in 71..77, 85, 86 -> R.drawable.ic_wx_snow
    in 95..99 -> R.drawable.ic_wx_storm
    else -> R.drawable.ic_wx_cloudy
}

/** WMO weather codes, as Open-Meteo reports them. */
internal fun weatherWord(code: Int) = when (code) {
    0 -> "Clear"
    1, 2 -> "Partly cloudy"
    3 -> "Cloudy"
    45, 48 -> "Fog"
    in 51..57 -> "Drizzle"
    in 61..67, in 80..82 -> "Rain"
    in 71..77, 85, 86 -> "Snow"
    in 95..99 -> "Thunderstorm"
    else -> ""
}

/** Where the weather is for: the city picked in settings, or the IP's (re-checked every 6 hours). */
internal fun locate(prefs: SharedPreferences) {
    if (prefs.getBoolean("geo_manual", false) || System.currentTimeMillis() - prefs.getLong("geo_time", 0) < 6 * 3600_000L) return
    val geo = fetchJson("https://ipwho.is/?fields=success,city,latitude,longitude")
    if (geo.optBoolean("success")) prefs.edit()
        .putString("geo_city", geo.getString("city"))
        .putFloat("geo_lat", geo.getDouble("latitude").toFloat())
        .putFloat("geo_lon", geo.getDouble("longitude").toFloat())
        .putLong("geo_time", System.currentTimeMillis()).apply()
}

/**
 * A frosted copy of a picture for the glass panels: halved down to 1/32 (each halving averages 2×2, so nothing
 * aliases), then bilinear back up to 1/8, which smooths the blocks into a soft blur. Drawn stretched over the screen.
 */
internal fun frost(src: Bitmap): Bitmap {
    var b = src
    fun scale(f: Float) {
        val next = Bitmap.createScaledBitmap(b, maxOf(1, (b.width * f).toInt()), maxOf(1, (b.height * f).toInt()), true)
        if (b !== src) b.recycle()
        b = next
    }
    repeat(5) { scale(.5f) }
    repeat(2) { scale(2f) }
    return b
}

/**
 * Never bound for notifications: it exists because Android only hands the playing media sessions to an app
 * whose notification listener is switched on (Settings → Screensaver → Music does that).
 */
class MediaListener : NotificationListenerService()

/** Whether Koolest's [MediaListener] is allowed (before Android 8.1, read from the setting, as components). */
internal fun musicAccess(c: Context): Boolean {
    val me = ComponentName(c, MediaListener::class.java)
    if (android.os.Build.VERSION.SDK_INT >= 27) return c.getSystemService(android.app.NotificationManager::class.java).isNotificationListenerAccessGranted(me)
    return Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners").orEmpty()
        .split(':').any { ComponentName.unflattenFromString(it) == me }
}

/**
 * The screensaver's clock, dates, weather and music on iOS-style glass, drawn over the Gallery pictures in 1080p
 * units: the clock and dates top right, music and weather bottom left, the picture's credit bottom right (in Gallery).
 * The whole layer drifts a few pixels over minutes so nothing sits on the same pixels all night.
 * Weather follows the launcher's Weather switch and units; it's fetched here too, since the dream can run
 * for hours with the launcher never opening.
 */
internal class GlassInfo(private val context: Context) {
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)
    private val regular = context.resources.getFont(R.font.vazirmatn_regular)
    private val medium = context.resources.getFont(R.font.vazirmatn_medium)
    private val bold = context.resources.getFont(R.font.vazirmatn_bold)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blurPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val fa = ULocale("fa_IR@calendar=persian")
    @Volatile private var wx: JSONObject? = prefs.getString("ss_wx", null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    /** Fetches the forecast if the saved one is over 25 minutes old. Runs on a background thread. */
    fun refresh() {
        if (prefs.getString("weather", "off") == "off") return
        if (System.currentTimeMillis() - prefs.getLong("ss_wx_time", 0) < 25 * 60_000L) return
        try {
            locate(prefs)
            if (!prefs.contains("geo_lat")) return
            val at = "latitude=${prefs.getFloat("geo_lat", 0f)}&longitude=${prefs.getFloat("geo_lon", 0f)}"
            val f = fetchJson("https://api.open-meteo.com/v1/forecast?$at&timezone=auto&timeformat=unixtime&forecast_days=6&forecast_hours=8" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,is_day,weather_code,wind_speed_10m,wind_direction_10m" +
                "&hourly=temperature_2m,weather_code,is_day,precipitation_probability" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max")
            // Air quality is a separate service; the rest still shows without it.
            runCatching { f.put("aqi", fetchJson("https://air-quality-api.open-meteo.com/v1/air-quality?$at&current=us_aqi").getJSONObject("current").getInt("us_aqi")) }
            wx = f
            prefs.edit().putString("ss_wx", f.toString()).putLong("ss_wx_time", System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            android.util.Log.w("Koolest", "screensaver weather failed ($e)") // keep the last one; the next refresh retries
        }
    }

    // The stack under the current weather, like StandBy's widget stacks: it turns every [PAGE_MS], or with ▲ ▼.
    private var page = 0
    private var lastPage = 0
    private var dir = 1
    @Volatile private var pageAt = 0L
    private var mul = 1f // opacity for whatever is being drawn (a page on its way in or out)

    /** ▲ ▼ on the remote: the previous or next page now. */
    fun flip(by: Int) = synchronized(this) {
        lastPage = page
        page = (page + by + PAGES) % PAGES
        dir = by
        pageAt = System.currentTimeMillis()
    }

    /** True while something moves fast enough to need full frame rate. */
    fun animating(now: Long) = now - pageAt < PAGE_ANIM_MS

    /** [blurs] are the pictures on screen with their opacity (two during a cross-fade). */
    fun draw(c: Canvas, now: Long, blurs: List<Pair<Bitmap, Int>>) = synchronized(this) {
        if (pageAt == 0L) pageAt = now
        if (now - pageAt >= PAGE_MS) flip(1)
        val u = c.height / 1080f
        // Drift: up to ±12 × ±8 px, on periods of a few minutes.
        val dx = (12 * sin(now / 97_000.0)).toFloat() * u
        val dy = (8 * sin(now / 131_000.0)).toFloat() * u
        c.save()
        c.translate(dx, dy)
        c.scale(u, u)
        val g = Glass(c.width / u, dx / u, dy / u, blurs)
        mul = 1f
        drawClock(c, now, g)
        var bottom = 1080f - 80f
        wx.takeIf { prefs.getString("weather", "off") != "off" }?.let { f ->
            val card = RectF(80f, bottom - CARD_H, 80f + CARD_W, bottom)
            try {
                drawWeather(c, f, card, now, g)
                bottom = card.top - 20
            } catch (e: Exception) { // a forecast missing a field: show none rather than crash the dream
                android.util.Log.w("Koolest", "screensaver weather unreadable ($e)")
            }
        }
        if (prefs.getBoolean("ss_music", true)) playing(now)?.let { drawMusic(c, it, RectF(80f, bottom - MUSIC_H, 80f + CARD_W, bottom), g) }
        c.restore()
    }

    // ---------- clock and dates (top right) ----------

    /**
     * The lock screen clock: bold white digits on a soft shadow, spaced by their ink rather than their advance
     * widths, so 1s don't leave holes and every time looks evenly set. AM/PM, on a 12-hour clock, small after it.
     * Under it, a Calendar-icon tile per date.
     */
    private fun drawClock(c: Canvas, now: Long, g: Glass) {
        val h24 = DateFormat.is24HourFormat(context)
        val time = DateFormat.format(if (h24) "H:mm" else "h:mm", now).toString()
        val marker = if (h24) "" else DateFormat.format("a", now).toString().uppercase(Locale.getDefault())
        val right = g.w - 80
        val base = 252f
        text.typeface = bold
        text.textAlign = Paint.Align.LEFT
        // Lay out right to left from the edge: the marker, then the digits.
        val glyphs = mutableListOf<Triple<String, Float, Float>>() // glyph, size, ink left
        var x = right
        var ink = right // left edge of the last glyph's ink
        fun place(s: String, size: Float, gap: Float) {
            text.textSize = size
            val bounds = android.graphics.Rect()
            text.getTextBounds(s, 0, s.length, bounds)
            x -= bounds.width()
            ink = x
            glyphs += Triple(s, size, x - bounds.left)
            x -= gap
        }
        if (marker.isNotEmpty()) place(marker, 60f, 22f)
        val digitsRight = if (marker.isEmpty()) right else x
        for (i in time.indices.reversed()) {
            val colon = time[i] == ':' || time.getOrNull(i - 1) == ':'
            place(time[i].toString(), 220f, if (colon) 18f else 16f)
        }
        val digitsCentre = (ink + digitsRight) / 2
        val shape = Path()
        glyphs.forEach { (s, size, gx) ->
            text.textSize = size
            val p = Path()
            text.getTextPath(s, 0, s.length, gx, base, p)
            shape.addPath(p)
        }
        fill.color = Color.WHITE
        fill.setShadowLayer(28f, 0f, 4f, 0x66000000)
        fill.shader = LinearGradient(0f, base - 160, 0f, base, Color.WHITE, 0xFFE9EDF2.toInt(), Shader.TileMode.CLAMP) // lit from above
        c.drawPath(shape, fill)
        fill.shader = null
        fill.clearShadowLayer()

        val tiles = when (prefs.getString("ss_dates", "g")) {
            "fa" -> listOf(false)
            "both" -> listOf(true, false)
            else -> listOf(true)
        }
        // The tiles span the clock, from its first digit to the end of AM/PM (never narrower than they need).
        val single = tiles.size == 1
        val span = maxOf(right - ink, if (single) 2 * TILE else 2 * TILE + 16)
        val w = if (single) span else (span - 16) / 2
        var tx = right - span
        for (gregorian in tiles) {
            dateTile(c, RectF(tx, base + 44, tx + w, base + 44 + if (single) TILE - 24 else TILE), now, gregorian, g)
            tx += w + 16
        }
    }

    /**
     * Like the Calendar app's icon: weekday in red, the day large, month and year. Square tiles stack the three,
     * centred as a block by ink; a wide one (one calendar) puts the day beside the other two, Persian mirrored.
     */
    private fun dateTile(c: Canvas, r: RectF, now: Long, gregorian: Boolean, g: Glass) {
        g.panel(c, r, 32f)
        fun f(pattern: String) = if (gregorian) DateFormat.format(pattern, now).toString()
            else android.icu.text.SimpleDateFormat(pattern, fa).format(Date(now))
        val weekday = Triple(if (gregorian) f("EEEE").uppercase(Locale.getDefault()) else f("EEEE"), 26f to bold, RED)
        val day = Triple(f("d"), 84f to medium, Color.WHITE)
        // Android's own formatter reads a single "y" as a two-digit year.
        val month = Triple(f(if (gregorian) "MMMM yyyy" else "MMMM y"), 26f to medium, Color.argb(SECONDARY, 255, 255, 255))

        fun ink(l: Triple<String, Pair<Float, Typeface>, Int>) = android.graphics.Rect().also {
            text.typeface = l.second.second
            text.textSize = l.second.first
            text.getTextBounds(l.first, 0, l.first.length, it)
        }
        /** [lines] stacked [gap] apart, centred on [cy] as a block, at [x] by [align]. */
        fun stack(lines: List<Triple<String, Pair<Float, Typeface>, Int>>, x: Float, cy: Float, align: Paint.Align, gap: Float) {
            val inks = lines.map(::ink)
            var top = cy - (inks.sumOf { it.height() } + gap * (lines.size - 1)) / 2
            lines.forEachIndexed { i, (s, font, colour) ->
                line(c, s, x, top - inks[i].top, font.first, font.second, Color.alpha(colour), align = align, colour = colour or 0xFF000000.toInt())
                top += inks[i].height() + gap
            }
        }

        if (r.width() < r.height() * 1.6f) return stack(listOf(weekday, day, month), r.centerX(), r.centerY(), Paint.Align.CENTER, 14f)
        // Wide: the day, then the weekday over the month beside it, the pair centred in the tile.
        // Persian reads right to left, so its day goes on the right.
        val dayInk = ink(day)
        val textW = maxOf(ink(weekday).width(), ink(month).width())
        val total = dayInk.width() + 28f + textW
        val start = r.centerX() - total / 2
        if (gregorian) {
            stack(listOf(day), start - dayInk.left, r.centerY(), Paint.Align.LEFT, 0f)
            stack(listOf(weekday, month), start + dayInk.width() + 28, r.centerY(), Paint.Align.LEFT, 16f)
        } else {
            val end = start + total
            stack(listOf(day), end - dayInk.right, r.centerY(), Paint.Align.LEFT, 0f)
            stack(listOf(weekday, month), end - dayInk.width() - 28, r.centerY(), Paint.Align.RIGHT, 16f)
        }
    }

    // ---------- weather (bottom left) ----------

    private fun drawWeather(c: Canvas, f: JSONObject, card: RectF, now: Long, g: Glass) {
        val cur = f.getJSONObject("current")
        val daily = f.getJSONObject("daily")
        val unitF = prefs.getString("weather", "c") == "f"
        fun deg(v: Double) = "${(if (unitF) v * 9 / 5 + 32 else v).roundToInt()}°"
        g.panel(c, card, 36f)
        val l = card.left
        val t = card.top

        // Always there: the sky now, how warm and how warm it feels, today's range, and where.
        val code = cur.getInt("weather_code")
        icon(c, weatherIcon(code, cur.getInt("is_day") == 1), l + 24, t + 26, 68f)
        val temp = deg(cur.getDouble("temperature_2m"))
        line(c, temp, l + 102, t + 88, 70f, medium, 255)
        text.textSize = 70f
        val tx = l + 102 + text.measureText(temp) + 20
        line(c, weatherWord(code), tx, t + 48, 30f, medium, 255)
        line(c, "Feels ${deg(cur.getDouble("apparent_temperature"))}  ·  H ${deg(daily.getJSONArray("temperature_2m_max").getDouble(0))}" +
            "  L ${deg(daily.getJSONArray("temperature_2m_min").getDouble(0))}", tx, t + 84, 26f, medium, 230)
        prefs.getString("geo_city", null)?.let { line(c, it, tx, t + 116, 24f, regular, SECONDARY) }
        fill.color = 0x26FFFFFF
        c.drawRect(l + 24, t + 136, card.right - 24, t + 137.5f, fill)

        // The stack. Like StandBy: the page on its way out rises and fades as the next rises in from below
        // (from above when going back), on an ease-out curve.
        val area = RectF(l, t + 140, card.right - 36, card.bottom - 10)
        val p = ((now - pageAt).toFloat() / PAGE_ANIM_MS).coerceIn(0f, 1f)
        val e = 1 - (1 - p).pow(3)
        c.save()
        c.clipRect(area)
        if (p < 1f && lastPage != page) {
            mul = 1 - e
            c.save()
            c.translate(0f, -dir * e * SLIDE)
            drawPage(c, lastPage, f, area, ::deg)
            c.restore()
        }
        mul = e
        c.save()
        c.translate(0f, dir * (1 - e) * SLIDE)
        drawPage(c, page, f, area, ::deg)
        c.restore()
        c.restore()
        mul = 1f

        // Page indicator down the right edge: the current page is a pill that grows as it arrives.
        val x = card.right - 24
        var y = area.centerY() - (PAGES * 10f + 14f + (PAGES - 1) * 8f) / 2
        for (i in 0 until PAGES) {
            val grow = when (i) {
                page -> e
                lastPage -> if (lastPage != page) 1 - e else 1f
                else -> 0f
            }
            val h = 10f + 14f * grow
            fill.color = Color.argb((77 + 153 * grow).toInt(), 255, 255, 255)
            c.drawRoundRect(RectF(x - 5, y, x + 5, y + h), 5f, 5f, fill)
            y += h + 8
        }
    }

    private fun drawPage(c: Canvas, which: Int, f: JSONObject, a: RectF, deg: (Double) -> String) {
        val zone = SimpleTimeZone(f.getInt("utc_offset_seconds") * 1000, "here")
        val h24 = DateFormat.is24HourFormat(context)
        fun at(t: Long, pattern: String) = java.text.SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = zone }.format(Date(t * 1000))
        val daily = f.getJSONObject("daily")
        when (which) {
            // Next hours: the hour under way (the forecast can be half an hour old), then five more.
            0 -> {
                val hourly = f.getJSONObject("hourly")
                val hours = hourly.getJSONArray("time")
                val first = (0 until hours.length()).firstOrNull { hours.getLong(it) + 3600 > System.currentTimeMillis() / 1000 } ?: 0
                val col = (a.width() - 24) / 6
                for (i in 0 until minOf(6, hours.length() - first)) {
                    val k = first + i
                    val cx = a.left + 24 + col * i + col / 2
                    line(c, if (i == 0) "Now" else at(hours.getLong(k), if (h24) "H" else "ha"), cx, a.top + 40, 26f, medium, SECONDARY, align = Paint.Align.CENTER)
                    icon(c, weatherIcon(hourly.getJSONArray("weather_code").getInt(k), hourly.getJSONArray("is_day").getInt(k) == 1), cx - 26, a.top + 54, 52f)
                    line(c, deg(hourly.getJSONArray("temperature_2m").getDouble(k)), cx, a.top + 146, 32f, medium, 255, align = Paint.Align.CENTER)
                    val rain = hourly.getJSONArray("precipitation_probability").optInt(k, 0)
                    if (rain >= 10) line(c, "$rain%", cx, a.top + 176, 24f, medium, 255, align = Paint.Align.CENTER, colour = RAIN)
                }
            }
            // Five days: name, sky, low, the day's range on the week's scale, high.
            1 -> {
                val lows = daily.getJSONArray("temperature_2m_min")
                val highs = daily.getJSONArray("temperature_2m_max")
                val n = minOf(5, lows.length())
                val lo = (0 until n).minOf { lows.getDouble(it) }
                val span = ((0 until n).maxOf { highs.getDouble(it) } - lo).coerceAtLeast(1.0)
                for (i in 0 until n) {
                    val y = a.top + 36 + i * 35f
                    line(c, if (i == 0) "Today" else at(daily.getJSONArray("time").getLong(i), "EEE"), a.left + 24, y, 26f, medium, 255)
                    icon(c, weatherIcon(daily.getJSONArray("weather_code").getInt(i), true), a.left + 114, y - 27, 34f)
                    line(c, deg(lows.getDouble(i)), a.left + 222, y, 26f, medium, SECONDARY, align = Paint.Align.RIGHT)
                    val bar = RectF(a.left + 240, y - 12, a.right - 72, y - 6)
                    fill.color = 0x33FFFFFF
                    fill.alpha = (0x33 * mul).toInt()
                    c.drawRoundRect(bar, 3f, 3f, fill)
                    val x0 = bar.left + bar.width() * ((lows.getDouble(i) - lo) / span).toFloat()
                    val x1 = bar.left + bar.width() * ((highs.getDouble(i) - lo) / span).toFloat()
                    fill.color = WARM
                    fill.alpha = (255 * mul).toInt()
                    c.drawRoundRect(RectF(x0, bar.top, maxOf(x1, x0 + 6), bar.bottom), 3f, 3f, fill)
                    line(c, deg(highs.getDouble(i)), a.right - 8, y, 26f, medium, 255, align = Paint.Align.RIGHT)
                }
            }
            // Details, three by two, as Apple Weather's tiles: what it is on top, the number below.
            // The sun slot is whichever comes next, sunrise or sunset.
            else -> {
                val cur = f.getJSONObject("current")
                val unitF = prefs.getString("weather", "c") == "f"
                val kmh = cur.getDouble("wind_speed_10m")
                val uv = daily.getJSONArray("uv_index_max").getDouble(0)
                val nowS = System.currentTimeMillis() / 1000
                val sun = listOf("Sunrise" to daily.getJSONArray("sunrise"), "Sunset" to daily.getJSONArray("sunset"))
                    .flatMap { (name, times) -> (0 until times.length()).map { name to times.getLong(it) } }
                    .filter { it.second > nowS }.minByOrNull { it.second }
                val items = listOfNotNull(
                    "Humidity" to "${cur.getInt("relative_humidity_2m")}%",
                    "Wind ${compass(cur.getDouble("wind_direction_10m"))}" to (if (unitF) "${(kmh / 1.609).roundToInt()} mph" else "${kmh.roundToInt()} km/h"),
                    "Rain today" to "${daily.getJSONArray("precipitation_probability_max").optInt(0, 0)}%",
                    "UV · ${uvWord(uv)}" to "${uv.roundToInt()}",
                    if (f.has("aqi")) "Air · ${aqiWord(f.getInt("aqi"))}" to "${f.getInt("aqi")}" else null,
                    sun?.let { (name, time) -> name to at(time, if (h24) "H:mm" else "h:mm a") },
                )
                val col = (a.width() - 24) / 3
                items.forEachIndexed { i, (label, value) ->
                    val x = a.left + 24 + (i % 3) * col
                    val y = a.top + 40 + (i / 3) * 88f
                    line(c, label, x, y, 24f, regular, SECONDARY)
                    line(c, value, x, y + 38, 30f, medium, 255)
                }
            }
        }
    }

    // ---------- music (above the weather) ----------

    private class Track(val title: String, val artist: String, val art: Bitmap?, val progress: Float?)

    private var track: Track? = null
    private var trackAt = 0L

    /** What's playing, looked up once a second; null when nothing is, or Music hasn't been allowed. */
    private fun playing(now: Long): Track? {
        if (now - trackAt < 1000) return track
        trackAt = now
        track = try {
            val sessions = context.getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(context, MediaListener::class.java))
            sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }?.let { s ->
                val m = s.metadata ?: return@let null
                val title = m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return@let null
                val state = s.playbackState!!
                val length = m.getLong(MediaMetadata.METADATA_KEY_DURATION)
                // The position is reported now and then; it has moved on by the time passed, at the playback speed.
                val at = state.position + ((android.os.SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
                Track(title, m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: "",
                    m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
                    if (length > 0) (at.toFloat() / length).coerceIn(0f, 1f) else null)
            }
        } catch (e: SecurityException) { // Music not allowed (no notification access)
            null
        }
        return track
    }

    /** iOS's Now Playing: the cover, title and artist, and how far into the track. */
    private fun drawMusic(c: Canvas, t: Track, r: RectF, g: Glass) {
        g.panel(c, r, 32f)
        val art = RectF(r.left + 16, r.top + 16, r.left + 16 + (r.height() - 32), r.bottom - 16)
        if (t.art != null) {
            c.save()
            path.reset()
            path.addRoundRect(art, 18f, 18f, Path.Direction.CW)
            c.clipPath(path)
            c.drawBitmap(t.art, null, art, blurPaint.apply { alpha = 255 })
            c.restore()
        } else {
            fill.color = 0x33FFFFFF
            c.drawRoundRect(art, 18f, 18f, fill)
            icon(c, R.drawable.ic_s_music, art.centerX() - 24, art.centerY() - 24, 48f, tint = Color.WHITE)
        }
        val x = art.right + 20
        val w = r.right - 24 - x
        text.typeface = medium
        text.textSize = 28f
        line(c, TextUtils.ellipsize(t.title, text, w, TextUtils.TruncateAt.END).toString(), x, r.top + 48, 28f, medium, 255)
        text.textSize = 24f
        line(c, TextUtils.ellipsize(t.artist, text, w, TextUtils.TruncateAt.END).toString(), x, r.top + 82, 24f, regular, SECONDARY)
        t.progress?.let {
            val bar = RectF(x, r.bottom - 26, r.right - 24, r.bottom - 20)
            fill.color = 0x33FFFFFF
            c.drawRoundRect(bar, 3f, 3f, fill)
            fill.color = 0xE6FFFFFF.toInt()
            c.drawRoundRect(RectF(bar.left, bar.top, bar.left + maxOf(6f, bar.width() * it), bar.bottom), 3f, 3f, fill)
        }
    }

    // ---------- glass ----------

    /** The frosted pictures, for one frame. [w] is the screen's width in 1080p units, [dx] [dy] the drift. */
    private inner class Glass(val w: Float, val dx: Float, val dy: Float, val blurs: List<Pair<Bitmap, Int>>) {
        /** Frosted picture inside [shape], under a tint, then an optional sheen. */
        fun fill(c: Canvas, shape: Path, tint: Int, sheen: Shader? = null) {
            c.save()
            c.clipPath(shape)
            // The layer is shifted by the drift; draw the blur where the picture really is.
            val screen = RectF(-dx, -dy, -dx + w, -dy + 1080f)
            blurs.forEach { (bmp, alpha) -> blurPaint.alpha = alpha; c.drawBitmap(bmp, null, screen, blurPaint) }
            fill.color = tint
            c.drawPaint(fill)
            if (sheen != null) {
                fill.shader = sheen
                c.drawPaint(fill)
                fill.shader = null
            }
            c.restore()
        }

        /** Apple's "regular" glass rather than "clear": dark enough that white text reads over a bright sky. */
        fun panel(c: Canvas, r: RectF, radius: Float) {
            path.reset()
            path.addRoundRect(r, radius, radius, Path.Direction.CW)
            fill(c, path, 0x5E0A0A0A, LinearGradient(0f, r.top, 0f, r.top + 60, 0x14FFFFFF, 0, Shader.TileMode.CLAMP))
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = 1.5f
            fill.shader = LinearGradient(0f, r.top, 0f, r.bottom, 0x59FFFFFF, 0x1AFFFFFF, Shader.TileMode.CLAMP) // lit from above
            c.drawRoundRect(r, radius, radius, fill)
            fill.shader = null
            fill.style = Paint.Style.FILL
        }
    }

    private fun line(c: Canvas, s: String, x: Float, y: Float, size: Float, face: Typeface, alpha: Int,
                     align: Paint.Align = Paint.Align.LEFT, colour: Int = Color.WHITE) {
        text.typeface = face
        text.textSize = size
        text.textAlign = align
        text.color = colour
        text.alpha = (alpha * mul).toInt()
        c.drawText(s, x, y, text)
    }

    private fun icon(c: Canvas, res: Int, x: Float, y: Float, size: Float, tint: Int? = null) {
        val d = context.getDrawable(res)?.mutate() ?: return
        tint?.let { d.setTint(it) }
        d.alpha = (255 * mul).toInt()
        d.setBounds(x.toInt(), y.toInt(), (x + size).toInt(), (y + size).toInt())
        d.draw(c)
    }

    private fun compass(deg: Double) = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[((deg + 22.5) / 45).toInt() % 8]

    private fun uvWord(uv: Double) = when {
        uv < 3 -> "Low"
        uv < 6 -> "Moderate"
        uv < 8 -> "High"
        uv < 11 -> "Very high"
        else -> "Extreme"
    }

    /** US AQI bands. */
    private fun aqiWord(aqi: Int) = when {
        aqi <= 50 -> "Good"
        aqi <= 100 -> "Moderate"
        aqi <= 150 -> "Poor"
        aqi <= 200 -> "Unhealthy"
        else -> "Hazardous"
    }

    private companion object {
        const val CARD_W = 560f
        const val CARD_H = 340f
        const val MUSIC_H = 124f
        const val TILE = 172f
        const val PAGES = 3
        const val PAGE_MS = 12_000L
        const val PAGE_ANIM_MS = 650L
        const val SLIDE = 44f
        const val SECONDARY = 175 // white at this alpha: iOS's secondary label on glass
        const val RED = 0xFFFF453A.toInt() // iOS system red (dark), the Calendar icon's weekday
        const val RAIN = 0xFF8FD3FF.toInt()
        const val WARM = 0xFFFFD36B.toInt()
    }
}
