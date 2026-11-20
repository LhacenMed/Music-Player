package org.fossify.musicplayer.screens

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.helpers.IS_CUSTOMIZING_COLORS
import org.fossify.commons.helpers.isTiramisuPlus
import org.fossify.commons.models.RadioItem
import org.fossify.musicplayer.R
import org.fossify.musicplayer.activities.SimpleControllerActivity
import org.fossify.musicplayer.activities.WidgetConfigureActivity
import org.fossify.musicplayer.dialogs.AppUpdateDialog
import org.fossify.musicplayer.dialogs.ManageVisibleTabsDialog
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.isNight
import org.fossify.musicplayer.extensions.sendCommand
import org.fossify.musicplayer.helpers.SHOW_FILENAME_ALWAYS
import org.fossify.musicplayer.helpers.SHOW_FILENAME_IF_UNAVAILABLE
import org.fossify.musicplayer.helpers.SHOW_FILENAME_NEVER
import org.fossify.musicplayer.nav.Dest
import org.fossify.musicplayer.nav.LocalNavigator
import org.fossify.musicplayer.playback.CustomCommands
import org.fossify.musicplayer.screens.components.BackNavigationIcon
import org.fossify.musicplayer.screens.components.PreferenceItem
import org.fossify.musicplayer.screens.components.PreferenceSubtitle
import org.fossify.musicplayer.screens.components.PreferenceSwitch
import org.fossify.musicplayer.screens.components.SmallTopAppBar
import org.fossify.musicplayer.theme.Accent
import org.fossify.musicplayer.theme.syncAppearanceState
import org.fossify.musicplayer.theme.toDarkMode
import org.fossify.musicplayer.update.UpdateChecker
import java.util.Locale
import kotlin.system.exitProcess

