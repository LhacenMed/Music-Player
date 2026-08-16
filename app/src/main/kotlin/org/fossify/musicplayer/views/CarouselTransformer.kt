package org.fossify.musicplayer.views

import android.graphics.RectF
import android.view.View
import androidx.core.view.isInvisible
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.carousel.MaskableFrameLayout

/**
 * Masks adjacent [ViewPager2] pages so that swiping between covers reads as one contiguous
 * Material carousel transform rather than a plain page slide.
 */
class CarouselTransformer : ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        val maskable = page as MaskableFrameLayout
        val width = page.width.toFloat()
        val height = page.height.toFloat()

        if (width <= 0f || height <= 0f) {
            return
        }

        // Pin the page. Normally the page's layout is controlled by LinearLayoutManager, which we
        // don't want since it means we can't arrange them in the carousel. So we use translationX
        // to fix them to the same position and re-mask them however we want to create our effect.
        page.translationX = -position * width

        // Make sure that despite the pinning, offscreen pages are non-interactable. Otherwise funky
        // android touch logic kicks in and breaks the playback stepper.
        page.isInvisible = position <= -1f || position >= 1f

        page.alpha = 1f

        val p = when {
            // Previous page, just abs. p will be 1.0 -> 0.0, so 1.0 when fully outset and 0.0 when
            // inset. We must conform to this.
            position < 0f -> -position
            // Next page, take the inverse. Remember, this must also be normalized to
            // outset = 1.0, inset = 0.0, so take the opposite end (1f - pos).
            position > 0f -> 1f - position
            else -> 0f
        }

        // Breakpoints for the gap. We have to encode the "gap" as an equation such that the masking
        // effect looks like a contiguous transform of adjacent covers. If we just add a fixed gap
        // it'll appear instantly, and any p * gap or similar logic will morph over time. Even
        // polynomials will morph as well, hence this piecewise function.
        //
        // At the first and last 18% of progress we grow and shrink the gap respectively.
        val gapEnterBreakpoint = GAP_BREAKPOINT
        val gapExitBreakpoint = 1f - GAP_BREAKPOINT
        val maxGap = width * MAX_GAP_FRACTION

        val gap = when {
            // Gap is not fully grown yet, so scale it by how close we are to a fully grown gap.
            p < gapEnterBreakpoint -> maxGap * (p / gapEnterBreakpoint)
            // Gap should be shrinking, so downscale maxGap by how close we are to the end.
            p > gapExitBreakpoint -> maxGap * ((1f - p) / (1f - gapExitBreakpoint))
            // Neither growing nor shrinking.
            else -> maxGap
        }

        // How much we actually want to reveal of the incoming cover post-gap. Normalize p past
        // gapEnterBreakpoint, then normalize it to the range between the gap transform breakpoints:
        // [enter] - [reveal] - [exit]. If p is inside [enter] or [exit] we snap to 0f/1f
        // respectively, so we don't reveal more/less than we need to if the gap being phased out
        // already handles it.
        val reveal = ((p - gapEnterBreakpoint) / (gapExitBreakpoint - gapEnterBreakpoint)).coerceIn(0f, 1f)

        // Allocate space for the incoming cover, the gap, and the outgoing cover.
        val available = width - gap
        val incomingWidth = available * reveal
        val outgoingWidth = available - incomingWidth

        val rect = when {
            // Previous cover: fit to the outgoing width on the right. The gap is already factored
            // into the available calculation that outgoingWidth depends on.
            position < 0f -> RectF(0f, 0f, outgoingWidth, height)

            // Incoming cover: mask to the incoming width towards the left. Note that
            // left = width - incomingWidth = width - (width - gap) * reveal, which means we
            // automatically inset for the gap without including it as an explicit term. Since gap
            // itself is scaled via the piecewise function, the gap automatically appears.
            position > 0f -> RectF(width - incomingWidth, 0f, width, height)

            // Current cover: mask to normal.
            else -> RectF(0f, 0f, width, height)
        }

        maskable.setMaskRectF(rect)

        // Internal page parallax, where left content moves further left and right content moves
        // further right. This assumes the cover item setup, but that's fine.
        val content = page.getChildAt(0)
        val maskCenter = (rect.left + rect.right) / 2f
        val pageCenter = width / 2f
        content.translationX = (maskCenter - pageCenter) * PARALLAX
    }

    private companion object {
        const val GAP_BREAKPOINT = 0.18f
        const val MAX_GAP_FRACTION = 0.08f
        const val PARALLAX = 0.2f
    }
}
