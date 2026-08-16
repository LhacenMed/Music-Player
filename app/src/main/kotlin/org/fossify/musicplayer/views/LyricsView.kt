package org.fossify.musicplayer.views

import android.content.Context
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.core.view.isInvisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import org.fossify.musicplayer.R
import org.fossify.musicplayer.databinding.ViewLyricsBinding
import org.fossify.musicplayer.models.Lyrics
import org.fossify.musicplayer.models.TimedLine

/**
 * A scrolling window onto a track's lyric lines, rendered at the given `lyricsDensity`.
 *
 * The view is a fixed-height window, so it occupies exactly the same space whether the track has
 * synced lyrics, plain lyrics, or none at all. Synced lyrics highlight and auto-center the active
 * line as playback advances; plain lyrics are simply scrollable. Blank space above the first line
 * and below the last lets either of them reach the center, and the edges fade into the background
 * so only the lines around the active one read clearly.
 */
class LyricsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val binding = ViewLyricsBinding.inflate(LayoutInflater.from(context), this)
    private val density = context.resolveLyricsDensity(attrs)
    private val lyricsAdapter = LyricsAdapter(density)
    private val lyricsLayoutManager = LinearLayoutManager(context)
    private val lineHeight = context.resources.getDimensionPixelSize(density.lineHeight)
    private val tapDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                performClick()
                return false
            }
        }
    )

    private var timedLines: List<TimedLine> = emptyList()
    private var activeIndex = LyricsAdapter.NO_ACTIVE

    init {
        // A View draws its fading edges inside its padding box, so the fade has to live on this
        // container rather than on the deliberately padded list, or it would land over the middle
        // of the window instead of at the top and bottom of it.
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength(context.resources.getDimensionPixelSize(density.fadeLength))
        setWillNotDraw(false)

        binding.lyricsRecycler.apply {
            adapter = lyricsAdapter
            layoutManager = lyricsLayoutManager
            // The sheet only ever registers one scrolling child, and that role belongs to the
            // queue. Staying out of nested scrolling leaves it there; this list claims its own
            // gestures below instead.
            isNestedScrollingEnabled = false
            // Rows never change size, and animating them would fight the auto-scroll.
            itemAnimator = null
            setHasFixedSize(true)
            // Watch for taps without ever claiming the touch, so scrolling still works.
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    tapDetector.onTouchEvent(e)
                    if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                        // The sheet's drag helper claims vertical drags over anything it does not
                        // recognize as scrolling content, so the list has to take the gesture up
                        // front or it will never scroll. With nothing to scroll there is no
                        // gesture worth taking, and the drag is left to the sheet so the area
                        // never becomes dead to it.
                        val isScrollable = rv.canScrollVertically(-1) || rv.canScrollVertically(1)
                        rv.parent.requestDisallowInterceptTouchEvent(isScrollable)
                    }
                    return false
                }
            })
        }
    }

    // The fade is a fixed part of the window's look rather than a scroll affordance, so it holds at
    // full strength instead of tapering off at either end of the list.
    override fun getTopFadingEdgeStrength() = 1f

    override fun getBottomFadingEdgeStrength() = 1f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // The blank run above the first line and below the last one. Sized so that either can be
        // scrolled all the way to the center of the window.
        val blank = ((h - lineHeight) / 2).coerceAtLeast(0)
        binding.lyricsRecycler.updatePadding(top = blank, bottom = blank)
    }

    /**
     * Display [state], seeding the active line from [positionMs] so that lyrics arriving while
     * paused (or mid-track) are immediately correct rather than waiting for the next tick.
     */
    fun update(state: LyricsState, positionMs: Long) {
        val lyrics = (state as? LyricsState.Loaded)?.lyrics
        timedLines = (lyrics as? Lyrics.Synced)?.timedLines ?: emptyList()
        lyricsAdapter.submit(lyrics?.lines ?: emptyList())

        binding.lyricsRecycler.isInvisible = lyrics == null
        binding.lyricsPlaceholder.isInvisible = state !is LyricsState.Empty

        activeIndex = indexAt(positionMs, LyricsAdapter.NO_ACTIVE)
        lyricsAdapter.setActiveIndex(activeIndex)
        jumpTo(activeIndex.coerceAtLeast(0))
    }

    /** Advance the highlight to whichever line [positionMs] falls in. A no-op for plain lyrics. */
    fun seekTo(positionMs: Long) {
        if (timedLines.isEmpty()) return
        val index = indexAt(positionMs, activeIndex)
        if (index == activeIndex) return
        activeIndex = index
        lyricsAdapter.setActiveIndex(index)
        if (index != LyricsAdapter.NO_ACTIVE) {
            centerOn(index)
        }
    }

    /**
     * Find the last line that has started by [positionMs], or [LyricsAdapter.NO_ACTIVE] if none
     * has yet.
     *
     * @param hint The previously active index. Playback is overwhelmingly monotonic, so checking
     *   the lines around it resolves almost every call without a search.
     */
    private fun indexAt(positionMs: Long, hint: Int): Int {
        if (hint > LyricsAdapter.NO_ACTIVE && hint < timedLines.size && timedLines[hint].startMs <= positionMs) {
            val next = hint + 1
            if (next == timedLines.size || timedLines[next].startMs > positionMs) return hint
            if (next + 1 == timedLines.size || timedLines[next + 1].startMs > positionMs) return next
        }

        return search(positionMs)
    }

    /** Binary search fallback for seeks and freshly loaded lyrics. */
    private fun search(positionMs: Long): Int {
        var low = 0
        var high = timedLines.size - 1
        var found = LyricsAdapter.NO_ACTIVE
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (timedLines[mid].startMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return found
    }

    /** Place [index] in the middle of the window without animating. */
    private fun jumpTo(index: Int) {
        // The blank run applied in onSizeChanged already offsets the list, so seating the line
        // against the start of it leaves the line centered.
        lyricsLayoutManager.scrollToPositionWithOffset(index, 0)
    }

    /** Glide [index] into the middle of the window. */
    private fun centerOn(index: Int) {
        // A fresh scroller each time: RecyclerView will not restart one that is still running.
        lyricsLayoutManager.startSmoothScroll(CenterSmoothScroller(context).apply { targetPosition = index })
    }
}

private fun Context.resolveLyricsDensity(attrs: AttributeSet?): LyricsDensity {
    val styled = obtainStyledAttributes(attrs, R.styleable.LyricsView)
    val ordinal = styled.getInt(R.styleable.LyricsView_lyricsDensity, LyricsDensity.COMPACT.ordinal)
    styled.recycle()
    return LyricsDensity.entries[ordinal]
}

/** A [LinearSmoothScroller] that centers its target and moves at a reading-friendly pace. */
private class CenterSmoothScroller(context: Context) : LinearSmoothScroller(context) {
    override fun calculateDtToFit(
        viewStart: Int,
        viewEnd: Int,
        boxStart: Int,
        boxEnd: Int,
        snapPreference: Int
    ) = (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)

    override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics) =
        MILLIS_PER_INCH / displayMetrics.densityDpi

    private companion object {
        /** Well below the 25f default, so lines drift rather than snap. */
        const val MILLIS_PER_INCH = 90f
    }
}
