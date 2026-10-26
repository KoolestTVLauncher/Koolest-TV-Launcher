package dev.koolest

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * What's behind the floating glass: the home screen's picture with its scrims, small and blurred, mapped to the
 * whole screen. Android can't blur the pixels behind a view, so the glass draws this instead, lined up with
 * where it sits on screen. Rebuilt when the picture changes (see MainActivity.updateBackdrop).
 */
internal class Backdrop {
    var frost: Bitmap? = null
        set(v) {
            field = v
            panes.forEach { it.invalidateSelf() }
        }
    /**
     * The open menu's view: the home screen as drawn, without the menu, taken when it opens (rows and text pass
     * under the open card, which [frost] doesn't have). Null while the menu is closed.
     */
    var snapshot: Bitmap? = null
        set(v) {
            field?.recycle()
            field = v
            panes.forEach { it.invalidateSelf() }
        }
    /** The dimming over the screen behind the menu (railDim's colour at its current opacity), for the menu's panes. */
    var dim = 0
        set(v) {
            field = v
            panes.forEach { it.invalidateSelf() }
        }
    val panes = mutableListOf<Drawable>()

    companion object {
        /**
         * The screen behind the glass: [picture] centre-cropped to the screen's shape with [over] (the scrims) on
         * top, at a quarter of the screen's size; drawn back up filtered, that softens it a little.
         */
        fun build(picture: Bitmap?, over: Drawable?, screenW: Int, screenH: Int, ground: Int): Bitmap {
            val w = 480
            val h = w * screenH / screenW
            val small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(small).apply {
                drawColor(ground)
                if (picture != null && !picture.isRecycled) {
                    val s = maxOf(small.width / picture.width.toFloat(), small.height / picture.height.toFloat())
                    val pw = picture.width * s
                    val ph = picture.height * s
                    drawBitmap(picture, null, RectF((small.width - pw) / 2, (small.height - ph) / 2, (small.width + pw) / 2, (small.height + ph) / 2),
                        Paint(Paint.FILTER_BITMAP_FLAG))
                }
                if (over != null) {
                    scale(small.width / screenW.toFloat(), small.height / screenH.toFloat())
                    over.setBounds(0, 0, screenW, screenH)
                    over.draw(this)
                }
            }
            // Liquid Glass is mostly clear: a light softening (1/4 size, drawn back up filtered), not a frost.
            return small
        }
    }
}

/**
 * Apple's Liquid Glass on a drawable. With a [backdrop] the pane shows what's behind it, blurred and slightly
 * magnified as through a lens; then a tint, a soft sheen from above, a light inner edge, and a rim drawn by light
 * (bright along the top, fading down the sides, a faint bounce at the bottom) instead of an outline.
 * For the navigation layer only (bars, menu, panels), never on content, never glass on glass.
 */
