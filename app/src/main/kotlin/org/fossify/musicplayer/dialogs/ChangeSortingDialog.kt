package org.fossify.musicplayer.dialogs

import android.app.Activity
import android.view.ViewGroup
import android.widget.RadioGroup
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.SORT_DESCENDING
import org.fossify.commons.models.RadioItem
import org.fossify.musicplayer.R
import org.fossify.musicplayer.databinding.DialogChangeSortingBinding
import org.fossify.musicplayer.databinding.SmallRadioButtonBinding
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.helpers.*
import org.fossify.musicplayer.models.Playlist

class ChangeSortingDialog(val activity: Activity, val location: Int, val playlist: Playlist? = null, val path: String? = null, val callback: () -> Unit) {
    private val config = activity.config
    private var currSorting = 0
    private val binding by activity.viewBinding(DialogChangeSortingBinding::inflate)

    init {
        binding.apply {
            useForThisPlaylistDivider.beVisibleIf(playlist != null || path != null)
            sortingDialogUseForThisOnly.beVisibleIf(playlist != null || path != null)

            // 'All tracks' holds the whole library, so the time a track joined it is the time its
            // file turned up: there are no two readings of "date added" to choose between.
            sortingDialogUsePlaylistDateAdded.beVisibleIf(playlist != null && !playlist.isAllTracks)

            if (playlist != null) {
                sortingDialogUseForThisOnly.isChecked = config.hasCustomPlaylistSorting(playlist.id)
            } else if (path != null) {
                sortingDialogUseForThisOnly.text = activity.getString(org.fossify.commons.R.string.use_for_this_folder)
                sortingDialogUseForThisOnly.isChecked = config.hasCustomSorting(path)
            }
        }

        currSorting = when (location) {
            TAB_PLAYLISTS -> config.playlistSorting
            TAB_FOLDERS -> config.folderSorting
            TAB_ARTISTS -> config.artistSorting
            TAB_ALBUMS -> config.albumSorting
            TAB_TRACKS -> config.trackSorting
            TAB_GENRES -> config.genreSorting
            else -> if (playlist != null) {
                config.getProperPlaylistSorting(playlist.id)
            } else if (path != null) {
                config.getProperFolderSorting(path)
            } else {
                config.trackSorting
            }
        }

        binding.sortingDialogUsePlaylistDateAdded.isChecked = currSorting and PLAYER_SORT_USE_PLAYLIST_DATE_ADDED != 0

        setupSortRadio()
        setupOrderRadio()

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> dialogConfirmed() }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, org.fossify.commons.R.string.sort_by)
            }
    }

    private fun setupSortRadio() {
        val radioItems = ArrayList<RadioItem>()
        when (location) {
            TAB_PLAYLISTS, TAB_FOLDERS -> {
                radioItems.add(RadioItem(0, activity.getString(org.fossify.commons.R.string.title), PLAYER_SORT_BY_TITLE))
                radioItems.add(RadioItem(1, activity.getString(R.string.track_count), PLAYER_SORT_BY_TRACK_COUNT))
            }

            TAB_ARTISTS -> {
                radioItems.add(RadioItem(0, activity.getString(org.fossify.commons.R.string.title), PLAYER_SORT_BY_TITLE))
                radioItems.add(RadioItem(1, activity.getString(R.string.album_count), PLAYER_SORT_BY_ALBUM_COUNT))
                radioItems.add(RadioItem(2, activity.getString(R.string.track_count), PLAYER_SORT_BY_TRACK_COUNT))
            }

            TAB_ALBUMS -> {
                radioItems.add(RadioItem(0, activity.getString(org.fossify.commons.R.string.title), PLAYER_SORT_BY_TITLE))
                radioItems.add(RadioItem(1, activity.getString(R.string.artist_name), PLAYER_SORT_BY_ARTIST_TITLE))
                radioItems.add(RadioItem(2, activity.getString(R.string.year), PLAYER_SORT_BY_YEAR))
                radioItems.add(RadioItem(4, activity.getString(org.fossify.commons.R.string.date_added), PLAYER_SORT_BY_DATE_ADDED))
            }

            TAB_TRACKS -> {
                radioItems.add(RadioItem(0, activity.getString(org.fossify.commons.R.string.title), PLAYER_SORT_BY_TITLE))
                radioItems.add(RadioItem(1, activity.getString(org.fossify.commons.R.string.artist), PLAYER_SORT_BY_ARTIST_TITLE))
                radioItems.add(RadioItem(2, activity.getString(org.fossify.commons.R.string.duration), PLAYER_SORT_BY_DURATION))
                radioItems.add(RadioItem(3, activity.getString(R.string.track_number), PLAYER_SORT_BY_TRACK_ID))
                radioItems.add(RadioItem(4, activity.getString(org.fossify.commons.R.string.date_added), PLAYER_SORT_BY_DATE_ADDED))
            }

            TAB_GENRES -> {
                radioItems.add(RadioItem(0, activity.getString(org.fossify.commons.R.string.title), PLAYER_SORT_BY_TITLE))
                radioItems.add(RadioItem(2, activity.getString(R.string.track_count), PLAYER_SORT_BY_TRACK_COUNT))
            }

            ACTIVITY_PLAYLIST_FOLDER -> {
                radioItems.add(RadioItem(0, activity.getString(org.fossify.commons.R.string.title), PLAYER_SORT_BY_TITLE))
                radioItems.add(RadioItem(1, activity.getString(org.fossify.commons.R.string.artist), PLAYER_SORT_BY_ARTIST_TITLE))
                radioItems.add(RadioItem(2, activity.getString(org.fossify.commons.R.string.duration), PLAYER_SORT_BY_DURATION))
                radioItems.add(RadioItem(3, activity.getString(R.string.track_number), PLAYER_SORT_BY_TRACK_ID))
                radioItems.add(RadioItem(4, activity.getString(org.fossify.commons.R.string.date_added), PLAYER_SORT_BY_DATE_ADDED))

                if (playlist != null) {
                    radioItems.add(RadioItem(4, activity.getString(org.fossify.commons.R.string.custom), PLAYER_SORT_BY_CUSTOM))
                    radioItems.add(RadioItem(5, activity.getString(R.string.most_played), PLAYER_SORT_BY_PLAY_COUNT))
                }
            }
        }

        binding.sortingDialogRadioSorting.setOnCheckedChangeListener { _, checkedId ->
            binding.sortingOrderDivider.beVisibleIf(checkedId != PLAYER_SORT_BY_CUSTOM)
            binding.sortingDialogRadioOrder.beVisibleIf(checkedId != PLAYER_SORT_BY_CUSTOM)
            // Which of the two dates to sort by only means anything while sorting by one of them.
            binding.sortingDialogUsePlaylistDateAdded.isEnabled = checkedId == PLAYER_SORT_BY_DATE_ADDED
        }

        radioItems.forEach { radioItem ->
            SmallRadioButtonBinding.inflate(activity.layoutInflater).apply {
                smallRadioButton.apply {
                    text = radioItem.title
                    isChecked = currSorting and (radioItem.value as Int) != 0
                    id = radioItem.value as Int
                }

                binding.sortingDialogRadioSorting.addView(
                    smallRadioButton,
                    RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                )
            }
        }
    }

    private fun setupOrderRadio() {
        var orderBtn = binding.sortingDialogRadioAscending

        if (currSorting and SORT_DESCENDING != 0) {
            orderBtn = binding.sortingDialogRadioDescending
        }

        orderBtn.isChecked = true
    }

    private fun dialogConfirmed() {
        val sortingRadio = binding.sortingDialogRadioSorting
        var sorting = sortingRadio.checkedRadioButtonId

        if (binding.sortingDialogRadioOrder.checkedRadioButtonId == R.id.sorting_dialog_radio_descending) {
            sorting = sorting or SORT_DESCENDING
        }

        if (binding.sortingDialogUsePlaylistDateAdded.isChecked) {
            sorting = sorting or PLAYER_SORT_USE_PLAYLIST_DATE_ADDED
        }

        if (currSorting != sorting || location == ACTIVITY_PLAYLIST_FOLDER) {
            when (location) {
                TAB_PLAYLISTS -> config.playlistSorting = sorting
                TAB_FOLDERS -> config.folderSorting = sorting
                TAB_ARTISTS -> config.artistSorting = sorting
                TAB_ALBUMS -> config.albumSorting = sorting
                TAB_TRACKS -> config.trackSorting = sorting
                TAB_GENRES -> config.genreSorting = sorting
                ACTIVITY_PLAYLIST_FOLDER -> {
                    if (binding.sortingDialogUseForThisOnly.isChecked) {
                        if (playlist != null) {
                            config.saveCustomPlaylistSorting(playlist.id, sorting)
                        } else if (path != null) {
                            config.saveCustomSorting(path, sorting)
                        }
                    } else {
                        if (playlist != null) {
                            config.removeCustomPlaylistSorting(playlist.id)
                            config.playlistTracksSorting = sorting
                        } else if (path != null) {
                            config.removeCustomSorting(path)
                            config.playlistTracksSorting = sorting
                        } else {
                            config.trackSorting = sorting
                        }
                    }
                }
            }

            callback()
        }
    }
}
