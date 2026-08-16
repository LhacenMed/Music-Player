package org.fossify.musicplayer.extensions

import android.content.Context
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.viewbinding.ViewBinding
import org.fossify.commons.extensions.isDynamicTheme
import org.fossify.musicplayer.R

inline fun <T : ViewBinding> View.viewBinding(crossinline bind: (View) -> T) =
    lazy(LazyThreadSafetyMode.NONE) {
        bind(this)
    }

/**
 * Get the [CoordinatorLayout.Behavior] of a [View], or null if the [View] is not part of a
 * [CoordinatorLayout] or does not have a [CoordinatorLayout.Behavior].
 */
val View.coordinatorLayoutBehavior: CoordinatorLayout.Behavior<View>?
    get() = (layoutParams as? CoordinatorLayout.LayoutParams)?.behavior

/**
 * Give a list row the multi-select highlight, drawn on the activated state.
 *
 * The commons equivalent draws it on the selected state, which every music list here needs free to
 * mark the track the player is on. Splitting the two apart is what lets a row be both checked and
 * playing without either reading as the other.
 */
fun View.setupActivatableBackground(context: Context) {
    setBackgroundResource(
        if (context.isDynamicTheme()) {
            R.drawable.selector_activatable_you
        } else {
            R.drawable.selector_activatable
        }
    )
}