internal class LiquidGlass(private val radius: Float, private val fill: Int, private val dark: Boolean, private val dp: Float,
                           private val backdrop: Backdrop? = null, private val inMenu: Boolean = false) : Drawable() {
    init {
        backdrop?.panes?.add(this)
    }
    private val frostPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val clip = Path()
    private val at = IntArray(2)
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * dp }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f * dp }
    private val box = RectF()

    override fun onBoundsChange(b: Rect) {
        box.set(b)
        clip.reset()
        clip.addRoundRect(box, minOf(radius, b.height() / 2f, b.width() / 2f), minOf(radius, b.height() / 2f, b.width() / 2f), Path.Direction.CW)
        inner.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(),
            if (dark) 0x26FFFFFF else 0x99FFFFFF.toInt(), 0, Shader.TileMode.CLAMP)
        val h = b.height().toFloat()
        sheen.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.top + h * .5f,
            if (dark) 0x1AFFFFFF else 0x33FFFFFF, 0, Shader.TileMode.CLAMP)
        rim.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(),
            if (dark) intArrayOf(0x8CFFFFFF.toInt(), 0x1AFFFFFF, 0x40FFFFFF) else intArrayOf(-1, 0x14000000, 0x33000000),
            floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(c: Canvas) {
        val r = minOf(radius, box.height() / 2, box.width() / 2)
        val frost = (if (inMenu) backdrop?.snapshot else null) ?: backdrop?.frost
        val view = callback as? android.view.View
        if (frost != null && view != null) {
            // The screen behind, exactly where this pane is (its place on screen), so the glass lines up with
            // what's around it; the menu's panes also take the dimming that sits between them and the screen.
            view.getLocationOnScreen(at)
            val m = view.resources.displayMetrics
            c.save()
            c.clipPath(clip)
            frostPaint.alpha = alpha
            c.drawBitmap(frost, null, RectF(-at[0].toFloat(), -at[1].toFloat(), m.widthPixels - at[0].toFloat(), m.heightPixels - at[1].toFloat()), frostPaint)
            backdrop?.dim?.takeIf { inMenu && it != 0 }?.let { c.drawColor(it) }
            c.restore()
        }
        body.color = fill
        body.alpha = Math.round(android.graphics.Color.alpha(fill) * alpha / 255f)
        c.drawRoundRect(box, r, r, body)
        c.drawRoundRect(box, r, r, sheen)
        val half = rim.strokeWidth / 2 // the stroke straddles its line: keep it all inside
        c.drawRoundRect(box.left + half, box.top + half, box.right - half, box.bottom - half, r - half, r - half, rim)
        val in2 = rim.strokeWidth + inner.strokeWidth / 2 // a second, softer line just inside: the glass's thickness
        c.drawRoundRect(box.left + in2, box.top + in2, box.right - in2, box.bottom - in2, r - in2, r - in2, inner)
    }

    private var alpha = 255
    override fun setAlpha(a: Int) {
        alpha = a
        sheen.alpha = a
        rim.alpha = a
        inner.alpha = a
        invalidateSelf()
    }

    // The pane's shape, for the view's elevation shadow.
    override fun getOutline(o: Outline) = o.setRoundRect(bounds, minOf(radius, bounds.height() / 2f, bounds.width() / 2f))

    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * tvOS's glare on a focused poster: a soft diagonal band of light that sweeps across once as focus lands.
 * [progress] 0..1 moves it from off the start edge to off the end edge; 0 or 1 shows nothing.
 */
internal class Sheen(private val radius: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    var progress = 0f
        set(v) {
            field = v
            invalidateSelf()
        }

    override fun onBoundsChange(b: Rect) {
        clip.reset()
        clip.addRoundRect(RectF(b), radius, radius, Path.Direction.CW)
        val w = b.width().toFloat()
        // The band is a third of the card wide, at 30° from vertical.
        paint.shader = LinearGradient(0f, 0f, w / 3, w / 6, intArrayOf(0, 0x47FFFFFF, 0), null, Shader.TileMode.CLAMP)
    }

    override fun draw(c: Canvas) {
        if (progress <= 0f || progress >= 1f) return
        val b = bounds
        c.save()
        c.clipPath(clip)
        // From fully off the start edge to fully off the end edge. The band slants (it's half a card high per third
        // of a card wide), so at the bottom it trails by half the card's height: it must travel that much further,
        // or it was cut off there at the end.
        val from = b.left - b.width() / 3f
        val to = b.left + b.width() + b.height() / 2f
        c.translate(from + progress * (to - from), b.top.toFloat())
        c.drawRect(-b.width().toFloat(), -b.height().toFloat(), b.width() * 2f, b.height() * 2f, paint)
        c.restore()
    }

    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * A picture that centre-crops itself to whatever bounds it gets, with no size of its own. The billboard art
 * cross-fades two of these in one TransitionDrawable: with plain bitmaps that drawable took the larger picture's
 * size, the ImageView cropped for that, and a picture of another shape was stretched, then jumped back when the
 * fade ended.
 */
internal class CenterCrop(val bitmap: Bitmap) : Drawable() {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()

    override fun onBoundsChange(b: Rect) {
        val scale = maxOf(b.width() / bitmap.width.toFloat(), b.height() / bitmap.height.toFloat())
        val w = (b.width() / scale).toInt()
        val h = (b.height() / scale).toInt()
        src.set((bitmap.width - w) / 2, (bitmap.height - h) / 2, (bitmap.width + w) / 2, (bitmap.height + h) / 2)
    }

    override fun draw(c: Canvas) {
        if (!bitmap.isRecycled) c.drawBitmap(bitmap, src, bounds, paint)
    }

    override fun setAlpha(a: Int) {
        paint.alpha = a
        invalidateSelf()
    }

    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
