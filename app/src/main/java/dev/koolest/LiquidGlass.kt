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
    val panes = mutableListOf<Drawable>()

    companion object {
        /**
         * The blurred screen: [picture] centre-cropped to the screen's shape, [over] (the scrims) on top, then
         * shrunk to 1/16 in halving steps (each averages 2×2, so nothing aliases) and drawn back up to 1/8 with
         * filtering, which leaves a soft blur of about 24 px at 1080p.
         */
        fun build(picture: Bitmap?, over: Drawable, screenW: Int, screenH: Int, ground: Int): Bitmap {
            val w = 240
            val h = w * screenH / screenW
            val small = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
            Canvas(small).apply {
                drawColor(ground)
                if (picture != null && !picture.isRecycled) {
                    val s = maxOf(small.width / picture.width.toFloat(), small.height / picture.height.toFloat())
                    val pw = picture.width * s
                    val ph = picture.height * s
                    drawBitmap(picture, null, RectF((small.width - pw) / 2, (small.height - ph) / 2, (small.width + pw) / 2, (small.height + ph) / 2),
                        Paint(Paint.FILTER_BITMAP_FLAG))
                }
                scale(small.width / screenW.toFloat(), small.height / screenH.toFloat())
                over.setBounds(0, 0, screenW, screenH)
                over.draw(this)
            }
            val tiny = Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(small, w, h, true), w / 2, h / 2, true)
            small.recycle()
            return Bitmap.createScaledBitmap(tiny, w, h, true).also { if (it !== tiny) tiny.recycle() }
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
                           private val backdrop: Backdrop? = null) : Drawable() {
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
        val frost = backdrop?.frost
        val view = callback as? android.view.View
        if (frost != null && view != null) {
            // The screen behind, where this pane is: the view's place on screen, less its own scale and drift,
            // magnified 6 % about the pane's centre, the lens of the glass.
            view.getLocationOnScreen(at)
            val m = view.resources.displayMetrics
            c.save()
            c.clipPath(clip)
            c.scale(1.06f, 1.06f, box.centerX(), box.centerY())
            frostPaint.alpha = alpha
            c.drawBitmap(frost, null, RectF(-at[0].toFloat(), -at[1].toFloat(), m.widthPixels - at[0].toFloat(), m.heightPixels - at[1].toFloat()), frostPaint)
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
        c.translate(b.left - b.width() / 3f + progress * (b.width() * 4 / 3f), b.top.toFloat())
        c.drawRect(-b.width().toFloat(), -b.height().toFloat(), b.width() * 2f, b.height() * 2f, paint)
        c.restore()
    }

    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
