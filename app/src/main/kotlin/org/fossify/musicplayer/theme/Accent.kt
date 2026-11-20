package org.fossify.musicplayer.theme

import android.os.Build
import org.fossify.musicplayer.R

/**
 * One of the colour schemes the app can be themed with, ported from Auxio.
 *
 * An accent is not a colour - it is a whole theme. Each entry below names a style that overrides
 * all forty-six Material colour roles at once, and the app is themed by handing that style to
 * setTheme() before anything inflates. Nothing is computed at runtime, which is what lets dialogs,
 * popup menus, the contextual action bar and ripples follow the accent without a line of code:
 * they resolve the same theme attributes as every other view.
 *
 * Light and dark are not part of this. The themes reference colour names that exist twice - once
 * in values/colors_ui.xml and once in values-night/colors_ui.xml - so the framework picks the
 * palette and the accent never has to know which mode it is in.
 *
 * The last entry is Material You. It maps to the base theme, which overrides no colours at all and
 * therefore keeps the dynamic palette the platform supplies on API 31+. That is the whole
 * mechanism: "dynamic" is simply the accent that declines to override anything.
 */
@JvmInline
value class Accent private constructor(val index: Int) {
    /** The name shown for this accent. */
    val name: Int
        get() = ACCENT_NAMES[index]

    /** The theme resource this accent applies. */
    val theme: Int
        get() = ACCENT_THEMES[index]

    /** The same theme with black surfaces, for OLED screens. Only used while dark. */
    val blackTheme: Int
        get() = ACCENT_BLACK_THEMES[index]

    /** This accent's primary colour, for showing the accent itself in the picker. */
    val primary: Int
        get() = ACCENT_PRIMARY_COLORS[index]

    companion object {
        /**
         * Material You where the platform has it, blue otherwise - never Material's default
         * purple, which is what an un-themed Material 3 app falls back to.
         */
        val DEFAULT = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ACCENT_THEMES.lastIndex
        } else {
            BLUE_INDEX
        }

        /** How many accents are selectable. Material You is hidden where it does not exist. */
        val MAX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ACCENT_THEMES.size
        } else {
            ACCENT_THEMES.size - 1
        }

        fun from(index: Int) = Accent(if (index in 0 until MAX) index else DEFAULT)

        /** The Material You entry's fixed index, regardless of which accent is currently chosen. */
        val DYNAMIC_INDEX = ACCENT_THEMES.lastIndex
    }
}

private const val BLUE_INDEX = 5

private val ACCENT_NAMES = intArrayOf(
    R.string.accent_red,
    R.string.accent_pink,
    R.string.accent_purple,
    R.string.accent_deep_purple,
    R.string.accent_indigo,
    R.string.accent_blue,
    R.string.accent_deep_blue,
    R.string.accent_cyan,
    R.string.accent_teal,
    R.string.accent_green,
    R.string.accent_deep_green,
    R.string.accent_lime,
    R.string.accent_yellow,
    R.string.accent_orange,
    R.string.accent_brown,
    R.string.accent_grey,
    R.string.accent_dynamic,
)

private val ACCENT_THEMES = intArrayOf(
    R.style.Theme_Music_Red,
    R.style.Theme_Music_Pink,
    R.style.Theme_Music_Purple,
    R.style.Theme_Music_DeepPurple,
    R.style.Theme_Music_Indigo,
    R.style.Theme_Music_Blue,
    R.style.Theme_Music_DeepBlue,
    R.style.Theme_Music_Cyan,
    R.style.Theme_Music_Teal,
    R.style.Theme_Music_Green,
    R.style.Theme_Music_DeepGreen,
    R.style.Theme_Music_Lime,
    R.style.Theme_Music_Yellow,
    R.style.Theme_Music_Orange,
    R.style.Theme_Music_Brown,
    R.style.Theme_Music_Grey,
    // Material You lives on the base theme, which overrides nothing.
    R.style.Theme_Music_App,
)

private val ACCENT_BLACK_THEMES = intArrayOf(
    R.style.Theme_Music_Red_Black,
    R.style.Theme_Music_Pink_Black,
    R.style.Theme_Music_Purple_Black,
    R.style.Theme_Music_DeepPurple_Black,
    R.style.Theme_Music_Indigo_Black,
    R.style.Theme_Music_Blue_Black,
    R.style.Theme_Music_DeepBlue_Black,
    R.style.Theme_Music_Cyan_Black,
    R.style.Theme_Music_Teal_Black,
    R.style.Theme_Music_Green_Black,
    R.style.Theme_Music_DeepGreen_Black,
    R.style.Theme_Music_Lime_Black,
    R.style.Theme_Music_Yellow_Black,
    R.style.Theme_Music_Orange_Black,
    R.style.Theme_Music_Brown_Black,
    R.style.Theme_Music_Grey_Black,
    R.style.Theme_Music_Black,
)

private val ACCENT_PRIMARY_COLORS = intArrayOf(
    R.color.red_primary,
    R.color.pink_primary,
    R.color.purple_primary,
    R.color.deep_purple_primary,
    R.color.indigo_primary,
    R.color.blue_primary,
    R.color.deep_blue_primary,
    R.color.cyan_primary,
    R.color.teal_primary,
    R.color.green_primary,
    R.color.deep_green_primary,
    R.color.lime_primary,
    R.color.yellow_primary,
    R.color.orange_primary,
    R.color.brown_primary,
    R.color.grey_primary,
    R.color.dynamic_primary,
)
