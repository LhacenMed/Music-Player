package org.fossify.musicplayer.extensions

import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.lang.reflect.Field

private val RV_TOUCH_SLOP_FIELD: Field? by lazyReflectedFieldOrNull(RecyclerView::class, "mTouchSlop")

/**
 * Get a [ViewPager2]'s internal [RecyclerView], which is its one and only child.
 *
 * Taken from the view hierarchy rather than by reflection, so that R8 cannot rename it out from
 * under us in a minified build.
 */
fun ViewPager2.recycler() = getChildAt(0) as RecyclerView

/**
 * Dampen a [ViewPager2] so that vertical scrolls can still easily occur.
 *
 * By default, ViewPager2's sensitivity is high enough to result in vertical scroll events being
 * registered as horizontal scroll events. Reflect into the internal [RecyclerView] and change the
 * touch slop so that touch actions will act more as a scroll than as a swipe.
 */
fun ViewPager2.dampen() {
    // Purely a refinement to the swipe feel, so a minified build that has stripped the field just
    // keeps the stock sensitivity rather than failing.
    val field = RV_TOUCH_SLOP_FIELD ?: return
    val recycler = recycler()
    val slop = field.get(recycler) as Int
    field.set(recycler, slop * 3)
}

/**
 * Move to [item] using smooth drag gestures instead of the [RecyclerView]'s scroll interpolator,
 * which is far faster/choppier and doesn't look as good.
 */
fun ViewPager2.smoothScrollByPageTo(item: Int, durationMs: Int = 300) {
    // Nothing to actually do if there's no data.
    val adapter = adapter ?: return
    if (adapter.itemCount <= 0) {
        return
    }

    val target = item.coerceIn(0, adapter.itemCount - 1)
    val delta = target - currentItem
    if (delta == 0) {
        return
    }

    val recycler = recycler()
    recycler.stopScroll()

    // Note: assumes a horizontal ViewPager, which means less logic to manage.
    val direction = if (layoutDirection == ViewPager2.LAYOUT_DIRECTION_RTL) -1 else 1
    recycler.smoothScrollBy(width * delta * direction, 0, null, durationMs)
}
