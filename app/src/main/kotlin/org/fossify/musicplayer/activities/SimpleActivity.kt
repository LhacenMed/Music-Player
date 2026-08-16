package org.fossify.musicplayer.activities

import android.os.Bundle
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.musicplayer.R
import org.fossify.musicplayer.helpers.REPOSITORY_NAME

open class SimpleActivity : BaseSimpleActivity() {
    // BaseSimpleActivity swaps in one of Commons' own accent-coloured themes via
    // setTheme(getThemeId()) in both onCreate() and onResume(), which replaces our AppTheme
    // wholesale. Re-applying the typography overlay after each call is what makes Inter survive
    // that swap instead of silently falling back to the platform default font.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        theme.applyStyle(R.style.ThemeOverlay_App_Typography, true)
    }

    override fun onResume() {
        super.onResume()
        theme.applyStyle(R.style.ThemeOverlay_App_Typography, true)
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
