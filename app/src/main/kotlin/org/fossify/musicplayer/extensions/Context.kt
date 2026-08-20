package org.fossify.musicplayer.extensions

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.MediaStore.Audio
import android.util.Size
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.media3.common.Player
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.google.android.material.color.MaterialColors
import com.google.android.material.color.utilities.Hct
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isQPlus
import org.fossify.musicplayer.data.AudioHelper
import org.fossify.musicplayer.data.MediaScanner
import org.fossify.musicplayer.data.MusicDatabase
import org.fossify.musicplayer.data.RoomHelper
import org.fossify.musicplayer.data.dao.*
import org.fossify.musicplayer.helpers.*
import org.fossify.musicplayer.models.Album
import org.fossify.musicplayer.models.Artist
import org.fossify.musicplayer.models.Genre
import org.fossify.musicplayer.models.Track
import org.fossify.musicplayer.playback.PlaybackService
import org.fossify.musicplayer.widget.MyWidgetProvider
import java.io.File

val Context.config: Config get() = Config.newInstance(applicationContext)

val Context.playlistDAO: PlaylistsDao get() = getTracksDB().PlaylistsDao()

val Context.tracksDAO: SongsDao get() = getTracksDB().SongsDao()

val Context.queueDAO: QueueItemsDao get() = getTracksDB().QueueItemsDao()

val Context.artistDAO: ArtistsDao get() = getTracksDB().ArtistsDao()

val Context.albumsDAO: AlbumsDao get() = getTracksDB().AlbumsDao()

val Context.genresDAO: GenresDao get() = getTracksDB().GenresDao()

val Context.playStatsDAO: PlayStatsDao get() = getTracksDB().PlayStatsDao()

val Context.audioHelper: AudioHelper get() = AudioHelper(this)

val Context.mediaScanner: MediaScanner get() = MediaScanner.getInstance(applicationContext as Application)

fun Context.getTracksDB() = MusicDatabase.getInstance(this)

fun Context.getPlaylistIdWithTitle(title: String) = playlistDAO.getPlaylistWithTitle(title)?.id ?: -1

fun Context.broadcastUpdateWidgetState() {
    Intent(this, MyWidgetProvider::class.java).apply {
        action = TRACK_STATE_CHANGED
        sendBroadcast(this)
    }
}

fun Context.getMediaStoreIdFromPath(path: String): Long {
    var id = 0L
    val projection = arrayOf(
        Audio.Media._ID
    )

    val uri = getFileUri(path)
    val selection = "${MediaStore.MediaColumns.DATA} = ?"
    val selectionArgs = arrayOf(path)

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                id = cursor.getLongValue(Audio.Media._ID)
            }
        }
    } catch (ignored: Exception) {
    }

    return id
}

fun Context.getFolderTracks(path: String, rescanWrongPaths: Boolean, callback: (tracks: ArrayList<Track>) -> Unit) {
    val folderTracks = getFolderTrackPaths(File(path))
    val allTracks = audioHelper.getAllTracks()
    val wantedTracks = ArrayList<Track>()
    val wrongPaths = ArrayList<String>()    // rescan paths that are not present in the MediaStore

    folderTracks.forEach { trackPath ->
        var trackAdded = false
        val mediaStoreId = getMediaStoreIdFromPath(trackPath)
        if (mediaStoreId != 0L) {
            allTracks.firstOrNull { it.mediaStoreId == mediaStoreId }?.apply {
                id = 0
                wantedTracks.add(this)
                trackAdded = true
            }
        }

        if (!trackAdded) {
            val track = RoomHelper(this).getTrackFromPath(trackPath)
            if (track != null && track.mediaStoreId != 0L) {
                wantedTracks.add(track)
            } else {
                wrongPaths.add(trackPath)
            }
        }
    }

    if (wrongPaths.isEmpty() || !rescanWrongPaths) {
        callback(wantedTracks)
    } else {
        rescanPaths(wrongPaths) {
            getFolderTracks(path, false) { tracks ->
                callback(tracks)
            }
        }
    }
}

private fun getFolderTrackPaths(folder: File): ArrayList<String> {
    val trackFiles = ArrayList<String>()
    val files = folder.listFiles() ?: return trackFiles
    files.forEach {
        if (it.isDirectory) {
            trackFiles.addAll(getFolderTrackPaths(it))
        } else if (it.isAudioFast()) {
            trackFiles.add(it.absolutePath)
        }
    }
    return trackFiles
}

