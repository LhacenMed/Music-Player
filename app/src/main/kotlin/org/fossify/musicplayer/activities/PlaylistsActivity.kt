package org.fossify.musicplayer.activities

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.PERMISSION_READ_STORAGE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isQPlus
import org.fossify.musicplayer.R
import org.fossify.musicplayer.databinding.ActivityPlaylistsBinding
import org.fossify.musicplayer.dialogs.NewPlaylistDialog
import org.fossify.musicplayer.dialogs.SelectPlaylistDialog
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.getFolderTracks
import org.fossify.musicplayer.helpers.M3uImporter
import org.fossify.musicplayer.helpers.M3uImporter.ImportResult
import org.fossify.musicplayer.helpers.MIME_TYPE_M3U
import org.fossify.musicplayer.models.Events
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.FileOutputStream

/** The screen the Playlists library shortcut opens, hosting the same list the tab used to show. */
class PlaylistsActivity : SimpleMusicActivity() {
    private val PICK_IMPORT_SOURCE_INTENT = 1

    private var isSearchOpen = false
    private var searchMenuItem: MenuItem? = null

    private val binding by viewBinding(ActivityPlaylistsBinding::inflate)

    private val playlistsFragment get() = binding.playlistsFragment.root

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()

        // The list pads itself from the insets the playback sheet hands down, which already carry
        // the navigation bar, so nothing here may pad it a second time.
        setupEdgeToEdge()
        setupMaterialScrollListener(binding.playlistsFragment.playlistsList, binding.playlistsAppbar)

        EventBus.getDefault().register(this)
        setupPlaybackSheet()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.playlistsAppbar, NavigationIcon.Arrow, searchMenuItem = searchMenuItem)
        playlistsFragment.setupColors(getProperTextColor(), getProperPrimaryColor())
        playlistsFragment.setupFragment(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    private fun setupOptionsMenu() {
        binding.playlistsToolbar.inflateMenu(R.menu.menu_playlists)
        setupSearch(binding.playlistsToolbar.menu)
        binding.playlistsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort -> playlistsFragment.onSortOpen(this)
                R.id.create_new_playlist -> createNewPlaylist()
                R.id.create_playlist_from_folder -> createPlaylistFromFolder()
                R.id.import_playlist -> tryImportPlaylist()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun setupSearch(menu: Menu) {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchMenuItem = menu.findItem(R.id.search)
        (searchMenuItem!!.actionView as SearchView).apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    if (isSearchOpen) {
                        playlistsFragment.onSearchQueryChanged(newText)
                    }
                    return true
                }
            })
        }

        MenuItemCompat.setOnActionExpandListener(searchMenuItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                isSearchOpen = true
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                playlistsFragment.onSearchClosed()
                isSearchOpen = false
                return true
            }
        })
    }

    private fun createNewPlaylist() {
        NewPlaylistDialog(this) {
            EventBus.getDefault().post(Events.PlaylistsUpdated())
        }
    }

    private fun createPlaylistFromFolder() {
        FilePickerDialog(this, pickFile = false, enforceStorageRestrictions = false) {
            createPlaylistFrom(it)
        }
    }

    private fun createPlaylistFrom(path: String) {
        ensureBackgroundThread {
            getFolderTracks(path, true) { tracks ->
                runOnUiThread {
                    NewPlaylistDialog(this) { playlistId ->
                        tracks.forEach {
                            it.playListId = playlistId
                        }

                        ensureBackgroundThread {
                            audioHelper.insertTracks(tracks)
                            EventBus.getDefault().post(Events.PlaylistsUpdated())
                        }
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == PICK_IMPORT_SOURCE_INTENT && resultCode == RESULT_OK && resultData?.data != null) {
            tryImportPlaylistFromFile(resultData.data!!)
        }
    }

    private fun tryImportPlaylistFromFile(uri: Uri) {
        when {
            uri.scheme == "file" -> showImportPlaylistDialog(uri.path!!)
            uri.scheme == "content" -> {
                val tempFile = getTempFile("imports", uri.path!!.getFilenameFromPath())
                if (tempFile == null) {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                    return
                }

                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val out = FileOutputStream(tempFile)
                    inputStream!!.copyTo(out)

                    showImportPlaylistDialog(tempFile.absolutePath)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }

            else -> toast(org.fossify.commons.R.string.invalid_file_format)
        }
    }

    private fun tryImportPlaylist() {
        if (isQPlus()) {
            hideKeyboard()
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = MIME_TYPE_M3U

                try {
                    startActivityForResult(this, PICK_IMPORT_SOURCE_INTENT)
                } catch (e: ActivityNotFoundException) {
                    toast(org.fossify.commons.R.string.system_service_disabled, Toast.LENGTH_LONG)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        } else {
            handlePermission(PERMISSION_READ_STORAGE) { granted ->
                if (granted) {
                    showFilePickerDialog()
                }
            }
        }
    }

    private fun showFilePickerDialog() {
        FilePickerDialog(this, enforceStorageRestrictions = false) { path ->
            SelectPlaylistDialog(this) { id ->
                importPlaylist(path, id)
            }
        }
    }

    private fun showImportPlaylistDialog(path: String) {
        SelectPlaylistDialog(this) { id ->
            importPlaylist(path, id)
        }
    }

    private fun importPlaylist(path: String, id: Int) {
        ensureBackgroundThread {
            M3uImporter(this) { result ->
                runOnUiThread {
                    toast(
                        when (result) {
                            ImportResult.IMPORT_OK -> org.fossify.commons.R.string.importing_successful
                            ImportResult.IMPORT_PARTIAL -> org.fossify.commons.R.string.importing_some_entries_failed
                            else -> org.fossify.commons.R.string.importing_failed
                        }
                    )

                    playlistsFragment.setupFragment(this)
                }
            }.importPlaylist(path, id)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun playlistsUpdated(event: Events.PlaylistsUpdated) {
        playlistsFragment.setupFragment(this)
    }
}
