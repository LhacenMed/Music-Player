package org.fossify.musicplayer.views

import android.view.View
import com.google.android.material.appbar.AppBarLayout
import kotlin.math.abs

/**
 * Auxio's FadingToolbarOffsetListener.
 *
 * Auxio collapses a single app bar child and so fades a single view; this app collapses the search
 * bar and the shortcut row, which move as one, so the same alpha is applied to both. The content
 * padding Auxio's copy also applies is left out, since this app's lists already take their bottom
 * padding from the insets the playback sheet hands down.
 *
 * The fade runs over the whole scroll range rather than the half Auxio shifts it to. Auxio shifts
 * it so its toolbar clears the status bar early, whereas here the range is the search bar and the
 * shortcuts together, so running it over the full range is what lands the two of them at nothing
 * exactly as the tabs come to rest against the top inset. Both ends come from the bar's own
 * geometry, so no distance or duration is configured anywhere.
 */
class FadingAppBarOffsetListener(private val collapsing: List<View>) :
    AppBarLayout.OnOffsetChangedListener {
    override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
        val range = appBarLayout.totalScrollRange
        if (range == 0) {
            return
        }

        val alpha = 1f - (abs(verticalOffset.toFloat()) / range.toFloat())
        collapsing.forEach { it.alpha = alpha }
    }
}
