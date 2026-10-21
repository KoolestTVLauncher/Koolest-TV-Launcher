package dev.koolest

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.HorizontalScrollView

/**
 * A home row's track. It never scrolls by itself: MainActivity glides it so the focused item sits at the
 * content edge (30nama). It runs the full screen width, so items before it still show under the menu, and its
 * padding leaves room for focus lifts and rings. It clips at its edges, so Android can skip the cards that are
 * off screen instead of drawing every card of every row each frame.
 */
class SnapScrollView(context: Context, attrs: AttributeSet?) : HorizontalScrollView(context, attrs) {
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect) = 0
}

/**
 * The home rows' vertical scroller. Measures the rows at full height (a plain frame would squeeze them)
 * but never scrolls by itself: MainActivity glides it so the focused row sits at the top.
 */
class RowStack(context: Context, attrs: AttributeSet?) : android.widget.ScrollView(context, attrs) {
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect) = 0
}