fun Context.getArtistCoverArt(artist: Artist, callback: (coverArt: Any?) -> Unit) {
    ensureBackgroundThread {
        if (artist.albumArt.isEmpty()) {
            val track = audioHelper.getArtistTracks(artist.id).firstOrNull()
            getTrackCoverArt(track, callback)
        } else {
            Handler(Looper.getMainLooper()).post {
                callback(artist.albumArt)
            }
        }
    }
}

fun Context.getAlbumCoverArt(album: Album, callback: (coverArt: Any?) -> Unit) {
    ensureBackgroundThread {
        if (album.coverArt.isEmpty()) {
            val track = audioHelper.getAlbumTracks(album.id).firstOrNull()
            getTrackCoverArt(track, callback)
        } else {
            Handler(Looper.getMainLooper()).post {
                callback(album.coverArt)
            }
        }
    }
}

fun Context.getGenreCoverArt(genre: Genre, callback: (coverArt: Any?) -> Unit) {
    ensureBackgroundThread {
        if (genre.albumArt.isEmpty()) {
            val track = audioHelper.getGenreTracks(genre.id).firstOrNull()
            getTrackCoverArt(track, callback)
        } else {
            Handler(Looper.getMainLooper()).post {
                callback(genre.albumArt)
            }
        }
    }
}

fun Context.getTrackCoverArt(track: Track?, callback: (coverArt: Any?) -> Unit) {
    ensureBackgroundThread {
        if (track == null) {
            Handler(Looper.getMainLooper()).post {
                callback(null)
            }
            return@ensureBackgroundThread
        }

        val coverArt = track.coverArt.ifEmpty {
            loadTrackCoverArt(track)
        }

        Handler(Looper.getMainLooper()).post {
            callback(coverArt)
        }
    }
}

fun Context.loadTrackCoverArt(track: Track?): Bitmap? {
    if (track == null) {
        return null
    }

    val artworkUri = track.coverArt
    if (artworkUri.startsWith("content://")) {
        try {
            return MediaStore.Images.Media.getBitmap(contentResolver, artworkUri.toUri())
        } catch (ignored: Exception) {
        }
    }

    if (isQPlus()) {
        val coverArtHeight = resources.getCoverArtHeight()
        val size = Size(coverArtHeight, coverArtHeight)
        if (artworkUri.startsWith("content://")) {
            try {
                return contentResolver.loadThumbnail(artworkUri.toUri(), size, null)
            } catch (ignored: Exception) {
            }
        }

        val path = track.path
        if (path.isNotEmpty() && File(path).exists()) {
            try {
                return ThumbnailUtils.createAudioThumbnail(File(track.path), size, null)
            } catch (ignored: OutOfMemoryError) {
            } catch (ignored: Exception) {
            }
        }
    }

    return null
}

fun Context.loadGlideResource(
    model: Any?,
    options: RequestOptions,
    size: Size,
    onLoadFailed: (e: Exception?) -> Unit,
    onResourceReady: (resource: Drawable) -> Unit,
) {
    ensureBackgroundThread {
        try {
            Glide.with(this)
                .load(model)
                .apply(options)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                        onLoadFailed(e)
                        return true
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        onResourceReady(resource)
                        return false
                    }
                })
                .submit(size.width, size.height)
                .get()
        } catch (e: Exception) {
            onLoadFailed(e)
        }
    }
}

fun Context.getTrackFromUri(uri: Uri?, callback: (track: Track?) -> Unit) {
    if (uri == null) {
        callback(null)
        return
    }

    ensureBackgroundThread {
        val path = getRealPathFromURI(uri)
        if (path == null) {
            callback(null)
            return@ensureBackgroundThread
        }

        val allTracks = audioHelper.getAllTracks()
        val track = allTracks.find { it.path == path } ?: RoomHelper(this).getTrackFromPath(path) ?: return@ensureBackgroundThread
        callback(track)
    }
}

fun Context.isTabVisible(flag: Int) = config.showTabs and flag != 0

fun Context.getVisibleTabs() = tabsList.filter { isTabVisible(it) }

/** The name a tab carries, read by the pager adapter so the tab layout can label itself. */
fun Context.getTabLabel(tab: Int): String = getString(
    when (tab) {
        TAB_FOLDERS -> org.fossify.musicplayer.R.string.folders
        TAB_ARTISTS -> org.fossify.musicplayer.R.string.artists
        TAB_ALBUMS -> org.fossify.musicplayer.R.string.albums
        TAB_GENRES -> org.fossify.musicplayer.R.string.genres
        else -> org.fossify.musicplayer.R.string.tracks
    }
)

fun Context.getPlaybackSetting(repeatMode: @Player.RepeatMode Int): PlaybackSetting {
    return when (repeatMode) {
        Player.REPEAT_MODE_OFF -> PlaybackSetting.REPEAT_OFF
        Player.REPEAT_MODE_ONE -> PlaybackSetting.REPEAT_TRACK
        Player.REPEAT_MODE_ALL -> PlaybackSetting.REPEAT_PLAYLIST
        else -> config.playbackSetting
    }
}

