package org.fossify.musicplayer.extensions

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import com.google.android.material.R as MR
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.MediaStore.Audio
import android.util.Size
import androidx.core.net.toUri
import androidx.media3.common.Player
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
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

/**
 * Resolve a theme colour attribute, ported from Auxio's getAttrColorCompat.
 *
 * The theme is the single source of colour in this app, so code that has to paint something the
 * layout could not asks the theme for it rather than deriving one. Everything else should use
 * ?attr/... directly in XML.
 */
@ColorInt
fun Context.getAttrColor(@AttrRes attr: Int): Int {
    val resolved = TypedValue()
    theme.resolveAttribute(attr, resolved, true)
    return if (resolved.resourceId != 0) getColor(resolved.resourceId) else resolved.data
}

/**
 * The accent: the one colour carrying the app's identity.
 *
 * colorPrimary is declared by AppCompat rather than by Material, which is why this one attribute
 * is qualified differently from the surface roles below.
 */
val Context.accentColor: Int
    get() = getAttrColor(androidx.appcompat.R.attr.colorPrimary)

/**
 * The surface hierarchy the playback UI is built on: browsing content at the bottom, and each sheet
 * stacked over it one step nearer the viewer. These are Material's own container ladder, so the
 * steps hold their relationship on every accent and in both modes.
 */
fun Context.getContentSurfaceColor() = getAttrColor(MR.attr.colorSurface)

fun Context.getPlaybackSurfaceColor() = getAttrColor(MR.attr.colorSurfaceContainer)

fun Context.getQueueSurfaceColor() = getAttrColor(MR.attr.colorSurfaceContainerHigh)

/** What the app bar lifts to once the list beneath it has been scrolled. */
fun Context.getLiftedSurfaceColor() = getAttrColor(MR.attr.colorSurfaceContainerLow)

/** What a queue entry lifts to while it is being dragged. */
fun Context.getLiftedQueueSurfaceColor() = getAttrColor(MR.attr.colorSurfaceContainerHighest)

/**
 * Body text and icons.
 *
 * Auxio colours text from ?android:attr/textColorPrimary rather than an M3 role, because the
 * Material 3 parent already maps that onto colorOnSurface - which is why its text follows a
 * dynamic palette with no wiring at all.
 */
fun Context.getTintedTextColor(): Int = getAttrColor(android.R.attr.textColorPrimary)

/**
 * Whether the device is one Android itself considers memory constrained.
 *
 * Used to decide whether theme changes animate by default: the transition repaints every themed
 * view in the tree on each frame, which is exactly the kind of work these devices should not be
 * doing for decoration.
 */
fun Context.isLowRamDevice(): Boolean =
    (getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice

/**
 * Whether the app is currently being drawn dark.
 *
 * Read from the configuration the framework actually applied rather than from the stored
 * preference, so it stays correct under "follow system" and cannot disagree with the resources
 * already inflated around it. Ported from Auxio.
 */
val Context.isNight: Boolean
    get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
