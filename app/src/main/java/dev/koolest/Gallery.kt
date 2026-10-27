package dev.koolest

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.service.dreams.DreamService
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Public-domain museum scans, not bundled: downloaded on demand from the repo's `gallery-1` release (the
 * files live in screensaver/) into [paintingsDir], so the APK stays under 1 MB for people who never use it. only paintings whose canvas is (within ~2-4%) 16:9, cropped
 * to exactly 1920×1080 so they fill the screen with nothing cut off and no bars. Bright ones for light mode,
 * dusk and storm ones for dark (wide night paintings are rare).
 */
private class Painting(val file: String, val title: String, val artist: String)

/** One picture in the show: a painting with its caption, or one of your photos (no caption). */
private class Picture(val title: String, val artist: String, val load: () -> Bitmap?)

private val LIGHT = listOf(
    Painting("andes", "The Heart of the Andes", "Frederic Edwin Church, 1859"),
    Painting("icebergs", "The Icebergs", "Frederic Edwin Church, 1861"),
    Painting("rye", "Rye", "Ivan Shishkin, 1878"),
    Painting("chimborazo", "Chimborazo", "Frederic Edwin Church, 1864"),
    Painting("hudson", "Autumn on the Hudson River", "Jasper Francis Cropsey, 1860"),
    Painting("yellowstone", "The Grand Canyon of the Yellowstone", "Thomas Moran, 1893–1901"),
    Painting("wilderness", "A Home in the Wilderness", "Sanford Robinson Gifford, 1866"),
    Painting("nybay", "Sunset Over New York Bay", "Sanford Robinson Gifford, 1873"),
    Painting("mountdesert", "The Artist Sketching at Mount Desert, Maine", "Sanford Robinson Gifford, 1864–65"),
    Painting("steppe", "Steppe", "Arkhip Kuindzhi, 1890–95"),
    Painting("lulworth", "By the Sea, Lulworth", "Jasper Francis Cropsey, 1857"),
    Painting("wyoming", "The Valley of Wyoming", "Jasper Francis Cropsey, 1865"),
    Painting("kensettsunset", "Sunset", "John Frederick Kensett, 1872"),
    Painting("labrador", "Whaler and Fishing Vessels near the Coast of Labrador", "William Bradford, c. 1880"),
)
private val DARK = listOf(
    Painting("cotopaxi", "Cotopaxi", "Frederic Edwin Church, 1862"),
    Painting("thunderstorm", "Thunder Storm on Narragansett Bay", "Martin Johnson Heade, 1868"),
    Painting("rosalie", "A Storm in the Rocky Mountains, Mt. Rosalie", "Albert Bierstadt, 1866"),
    Painting("pointjudith", "Point Judith, Rhode Island", "Martin Johnson Heade, 1867"),
    Painting("shepherds", "The Angel Appearing to the Shepherds", "Thomas Cole, 1833–34"),
    Painting("saltmeadow", "Salt Meadow in October", "John Frederick Kensett, 1872"),
    Painting("capestjohns", "Near Cape St. Johns, Coast of Labrador", "William Bradford, 1874"),
)

internal const val PAINTINGS_URL = "https://github.com/KoolestTVLauncher/Koolest-TV-Launcher/releases/download/gallery-1/"
internal const val PAINTINGS_BYTES = 9_100_000L // shown with Formatter, in the screen's language
internal val PAINTING_FILES = (LIGHT + DARK).map { "${it.file}.jpg" }
internal fun paintingsDir(c: Context) = File(c.filesDir, "paintings")
internal fun paintingsReady(c: Context) = PAINTING_FILES.all { File(paintingsDir(c), it).exists() }

/**
 * The Gallery screensaver. Picked in launcher settings, which also restore the previous one when it's off.
 * Shows the paintings, or a folder of your photos when one is chosen there ("ss_folder").
 * ◀ ▶ step through the pictures, ▲ ▼ through the weather; any other key leaves to the launcher's home screen.
 */
class GalleryDream : DreamService() {
    private var show: GalleryShow? = null