fun Context.getFriendlyFolder(path: String): String {
    return when (val parentPath = path.getParentPath()) {
        internalStoragePath -> getString(org.fossify.commons.R.string.internal)
        sdCardPath -> getString(org.fossify.commons.R.string.sd_card)
        else -> parentPath.getFilenameFromPath()
    }
}

/** How far the browsing surface sits below the playback sheet, in percent. */
private const val CONTENT_SURFACE_DARKEN_PERCENT = 4

/** How far the queue sheet sits above the playback sheet it is stacked on, in percent. */
private const val QUEUE_SURFACE_LIGHTEN_PERCENT = 4

/**
 * The app's accent color: the playing track's own cover color when
 * [org.fossify.musicplayer.helpers.Config.adaptiveTrackTheme] is on and one has been read for it,
 * otherwise the color chosen in Customize Colors.
 *
 * Everywhere in this app that would reach for [getProperPrimaryColor] to color something the user
 * looks at while browsing or playing music - buttons, tinted text and icons, selection state -
 * reads it through here instead, which is the one place that decides whether it is adaptive right
 * now. [getProperPrimaryColor] itself is still what this falls back to, and is still the right call
 * for anything Fossify Commons colors on its own (e.g. a switch's thumb): that stays tied to the
 * configured accent regardless, since Commons has no notion of an adaptive one.
 */
val Context.accentColor: Int
    get() = PlaybackService.currentAccentColor ?: getProperPrimaryColor()

/** How far the app bar lifts towards the foreground once the surface under it is scrolled. */
private const val LIFTED_SURFACE_BLEND = 0.08f

/**
 * The surface hierarchy the playback UI is built on, mirroring Auxio's: browsing content sits at
 * the bottom, and each sheet stacked over it steps one level lighter so the layers stay legible
 * against each other.
 */
fun Context.getContentSurfaceColor() = getProperBackgroundColor().darkenColor(CONTENT_SURFACE_DARKEN_PERCENT)

fun Context.getPlaybackSurfaceColor() = getProperBackgroundColor()

fun Context.getQueueSurfaceColor() = getProperBackgroundColor().lightenColor(QUEUE_SURFACE_LIGHTEN_PERCENT)

/**
 * The colour the app bar lifts to once the list running beneath it has been scrolled.
 *
 * Blended towards the text colour rather than stepped in lightness, because lightenColor and
 * darkenColor both hand pure black and pure white straight back, and a step of the size the sheets
 * use is barely visible at the dark end of the scale anyway. Moving towards the foreground lifts the
 * bar on every theme, and in the direction Material does it: lighter on a dark palette, darker on a
 * light one.
 */
fun Context.getLiftedSurfaceColor() =
    ColorUtils.blendARGB(getContentSurfaceColor(), getProperTextColor(), LIFTED_SURFACE_BLEND)

/**
 * The colour a queue entry lifts to while it is being dragged. This is the entry's own pressed
 * colour, so picking one up simply holds the shade that touching it already showed rather than
 * introducing a third one.
 */
fun Context.getLiftedQueueSurfaceColor() = ColorUtils.compositeColors(
    MaterialColors.getColor(this, android.R.attr.colorControlHighlight, Color.TRANSPARENT),
    getQueueSurfaceColor()
)

/**
 * How far the accent's hue and chroma bleed into text and icon colour. Kept low enough that the
 * shift reads as warmth rather than a colour change.
 */
private const val TEXT_TINT_CHROMA = 6.0

/**
 * HCT tone is only in-gamut as pure white/black at the very extremes, so a colour sitting at tone
 * 100 or 0 has no room left for [TEXT_TINT_CHROMA] to show. Clamping away from those extremes
 * keeps the tint visible even when the configured text colour is a flat white or black.
 */
private const val MIN_TINTED_TONE = 4.0
private const val MAX_TINTED_TONE = 96.0

/**
 * [getProperTextColor] leaned towards the current accent's hue, the way none of Auxio's generated
 * Material You palettes ever leave "white" perfectly neutral. Used for text and icons alike, since
 * this app colours both from the same value.
 */
fun Context.getTintedTextColor(): Int {
    val tone = Hct.fromInt(getProperTextColor()).tone.coerceIn(MIN_TINTED_TONE, MAX_TINTED_TONE)
    val accentHue = Hct.fromInt(accentColor).hue
    return Hct.from(accentHue, TEXT_TINT_CHROMA, tone).toInt()
}
