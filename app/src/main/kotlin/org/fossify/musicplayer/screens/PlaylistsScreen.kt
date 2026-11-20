package org.fossify.musicplayer.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getTempFile
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.PERMISSION_READ_STORAGE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isQPlus
import org.fossify.musicplayer.R
import org.fossify.musicplayer.activities.SimpleMusicActivity
import org.fossify.musicplayer.activities.TracksActivity
import org.fossify.musicplayer.data.M3uImporter
import org.fossify.musicplayer.data.M3uImporter.ImportResult
import org.fossify.musicplayer.dialogs.ChangeSortingDialog
import org.fossify.musicplayer.dialogs.NewPlaylistDialog
import org.fossify.musicplayer.dialogs.RemovePlaylistDialog
import org.fossify.musicplayer.dialogs.SelectPlaylistDialog
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.getFolderTracks
import org.fossify.musicplayer.helpers.MIME_TYPE_M3U
import org.fossify.musicplayer.helpers.PLAYLIST
import org.fossify.musicplayer.helpers.TAB_PLAYLISTS
import org.fossify.musicplayer.models.Events
import org.fossify.musicplayer.models.Playlist
import org.fossify.musicplayer.models.sortSafely
import org.fossify.musicplayer.screens.components.BackNavigationIcon
import org.fossify.musicplayer.screens.components.LargeTopAppBar
import org.fossify.musicplayer.screens.components.SelectionTopBar
import org.fossify.musicplayer.screens.components.SmallTopAppBar
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.FileOutputStream

private class PlaylistsEventSubscriber(private val onUpdated: () -> Unit) {
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaylistsUpdated(event: Events.PlaylistsUpdated) = onUpdated()
}

