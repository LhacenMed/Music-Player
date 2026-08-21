package org.fossify.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.IS_CUSTOMIZING_COLORS
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.isTiramisuPlus
import org.fossify.commons.models.RadioItem
import org.fossify.musicplayer.R
import org.fossify.musicplayer.databinding.ActivitySettingsBinding
import org.fossify.musicplayer.dialogs.AppUpdateDialog
import org.fossify.musicplayer.dialogs.ManageVisibleTabsDialog
import org.fossify.musicplayer.extensions.accentColor
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.sendCommand
import org.fossify.musicplayer.helpers.SHOW_FILENAME_ALWAYS
import org.fossify.musicplayer.helpers.SHOW_FILENAME_IF_UNAVAILABLE
import org.fossify.musicplayer.helpers.SHOW_FILENAME_NEVER
import org.fossify.musicplayer.playback.CustomCommands
import org.fossify.musicplayer.update.UpdateChecker
import java.util.Locale
import kotlin.system.exitProcess

class SettingsActivity : SimpleControllerActivity() {

    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomSystem = listOf(binding.settingsNestedScrollview))
        setupMaterialScrollListener(binding.settingsNestedScrollview, binding.settingsAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.settingsAppbar, NavigationIcon.Arrow)

        setupCustomizeColors()
        setupCustomizeWidgetColors()
        setupUseEnglish()
        setupLanguage()
        setupManageExcludedFolders()
        setupManageShownTabs()
        setupSwapPrevNext()
        setupAdaptiveTrackTheme()
        setupReplaceTitle()
        setupUpdates()
        updateTextColors(binding.settingsNestedScrollview)

        arrayOf(binding.settingsColorCustomizationSectionLabel, binding.settingsGeneralSettingsLabel, binding.settingsUpdatesLabel).forEach {
            it.setTextColor(accentColor)
        }
    }

    private fun setupCustomizeColors() = binding.apply {
        settingsColorCustomizationHolder.setOnClickListener {
            startCustomizationActivity()
        }
    }

    private fun setupCustomizeWidgetColors() {
        binding.settingsWidgetColorCustomizationHolder.setOnClickListener {
            Intent(this, WidgetConfigureActivity::class.java).apply {
                putExtra(IS_CUSTOMIZING_COLORS, true)
                startActivity(this)
            }
        }
    }

    private fun setupUseEnglish() = binding.apply {
        settingsUseEnglishHolder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settingsUseEnglish.isChecked = config.useEnglish
        settingsUseEnglishHolder.setOnClickListener {
            settingsUseEnglish.toggle()
            config.useEnglish = settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() = binding.apply {
        settingsLanguage.text = Locale.getDefault().displayLanguage
        settingsLanguageHolder.beVisibleIf(isTiramisuPlus())
        settingsLanguageHolder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun setupSwapPrevNext() = binding.apply {
        settingsSwapPrevNext.isChecked = config.swapPrevNext
        settingsSwapPrevNextHolder.setOnClickListener {
            settingsSwapPrevNext.toggle()
            config.swapPrevNext = settingsSwapPrevNext.isChecked
        }
    }

    private fun setupAdaptiveTrackTheme() = binding.apply {
        settingsAdaptiveTrackTheme.isChecked = config.adaptiveTrackTheme
        settingsAdaptiveTrackThemeHolder.setOnClickListener {
            settingsAdaptiveTrackTheme.toggle()
            config.adaptiveTrackTheme = settingsAdaptiveTrackTheme.isChecked
            withPlayer { sendCommand(CustomCommands.REFRESH_ACCENT_COLOR) }
        }
    }

    private fun setupReplaceTitle() = binding.apply {
        settingsShowFilename.text = getReplaceTitleText()
        settingsShowFilenameHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(SHOW_FILENAME_NEVER, getString(org.fossify.commons.R.string.never)),
                RadioItem(SHOW_FILENAME_IF_UNAVAILABLE, getString(R.string.title_is_not_available)),
                RadioItem(SHOW_FILENAME_ALWAYS, getString(org.fossify.commons.R.string.always))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.showFilename) {
                config.showFilename = it as Int
                settingsShowFilename.text = getReplaceTitleText()
                refreshQueueAndTracks()
            }
        }
    }

    private fun getReplaceTitleText() = getString(
        when (config.showFilename) {
            SHOW_FILENAME_NEVER -> org.fossify.commons.R.string.never
            SHOW_FILENAME_IF_UNAVAILABLE -> R.string.title_is_not_available
            else -> org.fossify.commons.R.string.always
        }
    )

    private fun setupManageShownTabs() = binding.apply {
        settingsManageShownTabsHolder.setOnClickListener {
            ManageVisibleTabsDialog(this@SettingsActivity) { result ->
                val tabsMask = config.showTabs
                if (tabsMask != result) {
                    config.showTabs = result
                    withPlayer {
                        sendCommand(CustomCommands.RELOAD_CONTENT)
                    }
                }
            }
        }
    }

    private fun setupManageExcludedFolders() {
        binding.settingsManageExcludedFoldersHolder.setOnClickListener {
            startActivity(Intent(this, ExcludedFoldersActivity::class.java))
        }
    }

    private fun setupUpdates() = binding.apply {
        settingsAutoCheckForUpdates.isChecked = config.checkForUpdates
        settingsAutoCheckForUpdatesHolder.setOnClickListener {
            settingsAutoCheckForUpdates.toggle()
            config.checkForUpdates = settingsAutoCheckForUpdates.isChecked
        }

        settingsCheckForUpdatesHolder.setOnClickListener {
            settingsCheckForUpdatesStatus.text = getString(R.string.checking_for_updates)
            UpdateChecker.checkAsync { update ->
                if (update != null) {
                    settingsCheckForUpdatesStatus.text = ""
                    AppUpdateDialog(this@SettingsActivity, update)
                } else {
                    settingsCheckForUpdatesStatus.text = getString(R.string.no_updates_available)
                }
            }
        }
    }
}
