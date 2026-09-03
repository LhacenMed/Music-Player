package org.fossify.musicplayer.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.musicplayer.R
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.isNight
import org.fossify.musicplayer.helpers.REPOSITORY_NAME

open class SimpleActivity : BaseSimpleActivity() {
    init {
        // Fossify Commons reads this at the top of both onCreate and onResume, and only then calls
        // setTheme(getThemeId()) - swapping in one of its own generated accent themes and
        // repainting the window background from its own config. Turning it off before either runs
        // is what leaves this app's themes as the only thing colouring it. Commons offers no other
        // way out: getThemeId is a top-level extension, not an overridable method.
        useDynamicTheme = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
    }

    /**
     * Apply the configured theme, ported from Auxio's MainActivity.setupTheme.
     *
     * Order matters twice over. This runs before super.onCreate, and therefore before any layout is
     * inflated, because a theme only reaches views that are created after it is set. And the night
     * mode is handed to AppCompat first, because whether the black theme applies depends on the
     * mode that lands.
     *
     * Black needs its own themes rather than a flag: a theme cannot be modified once applied, so
     * "black" is a second set of themes overriding the surface roles, and the choice is made here.
     */
    private fun applyTheme() {
        AppCompatDelegate.setDefaultNightMode(config.themeMode)

        val accent = config.accent
        setTheme(if (isNight && config.useBlackTheme) accent.blackTheme else accent.theme)
    }

    override fun getAppIconIDs() = arrayListOf(
        R.mipmap.ic_launcher_red,
        R.mipmap.ic_launcher_pink,
        R.mipmap.ic_launcher_purple,
        R.mipmap.ic_launcher_deep_purple,
        R.mipmap.ic_launcher_indigo,
        R.mipmap.ic_launcher_blue,
        R.mipmap.ic_launcher_light_blue,
        R.mipmap.ic_launcher_cyan,
        R.mipmap.ic_launcher_teal,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher_light_green,
        R.mipmap.ic_launcher_lime,
        R.mipmap.ic_launcher_yellow,
        R.mipmap.ic_launcher_amber,
        R.mipmap.ic_launcher_orange,
        R.mipmap.ic_launcher_deep_orange,
        R.mipmap.ic_launcher_brown,
        R.mipmap.ic_launcher_blue_grey,
        R.mipmap.ic_launcher_grey_black
    )

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

    override fun getRepositoryName() = REPOSITORY_NAME
}