private suspend fun loadPlaylists(context: Context): List<Playlist> = withContext(Dispatchers.IO) {
    val playlists = context.audioHelper.getAllPlaylists()
    playlists.forEach { it.trackCount = context.audioHelper.getPlaylistTrackCount(it.id) }
    playlists.sortSafely(context.config.playlistSorting)
    playlists
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen() {
    val context = LocalContext.current
    val activity = LocalActivity.current as SimpleMusicActivity

    var playlists by remember { mutableStateOf(emptyList<Playlist>()) }
    var reloadTick by remember { mutableStateOf(0) }
    var selectedIds by remember { mutableStateOf(emptySet<Int>()) }
    var overflowOpen by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val visiblePlaylists = if (searchActive && searchQuery.isNotEmpty()) {
        playlists.filter { it.title.contains(searchQuery, ignoreCase = true) }
    } else {
        playlists
    }

    LaunchedEffect(reloadTick) { playlists = loadPlaylists(context) }

    DisposableEffect(Unit) {
        val subscriber = PlaylistsEventSubscriber { reloadTick++ }
        EventBus.getDefault().register(subscriber)
        onDispose { EventBus.getDefault().unregister(subscriber) }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        when (uri.scheme) {
            "file" -> uri.path?.let { path ->
                SelectPlaylistDialog(activity) { id -> importPlaylist(activity, path, id) { reloadTick++ } }
            }

            "content" -> {
                val tempFile = context.getTempFile("imports", uri.path!!.getFilenameFromPath())
                if (tempFile == null) {
                    activity.toast(org.fossify.commons.R.string.unknown_error_occurred)
                } else {
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        FileOutputStream(tempFile).use { inputStream!!.copyTo(it) }
                        SelectPlaylistDialog(activity) { id ->
                            importPlaylist(activity, tempFile.absolutePath, id) { reloadTick++ }
                        }
                    } catch (e: Exception) {
                        activity.showErrorToast(e)
                    }
                }
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val selected = playlists.filter { it.id in selectedIds }
            if (selected.isNotEmpty()) {
                SelectionTopBar(
                    selectedCount = selected.size,
                    onClearSelection = { selectedIds = emptySet() },
                    actions = {
                        if (selected.size == 1 && selected.none { it.isManaged }) {
                            IconButton(onClick = {
                                NewPlaylistDialog(activity, selected.first()) {
                                    selectedIds = emptySet()
                                    reloadTick++
                                }
                            }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(org.fossify.commons.R.string.rename)) }
                        }
                        if (selected.none { it.isManaged }) {
                            IconButton(onClick = {
                                RemovePlaylistDialog(activity, null) { deleteFiles ->
                                    val toDelete = ArrayList(selected)
                                    ensureBackgroundThread {
                                        if (deleteFiles) {
                                            val tracksToDelete = toDelete.flatMap { context.audioHelper.getPlaylistTracks(it.id) }
                                            activity.deleteTracks(tracksToDelete) {
                                                context.audioHelper.deletePlaylists(toDelete)
                                                EventBus.getDefault().post(Events.PlaylistsUpdated())
                                            }
                                        } else {
                                            context.audioHelper.deletePlaylists(toDelete)
                                            EventBus.getDefault().post(Events.PlaylistsUpdated())
                                        }
                                    }
                                    selectedIds = emptySet()
                                }
                            }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(org.fossify.commons.R.string.delete)) }
                        }
                    },
                )
            } else if (searchActive) {
                SmallTopAppBar(
                    title = {
                        androidx.compose.material3.TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        )
                    },
                    navigationIcon = {
                        BackNavigationIcon(onBack = {
                            searchActive = false
                            searchQuery = ""
                        })
                    },
                )
            } else {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.playlists)) },
                    navigationIcon = { BackNavigationIcon(onBack = activity::finish) },
                    actions = {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(org.fossify.commons.R.string.search))
                        }
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(org.fossify.commons.R.string.sort_by)) },
                                onClick = {
                                    overflowOpen = false
                                    ChangeSortingDialog(activity, TAB_PLAYLISTS) { reloadTick++ }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.create_new_playlist)) },
                                onClick = {
                                    overflowOpen = false
                                    NewPlaylistDialog(activity) { EventBus.getDefault().post(Events.PlaylistsUpdated()) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.create_playlist_from_folder)) },
                                onClick = {
                                    overflowOpen = false
                                    FilePickerDialog(activity, pickFile = false, enforceStorageRestrictions = false) { path ->
                                        ensureBackgroundThread {
                                            context.getFolderTracks(path, true) { tracks ->
                                                activity.runOnUiThread {
                                                    NewPlaylistDialog(activity) { playlistId ->
                                                        ensureBackgroundThread {
                                                            context.audioHelper.addTracksToPlaylist(playlistId, tracks)
                                                            EventBus.getDefault().post(Events.PlaylistsUpdated())
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_playlist)) },
                                onClick = {
                                    overflowOpen = false
                                    tryImportPlaylist(activity, importLauncher)
                                },
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        if (visiblePlaylists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(org.fossify.commons.R.string.no_items_found))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(innerPadding)) {
                items(visiblePlaylists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        selected = playlist.id in selectedIds,
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                selectedIds = if (playlist.id in selectedIds) selectedIds - playlist.id else selectedIds + playlist.id
                            } else {
                                activity.hideKeyboard()
                                activity.startActivity(
                                    Intent(activity, TracksActivity::class.java).putExtra(PLAYLIST, Gson().toJson(playlist)),
                                )
                            }
                        },
                        onLongClick = { selectedIds = selectedIds + playlist.id },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistRow(playlist: Playlist, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(playlist.title, style = MaterialTheme.typography.titleMedium)
        Text(
            LocalContext.current.resources.getQuantityString(R.plurals.tracks_plural, playlist.trackCount, playlist.trackCount),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun tryImportPlaylist(
    activity: SimpleMusicActivity,
    launcher: androidx.activity.compose.ManagedActivityResultLauncher<Intent, ActivityResult>,
) {
    if (isQPlus()) {
        activity.hideKeyboard()
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = MIME_TYPE_M3U
        }
        try {
            launcher.launch(intent)
        } catch (e: Exception) {
            activity.showErrorToast(e)
        }
    } else {
        activity.handlePermission(PERMISSION_READ_STORAGE) { granted ->
            if (granted) {
                FilePickerDialog(activity, enforceStorageRestrictions = false) { path ->
                    SelectPlaylistDialog(activity) { id -> importPlaylist(activity, path, id) {} }
                }
            }
        }
    }
}

private fun importPlaylist(activity: SimpleMusicActivity, path: String, playlistId: Int, onDone: () -> Unit) {
    ensureBackgroundThread {
        M3uImporter(activity) { result ->
            activity.runOnUiThread {
                activity.toast(
                    when (result) {
                        ImportResult.IMPORT_OK -> org.fossify.commons.R.string.importing_successful
                        ImportResult.IMPORT_PARTIAL -> org.fossify.commons.R.string.importing_some_entries_failed
                        else -> org.fossify.commons.R.string.importing_failed
                    },
                )
                onDone()
            }
        }.importPlaylist(path, playlistId)
    }
}
