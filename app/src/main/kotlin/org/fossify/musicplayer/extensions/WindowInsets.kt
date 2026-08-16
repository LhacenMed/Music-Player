package org.fossify.musicplayer.extensions

import android.os.Build
import android.view.WindowInsets
import androidx.annotation.RequiresApi
import androidx.core.graphics.Insets

/**
 * Get the "System Bar" [Insets] in this [WindowInsets] instance in a version-compatible manner.
 * This can be used to prevent views from intersecting with the navigation bars.
 */
val WindowInsets.systemBarInsetsCompat: Insets
    get() = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> getCompatInsets(WindowInsets.Type.systemBars())
        else -> getSystemWindowCompatInsets()
    }

/**
 * Get the "System Gesture" [Insets] in this [WindowInsets] instance in a version-compatible manner.
 * This can be used to prevent views from intersecting with the navigation bars and their extended
 * gesture hit-boxes. Note that "System Bar" insets will be used if the system does not provide
 * gesture insets.
 */
val WindowInsets.systemGestureInsetsCompat: Insets
    get() =
        // Some android versions seemingly don't provide gesture insets, setting them to zero.
        // To resolve this, we take the maximum between the system bar and system gesture insets.
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Insets.max(
                getCompatInsets(WindowInsets.Type.systemGestures()),
                getCompatInsets(WindowInsets.Type.systemBars())
            )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> Insets.max(
                getSystemGestureCompatInsets(),
                getSystemWindowCompatInsets()
            )

            else -> getSystemWindowCompatInsets()
        }

/**
 * Get the "IME" [Insets] in this [WindowInsets] instance in a version-compatible manner. Versions
 * that do not report the keyboard resize the window for it instead, so nothing has to be reserved
 * there.
 */
val WindowInsets.imeInsetsCompat: Insets
    get() = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> getCompatInsets(WindowInsets.Type.ime())
        else -> Insets.NONE
    }

/**
 * Replace the "System Bar" [Insets] in this [WindowInsets] with a new set of [Insets].
 */
fun WindowInsets.replaceSystemBarInsetsCompat(left: Int, top: Int, right: Int, bottom: Int): WindowInsets {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> WindowInsets.Builder(this)
            .setInsets(WindowInsets.Type.systemBars(), Insets.of(left, top, right, bottom).toPlatformInsets())
            .build()

        else -> @Suppress("DEPRECATION") replaceSystemWindowInsets(left, top, right, bottom)
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private fun WindowInsets.getCompatInsets(typeMask: Int) = Insets.toCompatInsets(getInsets(typeMask))

@Suppress("DEPRECATION")
private fun WindowInsets.getSystemWindowCompatInsets() = Insets.of(
    systemWindowInsetLeft,
    systemWindowInsetTop,
    systemWindowInsetRight,
    systemWindowInsetBottom
)

@Suppress("DEPRECATION")
@RequiresApi(Build.VERSION_CODES.Q)
private fun WindowInsets.getSystemGestureCompatInsets() = Insets.toCompatInsets(systemGestureInsets)
