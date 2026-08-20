package org.fossify.musicplayer.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.animation.AnimationUtils as MaterialAnimationUtils
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.motion.MotionUtils
import org.fossify.commons.views.MySearchMenu
import org.fossify.musicplayer.extensions.coordinatorLayoutBehavior
import com.google.android.material.R as MR

/**
 * Auxio's CoordinatorAppBarLayout, ported onto the search menu this app's app bar already is, and
 * carrying the lift colour with it.
 *
 * It resolves two issues with the stock [AppBarLayout]:
 * 1. Lift state failing to update when list data changes.
 * 2. Expansion causing jumping in [RecyclerView] instances.
 *
 * Lifting is read the same way [AppBarLayout.shouldLift] reads it — whether the list at
 * [liftOnScrollTargetViewId] is off its top — so the bar stays lifted for as long as that list is
 * scrolled, however far the bar's own offset has since travelled back. It is painted here rather
 * than left to `liftOnScroll`, whose animation runs against a background this app repaints from the
 * theme on every resume and so never took effect. The duration and easing are read from the theme
 * with the same calls Material's own lift uses.
 *
 * Derived from Material Files: https://github.com/zhanghai/MaterialFiles
 */
class CoordinatorSearchMenu(context: Context, attrs: AttributeSet) : MySearchMenu(context, attrs) {
    private var scrollingChild: View? = null

    private var restingColor = Color.TRANSPARENT
    private var liftedColor = Color.TRANSPARENT
    private var liftProgress = 0f
    private var isLifted = false
    private var liftAnimator: ValueAnimator? = null

    private val liftDuration =
        MotionUtils.resolveThemeDuration(context, MR.attr.motionDurationMedium2, 0).toLong()
    private val liftInterpolator =
        MotionUtils.resolveThemeInterpolator(
            context,
            MR.attr.motionEasingStandardInterpolator,
            MaterialAnimationUtils.LINEAR_INTERPOLATOR,
        )

    private val tConsumed = IntArray(2)
    private val onPreDraw =
        ViewTreeObserver.OnPreDrawListener {
            val child = findScrollingChild()

            if (child != null) {
                val coordinator = parent as CoordinatorLayout
                coordinatorLayoutBehavior?.onNestedPreScroll(
                    coordinator,
                    this,
                    coordinator,
                    0,
                    0,
                    tConsumed,
                    0,
                )
            }

            updateLiftedState(child)
            true
        }

    init {
        viewTreeObserver.addOnPreDrawListener(onPreDraw)
    }

    /**
     * The colours the bar moves between as the list beneath it leaves and returns to its top.
     * Re-applied whenever the theme changes.
     */
    fun setSurfaceColors(resting: Int, lifted: Int) {
        restingColor = resting
        liftedColor = lifted
        paintSurface()
    }

    /**
     * Expand this [AppBarLayout] with respect to the current [RecyclerView] at
     * [liftOnScrollTargetViewId], preventing it from jumping around.
     */
    fun expandWithScrollingRecycler() {
        setExpanded(true)
        (findScrollingChild() as? RecyclerView)?.let {
            addOnOffsetChangedListener(ExpansionHackListener(it))
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnPreDrawListener(onPreDraw)
        liftAnimator?.cancel()
        scrollingChild = null
    }

    override fun setLiftOnScrollTargetViewId(liftOnScrollTargetViewId: Int) {
        super.setLiftOnScrollTargetViewId(liftOnScrollTargetViewId)
        // The scrolling child is swapped whenever the tab changes, so clear it and re-read.
        scrollingChild = null
        onPreDraw.onPreDraw()
    }

    private fun findScrollingChild(): View? {
        if (scrollingChild == null && liftOnScrollTargetViewId != ResourcesCompat.ID_NULL) {
            scrollingChild = (parent as? ViewGroup)?.findViewById(liftOnScrollTargetViewId)
        }

        return scrollingChild
    }

    /** Follow [scrollingChild] off and back onto its top, animating between the two colours. */
    private fun updateLiftedState(scrollingChild: View?) {
        val lifted = scrollingChild?.canScrollVertically(-1) == true
        if (lifted == isLifted) {
            return
        }

        isLifted = lifted
        liftAnimator?.cancel()
        liftAnimator =
            ValueAnimator.ofFloat(liftProgress, if (lifted) 1f else 0f).apply {
                duration = liftDuration
                interpolator = liftInterpolator
                addUpdateListener {
                    liftProgress = it.animatedValue as Float
                    paintSurface()
                }
                start()
            }
    }

    /** Painted onto the bar itself, so the search row, the shortcuts and the tabs all carry it. */
    private fun paintSurface() {
        setBackgroundColor(ColorUtils.blendARGB(restingColor, liftedColor, liftProgress))
    }

    /**
     * An [AppBarLayout.OnOffsetChangedListener] that will automatically move the given
     * [RecyclerView] as the [AppBarLayout] expands. Should be added right when the view is
     * expanding. Will be removed automatically.
     *
     * @param recycler [RecyclerView] to scroll with the [AppBarLayout].
     */
    private class ExpansionHackListener(private val recycler: RecyclerView) :
        AppBarLayout.OnOffsetChangedListener {
        private val offsetAnimationMaxEndTime =
            (AnimationUtils.currentAnimationTimeMillis() +
                APP_BAR_LAYOUT_MAX_OFFSET_ANIMATION_DURATION)
        private var currentVerticalOffset: Int? = null

        override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
            if (
                verticalOffset == 0 ||
                    AnimationUtils.currentAnimationTimeMillis() > offsetAnimationMaxEndTime
            ) {
                // AppBarLayout crashes with IndexOutOfBoundsException when a non-last listener
                // removes itself, so we have to do the removal asynchronously.
                appBarLayout.postOnAnimation { appBarLayout.removeOnOffsetChangedListener(this) }
            }

            // If possible, scroll by the offset delta between this update and the last update.
            val oldVerticalOffset = currentVerticalOffset
            currentVerticalOffset = verticalOffset
            if (oldVerticalOffset != null) {
                recycler.scrollBy(0, verticalOffset - oldVerticalOffset)
            }
        }
    }

    private companion object {
        /** @see AppBarLayout.BaseBehavior.MAX_OFFSET_ANIMATION_DURATION */
        const val APP_BAR_LAYOUT_MAX_OFFSET_ANIMATION_DURATION = 600
    }
}
