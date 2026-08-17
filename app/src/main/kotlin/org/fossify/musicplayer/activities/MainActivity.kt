package org.fossify.musicplayer.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Bundle
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import org.fossify.musicplayer.BuildConfig
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.FAQItem
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.Release
import org.fossify.musicplayer.R
import org.fossify.musicplayer.adapters.ViewPagerAdapter
import org.fossify.musicplayer.databinding.ActivityMainBinding
import org.fossify.musicplayer.dialogs.SleepTimerCustomDialog
import org.fossify.musicplayer.extensions.*
import org.fossify.musicplayer.helpers.*
import org.fossify.musicplayer.models.Events
import org.fossify.musicplayer.models.Playlist
import org.fossify.musicplayer.playback.CustomCommands
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : SimpleMusicActivity() {
    private var bus: EventBus? = null
    private var storedShowTabs = 0
    private var storedExcludedFolders = 0

    override var isSearchBarEnabled = true

    private val binding by viewBinding(ActivityMainBinding::inflate)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        refreshMenuItems()
        // Each tab's list pads itself from the insets the playback sheet hands down, which already
        // carry the navigation bar and the keyboard, so the column around them must stay unpadded
        // or the two would stack. Only the sleep timer strip, which floats over the lists rather
        // than scrolling with them, still has to be moved clear of the navigation bar.
        setupEdgeToEdge(moveBottomSystem = listOf(binding.sleepTimerHolder))
        storeStateVariables()
        setupTabs()
        setupLibraryShortcuts()
        setupPlaybackSheet()

        handlePermission(getPermissionToRequest()) {
            if (it) {
                initActivity()
            } else {
                toast(org.fossify.commons.R.string.no_storage_permissions)
                finish()
            }
        }

        volumeControlStream = AudioManager.STREAM_MUSIC
        checkWhatsNewDialog()
        checkAppOnSDCard()
    }

    override fun onResume() {
        super.onResume()
        handleNotificationIntent(intent)
        handleViewIntent(intent)
        if (storedShowTabs != config.showTabs) {
            System.exit(0)
            return
        }

        updateMenuColors()
        updateTextColors(binding.mainHolder)
        setupTabColors()
        setupLibraryShortcutColors()
        updateFavoritesShortcutCover()
        val properTextColor = getTintedTextColor()
        val properPrimaryColor = getProperPrimaryColor()
        binding.sleepTimerHolder.background = ColorDrawable(getContentSurfaceColor())
        binding.sleepTimerStop.applyColorFilter(properTextColor)
        binding.loadingProgressBar.setIndicatorColor(properPrimaryColor)
        binding.loadingProgressBar.trackColor = properPrimaryColor.adjustAlpha(LOWER_ALPHA)

        getAllFragments().forEach {
            it.setupColors(properTextColor, properPrimaryColor)
        }


        if (storedExcludedFolders != config.excludedFolders.hashCode()) {
            refreshAllFragments()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    /**
     * The tabs behave like navigation destinations: back closes the search first, then returns to
     * Tracks from anywhere else, and only leaves the app once Tracks itself is showing.
     */
    override fun onBackPressedCompat(): Boolean {
        return when {
            binding.mainMenu.isSearchOpen -> {
                binding.mainMenu.closeSearch()
                true
            }

            binding.viewPager.currentItem != HOME_TAB_POSITION -> {
                binding.viewPager.currentItem = HOME_TAB_POSITION
                true
            }

            else -> false
        }
    }

    private fun refreshMenuItems() {
        binding.mainMenu.requireToolbar().menu.apply {
            findItem(R.id.more_apps_from_us).isVisible = !resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.requireToolbar().inflateMenu(R.menu.menu_main)
        binding.mainMenu.toggleHideOnScroll(false)
        binding.mainMenu.setupMenu()

        binding.mainMenu.onSearchClosedListener = {
            getAllFragments().forEach {
                it.onSearchClosed()
            }
        }

        binding.mainMenu.onSearchTextChangedListener = { text ->
            getCurrentFragment()?.onSearchQueryChanged(text)
        }

        binding.mainMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort -> showSortingDialog()
                R.id.rescan_media -> refreshAllFragments(showProgress = true)
                R.id.sleep_timer -> showSleepTimer()
                R.id.equalizer -> launchEqualizer()
                R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun updateMenuColors() {
        binding.mainMenu.updateColors()
        // Commons paints the app bar from the plain background, so re-apply the content surface
        // afterwards to keep the bar, the tabs and the list behind them on one continuous colour.
        binding.mainMenu.setBackgroundColor(getContentSurfaceColor())
    }

    private fun storeStateVariables() {
        config.apply {
            storedShowTabs = showTabs
            storedExcludedFolders = config.excludedFolders.hashCode()
        }
    }

    private fun initActivity() {
        bus = EventBus.getDefault()
        bus!!.register(this)
        // trigger a scan first so that the fragments will accurately reflect the scanning state
        mediaScanner.scan()
        initFragments()
        binding.sleepTimerStop.setOnClickListener { stopSleepTimer() }

        refreshAllFragments()
    }

    private fun refreshAllFragments(showProgress: Boolean = config.appRunCount == 1) {
        if (showProgress) {
            binding.loadingProgressBar.show()
        }

        handleNotificationPermission { granted ->
            mediaScanner.scan(progress = showProgress && granted) { complete ->
                runOnUiThread {
                    getAllFragments().forEach {
                        it.setupFragment(this)
                    }

                    if (complete) {
                        binding.loadingProgressBar.hide()
                        withPlayer {
                            if (currentMediaItem == null) {
                                maybePreparePlayer()
                            } else {
                                sendCommand(CustomCommands.RELOAD_CONTENT)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun initFragments() {
        binding.viewPager.adapter = ViewPagerAdapter(this)
        binding.viewPager.offscreenPageLimit = tabsList.size - 1
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                // Only follow the pager when the pager led, which means a swipe. A tab tap has
                // already moved the selection, and selecting it again would come back as a
                // reselection and send the list it just opened to the top.
                if (binding.mainTabsHolder.selectedTabPosition != position) {
                    binding.mainTabsHolder.getTabAt(position)?.select()
                }

                getAllFragments().forEach {
                    it.finishActMode()
                }
            }
        })
    }

    private fun setupTabs() {
        binding.mainTabsHolder.removeAllTabs()
        getVisibleTabs().forEach { value ->
            binding.mainTabsHolder.newTab().apply {
                text = getTabLabel(value)
                binding.mainTabsHolder.addTab(this)
            }
        }

        binding.mainTabsHolder.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                binding.viewPager.currentItem = tab.position
                binding.viewPager.post {
                    getAdapter()?.getFragmentAt(tab.position)?.onSearchQueryChanged(
                        text = binding.mainMenu.getCurrentQuery()
                    )
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            // Tapping the tab you are already on returns its list to the top, as it does in every
            // other tabbed app.
            override fun onTabReselected(tab: TabLayout.Tab) {
                getAdapter()?.getFragmentAt(tab.position)?.scrollToTop()
            }
        })

        binding.mainTabsHolder.beGoneIf(binding.mainTabsHolder.tabCount == 1)
    }

    /** The fixed entry points above the tabs. */
    private fun setupLibraryShortcuts() {
        binding.libraryShortcuts.shortcutFavorites.setOnClickListener {
            openManagedPlaylist(FAVORITES_PLAYLIST_ID, org.fossify.commons.R.string.favorites)
        }

        binding.libraryShortcuts.shortcutRecent.setOnClickListener {
            openManagedPlaylist(HISTORY_PLAYLIST_ID, R.string.recent)
        }

        binding.libraryShortcuts.shortcutPlaylists.setOnClickListener {
            hideKeyboard()
            startActivity(Intent(applicationContext, PlaylistsActivity::class.java))
        }
    }

    /** Playlists the app maintains are built in, so their id and title need no database read. */
    private fun openManagedPlaylist(playlistId: Int, titleRes: Int) {
        hideKeyboard()
        val playlist = Playlist(playlistId, getString(titleRes))
        startActivity(
            Intent(applicationContext, TracksActivity::class.java)
                .putExtra(PLAYLIST, Gson().toJson(playlist))
        )
    }

    /**
     * Tint the shortcut row from the app's accent.
     *
     * The tonal style asks for M3 container roles, but Fossify only ever supplies a single accent
     * and leaves the rest of the palette at the framework defaults, so the roles have to be filled
     * in here or the cards resolve to unrelated colours. The container is handed over as a colour
     * rather than a flat tint, since a card backed by artwork dims its own to a scrim.
     */
    private fun setupLibraryShortcutColors() = binding.libraryShortcuts.apply {
        val containerColor = getProperBackgroundColor()
        val iconTint = ColorStateList.valueOf(getProperPrimaryColor())
        val labelColor = getTintedTextColor()

        listOf(shortcutFavorites, shortcutPlaylists, shortcutRecent).forEach {
            it.setContainerColor(containerColor)
            it.iconTint = iconTint
            it.setTextColor(labelColor)
        }
    }

    /** Back the favorites card with the cover of the track added to it most recently. */
    private fun updateFavoritesShortcutCover() {
        ensureBackgroundThread {
            val latestFavorite = audioHelper.getLatestTrackAddedToPlaylist(FAVORITES_PLAYLIST_ID)
            getTrackCoverArt(latestFavorite) { coverArt ->
                binding.libraryShortcuts.shortcutFavorites.bind(coverArt)
            }
        }
    }

    private fun setupTabColors() {
        val properTextColor = getTintedTextColor()
        val properPrimaryColor = getProperPrimaryColor()
        binding.mainTabsHolder.setTabTextColors(properTextColor.adjustAlpha(MEDIUM_ALPHA), properPrimaryColor)
        binding.mainTabsHolder.setSelectedTabIndicatorColor(properPrimaryColor)
    }

    private fun getTabLabel(position: Int): String {
        val stringId = when (position) {
            TAB_FOLDERS -> R.string.folders
            TAB_ARTISTS -> R.string.artists
            TAB_ALBUMS -> R.string.albums
            TAB_GENRES -> R.string.genres
            else -> R.string.tracks
        }

        return resources.getString(stringId)
    }

    private fun showSortingDialog() {
        getCurrentFragment()?.onSortOpen(this)
    }

    private fun showSleepTimer() {
        val minutes = getString(org.fossify.commons.R.string.minutes_raw)
        val hour = resources.getQuantityString(org.fossify.commons.R.plurals.hours, 1, 1)

        val items = arrayListOf(
            RadioItem(5 * 60, "5 $minutes"),
            RadioItem(10 * 60, "10 $minutes"),
            RadioItem(20 * 60, "20 $minutes"),
            RadioItem(30 * 60, "30 $minutes"),
            RadioItem(60 * 60, hour)
        )

        if (items.none { it.id == config.lastSleepTimerSeconds }) {
            val lastSleepTimerMinutes = config.lastSleepTimerSeconds / 60
            val text = resources.getQuantityString(org.fossify.commons.R.plurals.minutes, lastSleepTimerMinutes, lastSleepTimerMinutes)
            items.add(RadioItem(config.lastSleepTimerSeconds, text))
        }

        items.sortBy { it.id }
        items.add(RadioItem(-1, getString(org.fossify.commons.R.string.custom)))

        RadioGroupDialog(this, items, config.lastSleepTimerSeconds) {
            if (it as Int == -1) {
                SleepTimerCustomDialog(this) {
                    if (it > 0) {
                        pickedSleepTimer(it)
                    }
                }
            } else if (it > 0) {
                pickedSleepTimer(it)
            }
        }
    }

    private fun pickedSleepTimer(seconds: Int) {
        config.lastSleepTimerSeconds = seconds
        config.sleepInTS = System.currentTimeMillis() + seconds * 1000
        startSleepTimer()
    }

    private fun startSleepTimer() {
        binding.sleepTimerHolder.fadeIn()
        withPlayer {
            sendCommand(CustomCommands.TOGGLE_SLEEP_TIMER)
        }
    }

    private fun stopSleepTimer() {
        binding.sleepTimerHolder.fadeOut()
        withPlayer {
            sendCommand(CustomCommands.TOGGLE_SLEEP_TIMER)
        }
    }

    override fun onPlayingTrackChanged(trackId: Long, isPlaying: Boolean) {
        getAllFragments().forEach { it.onPlayingTrackChanged(trackId, isPlaying) }
    }

    private fun getAdapter() = binding.viewPager.adapter as? ViewPagerAdapter

    private fun getAllFragments() = getAdapter()?.getAllFragments().orEmpty()

    private fun getCurrentFragment() = getAdapter()?.getCurrentFragment()

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun sleepTimerChanged(event: Events.SleepTimerChanged) {
        binding.sleepTimerValue.text = event.seconds.getFormattedDuration()
        binding.sleepTimerHolder.beVisible()

        if (event.seconds == 0) {
            finish()
        }
    }

    /** Favoriting from anywhere — the playback panel included — repaints the card as it happens. */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun playlistsUpdated(event: Events.PlaylistsUpdated) {
        updateFavoritesShortcutCover()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun tracksUpdated(event: Events.RefreshTracks) {
        getAdapter()?.getTracksFragment()?.setupFragment(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun shouldRefreshFragments(event: Events.RefreshFragments) {
        refreshAllFragments()
    }

    private fun launchEqualizer() {
        hideKeyboard()
        startActivity(Intent(applicationContext, EqualizerActivity::class.java))
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_EVENT_BUS or LICENSE_GLIDE or LICENSE_M3U_PARSER or LICENSE_AUTOFITTEXTVIEW

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text),
            FAQItem(org.fossify.commons.R.string.faq_1_title_commons, org.fossify.commons.R.string.faq_1_text_commons),
            FAQItem(org.fossify.commons.R.string.faq_4_title_commons, org.fossify.commons.R.string.faq_4_text_commons),
            FAQItem(org.fossify.commons.R.string.faq_9_title_commons, org.fossify.commons.R.string.faq_9_text_commons)
        )

        if (!resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(org.fossify.commons.R.string.faq_2_title_commons, org.fossify.commons.R.string.faq_2_text_commons))
            faqItems.add(FAQItem(org.fossify.commons.R.string.faq_6_title_commons, org.fossify.commons.R.string.faq_6_text_commons))
        }

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }

    /** Play a track handed to us by another app, then reveal it in the panel. */
    private fun handleViewIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) {
            return
        }

        val uri = intent.data ?: return
        intent.data = null
        getTrackFromUri(uri) { track ->
            runOnUiThread {
                if (track != null) {
                    prepareAndPlay(listOf(track))
                } else {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
            }
        }
    }

    private fun handleNotificationIntent(intent: Intent) {
        val shouldOpenPlayer = intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)

        if (shouldOpenPlayer) {
            intent.removeExtra(EXTRA_OPEN_PLAYER)
            tryOpenPlaybackPanel()
        }
    }

    private companion object {
        /** The leading tab, which [tabsList] puts Tracks in. Back unwinds to it from anywhere else. */
        const val HOME_TAB_POSITION = 0
    }
}
