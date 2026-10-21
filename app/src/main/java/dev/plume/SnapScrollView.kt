package dev.plume

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.HorizontalScrollView

/**
 * A home row's track. It never scrolls by itself: MainActivity glides it so the focused item sits at the
 * content edge (30nama). Nothing clips, so items before it show under the menu and focus lifts and rings
 * reach past the track.
 */
class SnapScrollView(context: Context, attrs: AttributeSet?) : HorizontalScrollView(context, attrs) {
    init {
        clipChildren = false
    }

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect) = 0
}

/**
 * The home rows' vertical scroller. Measures the rows at full height (a plain frame would squeeze them)
 * but never scrolls by itself: MainActivity glides it so the focused row sits at the top.
 */
class RowStack(context: Context, attrs: AttributeSet?) : android.widget.ScrollView(context, attrs) {
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect) = 0
}
