package org.fossify.musicplayer.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.WindowInsets
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BackportBottomSheetBehavior
import org.fossify.musicplayer.extensions.coordinatorLayoutBehavior
import org.fossify.musicplayer.extensions.replaceSystemBarInsetsCompat
import org.fossify.musicplayer.extensions.systemBarInsetsCompat
import kotlin.math.abs

/**
 * A behavior that automatically re-layouts and re-insets content to align with the parent layout's
 * bottom sheet. Ideally, we would only want to re-inset content, but that has too many issues to
 * sensibly implement.
 */
class BottomSheetContentBehavior<V : View>(context: Context, attributeSet: AttributeSet?) :
    CoordinatorLayout.Behavior<V>(context, attributeSet) {
    private var dep: View? = null
    private var lastInsets: WindowInsets? = null
    private var lastConsumed = -1
    private var setup = false

    override fun layoutDependsOn(parent: CoordinatorLayout, child: V, dependency: View): Boolean {
        if (dependency.coordinatorLayoutBehavior is BackportBottomSheetBehavior) {
            dep = dependency
            return true
        }

        return false
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        super.onLayoutChild(parent, child, layoutDirection)
        layoutContent(child)

        if (!setup) {
            child.setOnApplyWindowInsetsListener { _, insets ->
                lastInsets = insets

                val dep = dep ?: return@setOnApplyWindowInsetsListener insets
                val behavior = dep.coordinatorLayoutBehavior as BackportBottomSheetBehavior
                val consumed = behavior.calculateConsumedByBar()
                if (consumed == Int.MIN_VALUE) {
                    return@setOnApplyWindowInsetsListener insets
                }

                val bars = insets.systemBarInsetsCompat
                insets.replaceSystemBarInsetsCompat(
                    bars.left,
                    bars.top,
                    bars.right,
                    consumed.coerceAtLeast(bars.bottom)
                )
            }

            setup = true
        }

        // Critical: seed / refresh inset dispatch after listener installation.
        child.requestApplyInsets()
        return true
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: V, dependency: View): Boolean {
        val behavior = dependency.coordinatorLayoutBehavior as BackportBottomSheetBehavior
        val consumed = behavior.calculateConsumedByBar()
        if (consumed == Int.MIN_VALUE) {
            return false
        }

        if (consumed != lastConsumed) {
            lastConsumed = consumed

            if (lastInsets != null) {
                child.dispatchApplyWindowInsets(lastInsets)
            } else {
                child.requestApplyInsets()
            }

            measureContent(parent, child)
            layoutContent(child)
            return true
        }

        return false
    }

    override fun onMeasureChild(
        parent: CoordinatorLayout,
        child: V,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int
    ): Boolean {
        measureContent(parent, child)
        return true
    }

    private fun measureContent(parent: View, child: View) {
        val contentWidthSpec = View.MeasureSpec.makeMeasureSpec(parent.measuredWidth, View.MeasureSpec.EXACTLY)
        val contentHeightSpec = View.MeasureSpec.makeMeasureSpec(parent.measuredHeight, View.MeasureSpec.EXACTLY)
        child.measure(contentWidthSpec, contentHeightSpec)
    }

    private fun layoutContent(child: View) {
        child.layout(0, 0, child.measuredWidth, child.measuredHeight)
    }

    private fun BackportBottomSheetBehavior<*>.calculateConsumedByBar(): Int {
        val offset = calculateSlideOffset()
        if (offset == Float.MIN_VALUE || peekHeight < 0) {
            return Int.MIN_VALUE
        }

        return if (offset >= 0) {
            peekHeight
        } else {
            (peekHeight * (1 - abs(offset))).toInt()
        }
    }
}