    override fun attachBaseContext(base: Context) = super.attachBaseContext(localized(base))

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true // we handle keys ourselves (see dispatchKeyEvent)
        isFullscreen = true
        Locale.setDefault(resources.configuration.locales[0]) // Android puts the TV's back when it binds the dream
        show = GalleryShow(this).also { setContentView(it.view) }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (e.action == KeyEvent.ACTION_DOWN) show?.step(if (e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1)
            return true
        }
        if (e.keyCode == KeyEvent.KEYCODE_DPAD_UP || e.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (e.action == KeyEvent.ACTION_DOWN) show?.info?.flip(if (e.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) 1 else -1)
            return true
        }
        if (e.action == KeyEvent.ACTION_UP) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
        return true
    }

    override fun onDetachedFromWindow() {
        show?.release()
        super.onDetachedFromWindow()
    }
}

/**
 * Paints edge to edge on a SurfaceView at the UI's 1080p. Not 4K: this TV's display hardware shows a 4K app
 * surface as black (screenshots still show it, since they composite on the GPU).
 * Each painting fills the screen exactly, holds, then cross-fades to the next. The title shows for a few seconds per painting.
 * About 6 MB per decoded painting; one in memory, two during a cross-fade, plus the surface's buffers.
 * Outgoing paintings are recycled as soon as they're off screen, and everything when the dream ends.
 */
private class GalleryShow(private val context: Context) : SurfaceHolder.Callback {
    private val paintings = pictures()
    private val io = Executors.newSingleThreadExecutor()
    val view = SurfaceView(context).also { it.holder.addCallback(this) }

