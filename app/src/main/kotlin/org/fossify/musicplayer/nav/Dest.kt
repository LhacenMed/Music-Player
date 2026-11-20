package org.fossify.musicplayer.nav

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.fragment.app.Fragment
import org.fossify.musicplayer.fragments.ExcludedFoldersFragment
import org.fossify.musicplayer.screens.PlaylistsScreen
import org.fossify.musicplayer.screens.SettingsScreen
import java.io.Serializable

/**
 * Type-safe catalogue of every screen the shared host renders.
 *
 * A destination supplies its own body and its own title, and nothing else knows it exists: adding
 * one is an entry here plus its body, with NO Activity class and NO manifest line - the single
 * already-declared [org.fossify.musicplayer.activities.ScreenHostActivity] serves them all.
 *
 * A body is either Compose ([screen]) or a native [fragment]. Both get the same chrome, so the
 * choice is only about what the screen is easiest to build in. A fragment can also contribute
 * toolbar actions through the standard [androidx.core.view.MenuProvider] API and run a contextual
 * action bar; a Compose body cannot, which is what decides the kind for a screen with either.
 *
 * Destinations are [Serializable] and travel to the host as an intent extra, so a destination's
 * constructor properties are its typed arguments and call sites stay type-safe:
 * `nav.go(Dest.ExcludedFolders)`.
 */
sealed class Dest : Serializable {

    /** Compose body. Rendered by the host inside its chrome; null for a native destination. */
    open fun screen(): (@Composable () -> Unit)? = null

    /** Native (View-based) body, for a screen built from XML and platform widgets. */
    open fun fragment(): Fragment? = null

    /** Title for the host's top bar. */
    @get:StringRes
    open val titleRes: Int? get() = null

    /**
     * Toolbar title resolved at show time; defaults to [titleRes]. Override for a dynamic title -
     * one that embeds an argument, say - so the bar is complete on the host's first frame.
     */
    open fun title(context: Context): String? = titleRes?.let(context::getString)

    data object Settings : Dest() {
        override val titleRes get() = org.fossify.commons.R.string.settings
        override fun screen() = @Composable { SettingsScreen() }
    }

    /**
     * Native rather than Compose: the folder list is a Commons `MyRecyclerViewAdapter`, whose
     * multi-select delete runs through the platform contextual action bar - chrome only a
     * [fragment] destination can drive.
     */
    data object ExcludedFolders : Dest() {
        override val titleRes get() = org.fossify.commons.R.string.manage_excluded_folders
        override fun fragment() = ExcludedFoldersFragment()
    }

    /** The screen the Playlists library shortcut opens. Owns its own top bar - see [PlaylistsScreen]. */
    data object Playlists : Dest() {
        override val titleRes get() = org.fossify.musicplayer.R.string.playlists
        override fun screen() = @Composable { PlaylistsScreen() }
    }
}

/**
 * Navigation entry point used by screen content. The host Activity supplies the implementation
 * (intent dispatch); leaf screens just call [go] / [back].
 */
interface AppNavigator {
    /** Launch the host for [dest]. */
    fun go(dest: Dest)

    /** Finish the current screen (native back). */
    fun back()
}

/** Provided by every host so any composable in the tree can navigate. */
val LocalNavigator = staticCompositionLocalOf<AppNavigator> {
    error("No AppNavigator provided. Did the host wrap content in CompositionLocalProvider(LocalNavigator provides ...)?")
}
