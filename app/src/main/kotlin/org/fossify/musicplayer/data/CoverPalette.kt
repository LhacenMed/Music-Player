package org.fossify.musicplayer.data

import android.graphics.Bitmap
import androidx.palette.graphics.Palette

/**
 * Pick the accent color this cover art suggests, or null if it doesn't suggest one usable (too
 * washed out, or too small a swatch to trust).
 *
 * Priority mirrors what a listener's eye goes to first: a vibrant color if the art has one,
 * otherwise its most common bold color, otherwise a muted tone - checking both the light and dark
 * variant of each before giving up.
 */
fun Bitmap.extractAccentColor(): Int? {
    val palette = Palette.from(this).maximumColorCount(16).generate()
    return sequenceOf(
        palette.vibrantSwatch,
        palette.dominantSwatch,
        palette.mutedSwatch,
        palette.lightVibrantSwatch,
        palette.darkVibrantSwatch,
        palette.lightMutedSwatch,
        palette.darkMutedSwatch
    ).firstNotNullOfOrNull { it }?.rgb
}
