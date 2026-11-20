package org.fossify.musicplayer.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.Window
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import org.fossify.musicplayer.R

/**
 * The [ColorScheme] for [accentIndex], generated from a seed colour rather than read back from an
 * XML theme - see [AppearanceState] for why. Material You keeps using the platform's own wallpaper
 * palette; every named accent seeds MaterialKolor with the same primary colour [Accent] already
 * shows for it in the picker, so no palette data is duplicated.
 */
private fun seedColorScheme(context: Context, accentIndex: Int, isDark: Boolean): ColorScheme {
    val isMaterialYou = accentIndex == Accent.DYNAMIC_INDEX && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return if (isMaterialYou) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val seed = Color(context.getColor(Accent.from(accentIndex).primary))
        dynamicColorScheme(seedColor = seed, isDark = isDark, isAmoled = false, style = PaletteStyle.TonalSpot)
    }
}

/** Forces black surfaces while dark, for OLED screens - only meaningful together with [isDark]. */
private fun ColorScheme.withPureBlack(isDark: Boolean, pureBlack: Boolean): ColorScheme =
    if (isDark && pureBlack) copy(background = Color.Black, surface = Color.Black) else this

/** Cross-fades every role of [target] in from the previous composition's scheme, frame by frame. */
@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    @Composable
    fun animate(color: Color) = animateColorAsState(color, label = "themeColor").value
    return ColorScheme(
        primary = animate(target.primary),
        onPrimary = animate(target.onPrimary),
        primaryContainer = animate(target.primaryContainer),
        onPrimaryContainer = animate(target.onPrimaryContainer),
        inversePrimary = animate(target.inversePrimary),
        secondary = animate(target.secondary),
        onSecondary = animate(target.onSecondary),
        secondaryContainer = animate(target.secondaryContainer),
        onSecondaryContainer = animate(target.onSecondaryContainer),
        tertiary = animate(target.tertiary),
        onTertiary = animate(target.onTertiary),
        tertiaryContainer = animate(target.tertiaryContainer),
        onTertiaryContainer = animate(target.onTertiaryContainer),
        background = animate(target.background),
        onBackground = animate(target.onBackground),
        surface = animate(target.surface),
        onSurface = animate(target.onSurface),
        surfaceVariant = animate(target.surfaceVariant),
        onSurfaceVariant = animate(target.onSurfaceVariant),
        surfaceTint = animate(target.surfaceTint),
        inverseSurface = animate(target.inverseSurface),
        inverseOnSurface = animate(target.inverseOnSurface),
        error = animate(target.error),
        onError = animate(target.onError),
        errorContainer = animate(target.errorContainer),
        onErrorContainer = animate(target.onErrorContainer),
        outline = animate(target.outline),
        outlineVariant = animate(target.outlineVariant),
        scrim = animate(target.scrim),
        surfaceBright = animate(target.surfaceBright),
        surfaceDim = animate(target.surfaceDim),
        surfaceContainer = animate(target.surfaceContainer),
        surfaceContainerHigh = animate(target.surfaceContainerHigh),
        surfaceContainerHighest = animate(target.surfaceContainerHighest),
        surfaceContainerLow = animate(target.surfaceContainerLow),
        surfaceContainerLowest = animate(target.surfaceContainerLowest),
    )
}

/** Inter, the same family `values/typography.xml` gives every native text appearance. */
private val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/** Material's baseline type with Inter in place of the platform default. */
private val InterTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = InterFamily),
        displayMedium = displayMedium.copy(fontFamily = InterFamily),
        displaySmall = displaySmall.copy(fontFamily = InterFamily),
        headlineLarge = headlineLarge.copy(fontFamily = InterFamily),
        headlineMedium = headlineMedium.copy(fontFamily = InterFamily),
        headlineSmall = headlineSmall.copy(fontFamily = InterFamily),
        titleLarge = titleLarge.copy(fontFamily = InterFamily),
        titleMedium = titleMedium.copy(fontFamily = InterFamily),
        titleSmall = titleSmall.copy(fontFamily = InterFamily),
        bodyLarge = bodyLarge.copy(fontFamily = InterFamily),
        bodyMedium = bodyMedium.copy(fontFamily = InterFamily),
        bodySmall = bodySmall.copy(fontFamily = InterFamily),
        labelLarge = labelLarge.copy(fontFamily = InterFamily),
        labelMedium = labelMedium.copy(fontFamily = InterFamily),
        labelSmall = labelSmall.copy(fontFamily = InterFamily),
    )
}

/** The title style of a settings row - larger than titleMedium, so a row reads as its own line. */
val preferenceTitle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 20.sp,
    lineHeight = 24.sp,
)

/** Walks the context chain to the hosting Activity's Window (for system-bar control). */
private tailrec fun Context.findWindow(): Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.findWindow()
    else -> null
}

/**
 * Wraps Compose content in the app's active palette.
 *
 * Unlike the rest of the app's chrome, this reads the accent, dark mode and black-theme choice
 * from [AppearanceState] rather than the host Activity's already-inflated theme, so a Compose
 * screen recolors the moment the choice changes - no `recreate()` needed. See [AppearanceState]'s
 * doc for the full reasoning; native chrome keeps following the XML [Accent] theme exactly as
 * before.
 *
 * Uses plain [MaterialTheme] rather than Material3's `MaterialExpressiveTheme`/`MotionScheme`:
 * the BOM this project currently builds against doesn't expose those publicly yet, and the
 * newer alpha that does requires compileSdk 37, which this environment's SDK manager can't
 * install (network-restricted). [org.fossify.musicplayer.screens.components.ExpressiveButton]
 * gets its press morph from a plain spring spec instead, for the same reason.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val accentIndex by rememberAccentIndex()
    val darkMode by rememberDarkMode()
    val pureBlack by rememberPureBlack()

    val isDark = when (darkMode) {
        DarkMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }

    val target = seedColorScheme(context, accentIndex, isDark).withPureBlack(isDark, pureBlack)
    val colorScheme = animateColorScheme(target)

    val view = LocalView.current
    val window = view.context.findWindow()
    if (!view.isInEditMode && window != null) {
        SideEffect {
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = InterTypography,
        content = content,
    )
}