/**
 * App settings.
 *
 * Every row reads its value from [config] once and keeps it in composition state, so a change is
 * on screen the moment it is made - which is what the native screen used a full `onResume` rebuild
 * to achieve. The choices that are themselves a theme (accent, black) recreate the Activity, since
 * a theme is only read at inflation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val activity = LocalActivity.current as SimpleControllerActivity
    val context = LocalContext.current
    val config = context.config
    val navigator = LocalNavigator.current

    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    var swapPrevNext by remember { mutableStateOf(config.swapPrevNext) }
    var useBlackTheme by remember { mutableStateOf(config.useBlackTheme) }
    var checkForUpdates by remember { mutableStateOf(config.checkForUpdates) }
    var themeMode by remember { mutableStateOf(config.themeMode) }
    var showFilename by remember { mutableStateOf(config.showFilename) }
    var updateStatus by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SmallTopAppBar(
                title = { Text(stringResource(org.fossify.commons.R.string.settings)) },
                navigationIcon = { BackNavigationIcon(onBack = navigator::back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(scrollState)
    ) {
        PreferenceSubtitle(text = stringResource(org.fossify.commons.R.string.color_customization))

        PreferenceItem(
            title = stringResource(org.fossify.commons.R.string.customize_colors),
            onClick = { activity.startCustomizationActivity() },
        )

        PreferenceItem(
            title = stringResource(org.fossify.commons.R.string.customize_widget_colors),
            onClick = {
                activity.startActivity(
                    Intent(activity, WidgetConfigureActivity::class.java)
                        .putExtra(IS_CUSTOMIZING_COLORS, true)
                )
            },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        PreferenceSubtitle(text = stringResource(org.fossify.commons.R.string.general_settings))

        // Pre-Tiramisu only: from 13 the system's own per-app language picker replaces it.
        if ((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus()) {
            PreferenceSwitch(
                title = stringResource(org.fossify.commons.R.string.use_english_language),
                isChecked = config.useEnglish,
                onClick = {
                    config.useEnglish = !config.useEnglish
                    exitProcess(0)
                },
            )
        }

        if (isTiramisuPlus()) {
            PreferenceItem(
                title = stringResource(org.fossify.commons.R.string.language),
                description = Locale.getDefault().displayLanguage,
                onClick = { activity.launchChangeAppLanguageIntent() },
            )
        }

        PreferenceItem(
            title = stringResource(org.fossify.commons.R.string.manage_excluded_folders),
            onClick = { navigator.go(Dest.ExcludedFolders) },
        )

        PreferenceItem(
            title = stringResource(org.fossify.commons.R.string.manage_shown_tabs),
            onClick = {
                ManageVisibleTabsDialog(activity) { result ->
                    if (config.showTabs != result) {
                        config.showTabs = result
                        activity.withPlayer { sendCommand(CustomCommands.RELOAD_CONTENT) }
                    }
                }
            },
        )

        PreferenceSwitch(
            title = stringResource(R.string.swap_prev_next),
            isChecked = swapPrevNext,
            onClick = {
                swapPrevNext = !swapPrevNext
                config.swapPrevNext = swapPrevNext
            },
        )

        PreferenceItem(
            title = stringResource(R.string.accent),
            description = stringResource(config.accent.name),
            onClick = {
                val items = ArrayList((0 until Accent.MAX).map { index ->
                    RadioItem(index, activity.getString(Accent.from(index).name))
                })

                RadioGroupDialog(activity, items, config.accent.index) { chosen ->
                    config.accent = Accent.from(chosen as Int)
                    // Compose screens follow the new accent immediately through this; native
                    // chrome still needs the theme rebuilt, hence recreate() below. See
                    // org.fossify.musicplayer.theme.AppearanceState.
                    context.syncAppearanceState(config.accent.index, config.themeMode.toDarkMode(), config.useBlackTheme)
                    // An accent is a theme, and a theme is only read at inflation - so the screen
                    // has to be built again for it to take. This is what Auxio does, and it is the
                    // same thing AppCompat does for night mode.
                    activity.recreate()
                }
            },
        )

        PreferenceItem(
            title = stringResource(R.string.theme),
            description = stringResource(themeModeLabel(themeMode)),
            onClick = {
                val items = arrayListOf(
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                    AppCompatDelegate.MODE_NIGHT_NO,
                    AppCompatDelegate.MODE_NIGHT_YES,
                ).map { mode -> RadioItem(mode, activity.getString(themeModeLabel(mode))) }

                RadioGroupDialog(activity, ArrayList(items), themeMode) { chosen ->
                    themeMode = chosen as Int
                    config.themeMode = themeMode
                    context.syncAppearanceState(config.accent.index, themeMode.toDarkMode(), config.useBlackTheme)
                    // AppCompat re-creates every started activity itself in response, so the
                    // change lands across the app rather than only on this screen.
                    AppCompatDelegate.setDefaultNightMode(themeMode)
                }
            },
        )

        PreferenceSwitch(
            title = stringResource(R.string.black_theme),
            isChecked = useBlackTheme,
            onClick = {
                useBlackTheme = !useBlackTheme
                config.useBlackTheme = useBlackTheme
                context.syncAppearanceState(config.accent.index, config.themeMode.toDarkMode(), useBlackTheme)
                // Only visible while dark, so only worth rebuilding then.
                if (context.isNight) {
                    activity.recreate()
                }
            },
        )

        PreferenceItem(
            title = stringResource(R.string.show_filename),
            description = stringResource(replaceTitleLabel(showFilename)),
            onClick = {
                val items = arrayListOf(
                    RadioItem(SHOW_FILENAME_NEVER, activity.getString(org.fossify.commons.R.string.never)),
                    RadioItem(SHOW_FILENAME_IF_UNAVAILABLE, activity.getString(R.string.title_is_not_available)),
                    RadioItem(SHOW_FILENAME_ALWAYS, activity.getString(org.fossify.commons.R.string.always)),
                )

                RadioGroupDialog(activity, items, showFilename) { chosen ->
                    showFilename = chosen as Int
                    config.showFilename = showFilename
                    activity.refreshQueueAndTracks()
                }
            },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        PreferenceSubtitle(text = stringResource(R.string.check_for_updates))

        PreferenceItem(
            title = stringResource(R.string.check_for_updates),
            description = updateStatus,
            onClick = {
                updateStatus = activity.getString(R.string.checking_for_updates)
                UpdateChecker.checkAsync { update ->
                    if (update != null) {
                        updateStatus = ""
                        AppUpdateDialog(activity, update)
                    } else {
                        updateStatus = activity.getString(R.string.no_updates_available)
                    }
                }
            },
        )

        PreferenceSwitch(
            title = stringResource(R.string.auto_check_for_updates),
            isChecked = checkForUpdates,
            onClick = {
                checkForUpdates = !checkForUpdates
                config.checkForUpdates = checkForUpdates
            },
        )
    }
    }
}

private fun themeModeLabel(mode: Int) = when (mode) {
    AppCompatDelegate.MODE_NIGHT_NO -> R.string.theme_mode_light
    AppCompatDelegate.MODE_NIGHT_YES -> R.string.theme_mode_dark
    else -> R.string.theme_mode_auto
}

private fun replaceTitleLabel(showFilename: Int) = when (showFilename) {
    SHOW_FILENAME_NEVER -> org.fossify.commons.R.string.never
    SHOW_FILENAME_IF_UNAVAILABLE -> R.string.title_is_not_available
    else -> org.fossify.commons.R.string.always
}
