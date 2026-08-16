package org.fossify.musicplayer.views

import android.content.Context
import android.util.AttributeSet
import android.view.WindowInsets
import androidx.core.view.updatePadding
import org.fossify.commons.views.MyRecyclerView
import org.fossify.musicplayer.extensions.imeInsetsCompat
import org.fossify.musicplayer.extensions.systemBarInsetsCompat

/**
 * A [MyRecyclerView] that keeps its last row clear of whatever occupies the bottom of the window.
 *
 * Every sheet behavior folds what it covers into the "system bar" insets it hands down — the
 * playback bar, and the queue handle stacked inside it — so a list only has to honour the insets it
 * is given to come to rest above the navigation bar, the playback bar and the queue in every
 * combination of the three, and to reclaim the space again the moment a sheet goes away.
 *
 * The space is padding rather than a shorter list, so rows still scroll under what covers them.
 */
class BottomInsetRecyclerView(context: Context, attrs: AttributeSet) : MyRecyclerView(context, attrs) {
    private val basePaddingBottom = paddingBottom

    init {
        clipToPadding = false
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        // The keyboard is drawn over the sheets rather than pushing them up, so it replaces their
        // inset while it is up instead of adding to it.
        val occluded = maxOf(insets.systemBarInsetsCompat.bottom, insets.imeInsetsCompat.bottom)
        updatePadding(bottom = basePaddingBottom + occluded)
        android.util.Log.e("InsetProbe", "id=${resources.getResourceEntryName(id)} sys=${insets.systemBarInsetsCompat.bottom} ime=${insets.imeInsetsCompat.bottom} padBottom=$paddingBottom h=$height")
        return insets
    }
}
