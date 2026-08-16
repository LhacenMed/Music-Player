package org.fossify.musicplayer.fragments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.musicplayer.activities.SimpleActivity
import org.fossify.musicplayer.activities.SimpleControllerActivity
import org.fossify.musicplayer.models.Track

abstract class MyViewPagerFragment(context: Context, attributeSet: AttributeSet) : RelativeLayout(context, attributeSet) {
    /** The list this tab is built around, so that re-selecting its tab can send it back to the top. */
    protected abstract val list: RecyclerView

    abstract fun setupFragment(activity: BaseSimpleActivity)

    abstract fun finishActMode()

    abstract fun onSearchQueryChanged(text: String)

    abstract fun onSearchClosed()

    abstract fun onSortOpen(activity: SimpleActivity)

    abstract fun setupColors(textColor: Int, adjustedPrimaryColor: Int)

    /**
     * Move the playing indicator to whatever the player is on now. A no-op for tabs that list
     * something other than tracks, which have no row for it to land on.
     */
    open fun onPlayingTrackChanged(trackId: Long, isPlaying: Boolean) {}

    /**
     * Send the list back to its first entry, the way tapping an already active navigation tab does
     * everywhere else on the platform.
     *
     * Smooth-scrolling the whole way from deep in a library-sized list would take far too long, so
     * anything past [SMOOTH_SCROLL_FROM] is jumped to that mark first and only the last stretch is
     * animated. The result reads as one quick glide regardless of how far down the list was.
     */
    fun scrollToTop() {
        val layoutManager = list.layoutManager as LinearLayoutManager
        if (layoutManager.findFirstVisibleItemPosition() > SMOOTH_SCROLL_FROM) {
            list.scrollToPosition(SMOOTH_SCROLL_FROM)
        }

        list.smoothScrollToPosition(0)
    }

    fun playTrack(tracks: List<Track>, startIndex: Int) {
        (context as SimpleControllerActivity).playTrack(tracks, startIndex)
    }

    private companion object {
        /** The furthest down the list a return to the top is still animated the whole way. */
        const val SMOOTH_SCROLL_FROM = 12
    }
}