    /** A folder's photos in shuffled order if one is chosen and still has photos, else the theme's paintings. */
    private fun pictures(): List<Picture> {
        val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)
        val photos = try {
            prefs.getString("ss_folder", null)?.let { folderPhotos(context.contentResolver, it) }.orEmpty()
        } catch (e: Exception) { // photo access taken away
            emptyList()
        }
        if (photos.isNotEmpty()) return photos.shuffled().map { id ->
            val uri = ContentUris.withAppendedId(PHOTOS, id)
            Picture("", "") { fitWallpaper { context.contentResolver.openInputStream(uri) ?: throw IOException("gone: $uri") } }
        }
        // Only those already downloaded: an update that adds paintings fetches them the next time Koolest opens.
        return (if (prefersDark(prefs)) DARK else LIGHT).filter { File(paintingsDir(context), "${it.file}.jpg").exists() }.map { p ->
            Picture(p.title, p.artist) { File(paintingsDir(context), "${p.file}.jpg").inputStream().use { BitmapFactory.decodeStream(it) } }
        }
    }

    /** [blur] is the picture frosted for the glass panels (see [frost]), about 1/8 its size. */
    private class Slide(val bmp: Bitmap, val blur: Bitmap, val painting: Picture, val start: Long, val fadeMs: Long) {
        fun recycle() {
            bmp.recycle()
            blur.recycle()
        }
    }

    // Shared between the loader and the render thread; swaps and draws hold [lock], so a bitmap is
    // never recycled mid-draw.
    private val lock = Any()
    @Volatile private var current: Slide? = null
    @Volatile private var previous: Slide? = null
    @Volatile private var running = false
    private var index = if (paintings.isEmpty()) 0 else paintings.indices.random()
    @Volatile private var advanceAt = 0L
    private var render: Thread? = null

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; setShadowLayer(12f, 0f, 2f, 0x99000000.toInt()) }
    private val artist = Paint(title)
    val info = GlassInfo(context)
    @Volatile private var weatherAt = 0L

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        if (paintings.isNotEmpty()) load(index)
        render = Thread(::loop, "gallery").apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        render?.join(500)
    }

    fun step(by: Int) {
        if (paintings.isEmpty()) return
        index = (index + by + paintings.size) % paintings.size
        load(index)
    }

    fun release() {
        io.shutdownNow()
        synchronized(lock) {
            running = false
            current?.recycle()
            previous?.recycle()
            current = null
            previous = null
        }
    }

    private fun load(i: Int) {
        val p = paintings[i]
        io.execute {
            val bmp = try {
                p.load()
            } catch (e: Exception) {
                null
            } ?: return@execute
            val blur = frost(bmp)
            synchronized(lock) {
                if (!running) { // dream ended while decoding
                    bmp.recycle()
                    blur.recycle()
                    return@execute
                }
                previous?.recycle() // stepped again mid-fade: that one won't be drawn any more
                // The first painting fades up from black: keep that short or the screen just looks off.
                val fade = if (current == null) FIRST_FADE_MS else FADE_MS
                previous = current
                current = Slide(bmp, blur, p, System.currentTimeMillis(), fade)
                advanceAt = System.currentTimeMillis() + HOLD_MS
            }
        }
    }

    private fun loop() {
        while (running) {
            val now = System.currentTimeMillis()
            if (advanceAt in 1..now) {
                advanceAt = 0
                step(1)
            }
            if (now >= weatherAt) {
                weatherAt = now + 30 * 60_000L
                io.execute { info.refresh() }
            }
            val holder = view.holder
            val canvas = try {
                holder.lockHardwareCanvas()
            } catch (e: Exception) {
                null
            } ?: return
            try {
                draw(canvas, now)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
            // 30 fps while something fades (plenty for motion this slow); otherwise only the clock and the slow drift
            // move, and a few frames a second spares the GPU.
            val busy = info.animating(now) || current?.let { now - it.start < CAPTION_MS + FADE_MS } ?: true
            Thread.sleep(if (busy) FRAME_MS else IDLE_MS)
        }
    }

    private fun draw(c: Canvas, now: Long) = synchronized(lock) {
        c.drawColor(Color.BLACK)
        if (!running) return
        val cur = current ?: return info.draw(c, now, emptyList()) // no picture yet (or none to show): the clock still shows
        val fade = ((now - cur.start).toFloat() / cur.fadeMs).coerceIn(0f, 1f)
        previous?.let {
            if (fade < 1f) drawSlide(c, it, ((1f - fade) * 255).toInt()) else {
                it.recycle() // faded out: free it now rather than whenever GC gets to it
                previous = null
            }
        }
        drawSlide(c, cur, (fade * 255).toInt())
        info.draw(c, now, listOfNotNull(previous?.let { it.blur to ((1f - fade) * 255).toInt() }, cur.blur to (fade * 255).toInt()))

        // Title for the first seconds of each painting, fading in and out.
        val t = now - cur.start
        val a = when {
            t < FADE_MS -> t.toFloat() / FADE_MS
            t < CAPTION_MS -> 1f
            t < CAPTION_MS + FADE_MS -> 1f - (t - CAPTION_MS).toFloat() / FADE_MS
            else -> 0f
        }
        if (a > 0f && cur.painting.title.isNotEmpty()) {
            val u = c.height / 1080f // design in 1080p units, draw at panel resolution
            title.textSize = 24f * u
            artist.textSize = 20f * u
            title.alpha = (a * 235).toInt()
            artist.alpha = (a * 190).toInt()
            // Small, bottom right: the clock has the top right, weather and music the bottom left.
            title.textAlign = Paint.Align.RIGHT
            artist.textAlign = Paint.Align.RIGHT
            c.drawText(cur.painting.title, c.width - 80f * u, c.height - 108f * u, title)
            c.drawText(cur.painting.artist, c.width - 80f * u, c.height - 80f * u, artist)
        }
    }

    /** The paintings are already the screen's shape, so each one simply covers the whole surface. */
    private fun drawSlide(c: Canvas, s: Slide, alpha: Int) {
        paint.alpha = alpha
        c.drawBitmap(s.bmp, null, RectF(0f, 0f, c.width.toFloat(), c.height.toFloat()), paint)
    }

    private companion object {
        const val HOLD_MS = 2 * 60_000L
        const val FADE_MS = 2_500L
        const val FIRST_FADE_MS = 600L
        const val CAPTION_MS = 8_000L
        const val FRAME_MS = 33L
        const val IDLE_MS = 250L
    }
}
