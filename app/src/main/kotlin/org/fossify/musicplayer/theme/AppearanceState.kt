package org.fossify.musicplayer.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.fossify.musicplayer.extensions.config

/**
 * The palette a Compose screen is painted with, kept independent of the Activity that shows it.
 *
 * The app's native chrome (still every other Activity, plus Commons' own dialogs) is themed by a
 * whole [Accent] style applied at setTheme() time, which only takes effect on the next inflate -
 * hence [org.fossify.musicplayer.activities.SimpleActivity]'s `recreate()` when it changes. A
 * Compose screen has no such moment: it is already composed, so the only way for it to follow a
 * changed accent or dark mode without waiting for a recreate is to read the choice from state
 * Compose can observe directly. This store is that state - a small DataStore mirror of [Config]'s
 * `accent`/`themeMode`/`useBlackTheme`, read reactively by [AppTheme] instead of resolved once
 * from the Activity's theme.
 *
 * Whoever changes the choice (currently `SettingsScreen`) writes both: [Config] for native chrome,
 * still followed by the usual `recreate()`, and this store for every Compose screen already on
 * screen to repaint immediately.
 */
enum class DarkMode { SYSTEM, LIGHT, DARK }

private val Context.appearanceDataStore by preferencesDataStore("appearance")

private val ACCENT_INDEX = intPreferencesKey("accent_index")
private val DARK_MODE = stringPreferencesKey("dark_mode")
private val PURE_BLACK = booleanPreferencesKey("pure_black")

private val appearanceScope = CoroutineScope(Dispatchers.IO)

/** Maps an AppCompatDelegate MODE_NIGHT_* value to [DarkMode]. */
fun Int.toDarkMode() = when (this) {
    AppCompatDelegate.MODE_NIGHT_YES -> DarkMode.DARK
    AppCompatDelegate.MODE_NIGHT_NO -> DarkMode.LIGHT
    else -> DarkMode.SYSTEM
}

@Composable
fun rememberAccentIndex(): State<Int> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val default = context.config.accent.index
    return context.appearanceDataStore.data
        .map { it[ACCENT_INDEX] ?: default }
        .distinctUntilChanged()
        .collectAsState(default)
}

@Composable
fun rememberDarkMode(): State<DarkMode> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val default = context.config.themeMode.toDarkMode()
    return context.appearanceDataStore.data
        .map { pref -> pref[DARK_MODE]?.let(DarkMode::valueOf) ?: default }
        .distinctUntilChanged()
        .collectAsState(default)
}

@Composable
fun rememberPureBlack(): State<Boolean> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val default = context.config.useBlackTheme
    return context.appearanceDataStore.data
        .map { it[PURE_BLACK] ?: default }
        .distinctUntilChanged()
        .collectAsState(default)
}

/** Mirrors [Config]'s accent/dark-mode/black-theme into the store every Compose screen reads. */
fun Context.syncAppearanceState(accentIndex: Int, darkMode: DarkMode, pureBlack: Boolean) {
    appearanceScope.launch {
        appearanceDataStore.edit {
            it[ACCENT_INDEX] = accentIndex
            it[DARK_MODE] = darkMode.name
            it[PURE_BLACK] = pureBlack
        }
    }
}
