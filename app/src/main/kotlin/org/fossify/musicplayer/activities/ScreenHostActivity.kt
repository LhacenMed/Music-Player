package org.fossify.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ScrollingView
import androidx.core.view.isVisible
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.musicplayer.databinding.ActivityScreenHostBinding
import org.fossify.musicplayer.nav.Dest
import org.fossify.musicplayer.nav.IntentNavigator
import org.fossify.musicplayer.nav.LocalNavigator
import org.fossify.musicplayer.theme.AppTheme

/**
 * The single host Activity for every [Dest] - so adding a screen never touches the manifest, since
 * this one entry serves them all. Each navigation launches a fresh instance, which leaves the
 * platform owning the back stack, predictive back and transitions.
 *
 * A Compose destination ([Dest.screen]) renders its own top bar as part of its own composition
 * (see [org.fossify.musicplayer.theme.AppTheme] and `screens/components/AppTopBar.kt`) - the host
 * contributes no chrome of its own for it. A native destination ([Dest.fragment]) still needs
 * Commons' XML chrome, since it can drive a contextual action bar a Compose body cannot; the host
 * keeps `host_appbar` for exactly that remaining case.
 *
 * Extends [SimpleMusicActivity] so every hosted screen shows the same mini player every other
 * screen in the app does, wired up by [setupPlaybackSheet] exactly as any other music screen would.
 */
class ScreenHostActivity : SimpleMusicActivity() {

    private val binding by viewBinding(ActivityScreenHostBinding::inflate)

    /** Null only on the malformed-intent path, where [onCreate] has already finished the host. */
    private var dest: Dest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dest = intent.dest() ?: return finish()
        this.dest = dest

        setContentView(binding.root)
        setupEdgeToEdge()
        setupPlaybackSheet()

        val body = dest.fragment()
        if (body != null) {
            binding.hostAppbar.isVisible = true
            // The FragmentManager restores the body itself after a configuration change.
            if (savedInstanceState == null) {
                supportFragmentManager.beginTransaction()
                    .replace(binding.hostBody.id, body)
                    .commit()
            }
            return
        }

        val screen = dest.screen() ?: return finish()
        val navigator = IntentNavigator(this)
        binding.hostBody.addView(
            ComposeView(this).apply {
                setContent {
                    AppTheme {
                        CompositionLocalProvider(LocalNavigator provides navigator) {
                            Box(Modifier.fillMaxSize()) {
                                screen()
                            }
                        }
                    }
                }
            }
        )
    }

    /**
     * In `onResume`, like every other screen: Commons re-reads the theme here, so the bar is
     * repainted after a night-mode change rather than keeping the palette it was built with.
     *
     * Only reached for a native destination - `host_appbar` stays gone, and this a no-op, for a
     * Compose one.
     */
    override fun onResume() {
        super.onResume()
        val dest = dest ?: return
        if (!binding.hostAppbar.isVisible) return
        setupTopAppBar(binding.hostAppbar, NavigationIcon.Arrow)
        // Assigned as a string rather than passed as a res id, so a destination with a dynamic
        // title (one embedding an argument) needs nothing more from the host.
        binding.hostAppbar.requireToolbar().title = dest.title(this)
    }

    /**
     * Takes a native body's scrolling list and gives it the lifting top bar and the bottom system
     * inset - everything a [Dest.fragment] needs from the chrome around it, so the fragment never
     * has to reach for the host's own views.
     */
    fun <T> bindScrollingBody(view: T) where T : View, T : ScrollingView {
        setupEdgeToEdge(padBottomSystem = listOf(view))
        setupMaterialScrollListener(view, binding.hostAppbar)
    }

    /**
     * The destination this launch asks for: the one the navigator attached, or - for the system's
     * "app settings" action, which names the screen by its action rather than by an extra -
     * [Dest.Settings].
     */
    private fun Intent.dest(): Dest? {
        @Suppress("DEPRECATION")
        val attached = getSerializableExtra(EXTRA_DEST) as? Dest
        return attached ?: Dest.Settings.takeIf { action == Intent.ACTION_APPLICATION_PREFERENCES }
    }

    companion object {
        const val EXTRA_DEST = "dest"
    }
}
