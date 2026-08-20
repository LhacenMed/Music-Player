package org.fossify.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.R
import org.fossify.musicplayer.adapters.AlbumsTracksAdapter
import org.fossify.musicplayer.adapters.BaseMusicAdapter
import org.fossify.musicplayer.databinding.ActivityAlbumsBinding
import org.fossify.musicplayer.extensions.accentColor
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.helpers.ALBUM
import org.fossify.musicplayer.helpers.ARTIST
import org.fossify.musicplayer.models.*

// Artists -> Albums -> Tracks
class AlbumsActivity : SimpleMusicActivity() {

    private val binding by viewBinding(ActivityAlbumsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // The list pads itself from the insets the playback sheet hands down, which already carry
        // the navigation bar, so nothing here may pad it a second time.
        setupEdgeToEdge()
        setupMaterialScrollListener(binding.albumsList, binding.albumsAppbar)

        binding.albumsFastscroller.updateColors(accentColor)

        val artistType = object : TypeToken<Artist>() {}.type
        val artist = Gson().fromJson<Artist>(intent.getStringExtra(ARTIST), artistType)
        binding.albumsToolbar.title = artist.title

        ensureBackgroundThread {
            val albums = audioHelper.getArtistAlbums(artist.id)
            val listItems = ArrayList<ListItem>()
            val albumsSectionLabel = resources.getQuantityString(R.plurals.albums_plural, albums.size, albums.size)
            listItems.add(AlbumSection(albumsSectionLabel))
            listItems.addAll(albums)

            val albumTracks = audioHelper.getAlbumTracks(albums)
            val trackFullDuration = albumTracks.sumOf { it.duration }

            var tracksSectionLabel = resources.getQuantityString(R.plurals.tracks_plural, albumTracks.size, albumTracks.size)
            tracksSectionLabel += " • ${trackFullDuration.getFormattedDuration(true)}"
            listItems.add(AlbumSection(tracksSectionLabel))
            listItems.addAll(albumTracks)

            runOnUiThread {
                AlbumsTracksAdapter(this, listItems, binding.albumsList) {
                    hideKeyboard()
                    if (it is Album) {
                        Intent(this, TracksActivity::class.java).apply {
                            putExtra(ALBUM, Gson().toJson(it))
                            startActivity(this)
                        }
                    } else {
                        handleNotificationPermission { granted ->
                            if (granted) {
                                val startIndex = albumTracks.indexOf(it as Track)
                                playTrack(albumTracks, startIndex)
                            } else {
                                PermissionRequiredDialog(
                                    this,
                                    org.fossify.commons.R.string.allow_notifications_music_player,
                                    { openNotificationSettings() }
                                )
                            }
                        }
                    }
                }.apply {
                    binding.albumsList.adapter = this
                }

                if (areSystemAnimationsEnabled) {
                    binding.albumsList.scheduleLayoutAnimation()
                }
            }
        }

        setupPlaybackSheet()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.albumsAppbar, NavigationIcon.Arrow)
    }
    override fun onPlayingTrackChanged(trackId: Long, isPlaying: Boolean) {
        (binding.albumsList.adapter as? BaseMusicAdapter<*>)?.setPlayingTrack(trackId, isPlaying)
    }

}
