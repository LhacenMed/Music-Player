package org.fossify.musicplayer.models

import androidx.room.*
import org.fossify.commons.helpers.AlphanumericComparator
import org.fossify.commons.helpers.SORT_DESCENDING
import org.fossify.musicplayer.extensions.sortSafely
import org.fossify.musicplayer.helpers.ALL_TRACKS_PLAYLIST_ID
import org.fossify.musicplayer.helpers.FAVORITES_PLAYLIST_ID
import org.fossify.musicplayer.helpers.HISTORY_PLAYLIST_ID
import org.fossify.musicplayer.helpers.MOST_PLAYED_PLAYLIST_ID
import org.fossify.musicplayer.helpers.PLAYER_SORT_BY_TITLE

@Entity(tableName = "playlists", indices = [(Index(value = ["id"], unique = true))])
data class Playlist(
    @PrimaryKey(autoGenerate = true) var id: Int,
    @ColumnInfo(name = "title") var title: String,

    @Ignore var trackCount: Int = 0
) {
    constructor() : this(0, "", 0)

    /** The playlist the scanner fills with everything it finds. */
    val isAllTracks get() = id == ALL_TRACKS_PLAYLIST_ID

    /** The playlist the favorite button fills. */
    val isFavorites get() = id == FAVORITES_PLAYLIST_ID

    /** The playlist playback itself fills, in the order tracks were last listened to. */
    val isHistory get() = id == HISTORY_PLAYLIST_ID

    /** The playlist playback itself fills, in the order tracks have been listened to most. */
    val isMostPlayed get() = id == MOST_PLAYED_PLAYLIST_ID

    /** Playlists the app keeps filled on its own, so the user may neither rename nor delete them. */
    val isManaged get() = isAllTracks || isFavorites || isHistory || isMostPlayed

    /**
     * Playlists the app orders itself, so their order is the one thing about them the user does not
     * choose: it is neither sorted from the toolbar nor rearranged by hand.
     */
    val hasFixedOrder get() = isHistory || isMostPlayed

    /** Whether the tracks in this playlist can be dragged into an order of the user's own. */
    val isReorderable get() = !hasFixedOrder

    companion object {
        fun getComparator(sorting: Int) = Comparator<Playlist> { first, second ->
            var result = when {
                sorting and PLAYER_SORT_BY_TITLE != 0 -> AlphanumericComparator().compare(first.title.lowercase(), second.title.lowercase())
                else -> first.trackCount.compareTo(second.trackCount)
            }

            if (sorting and SORT_DESCENDING != 0) {
                result *= -1
            }

            return@Comparator result
        }
    }

    fun getBubbleText(sorting: Int) = when {
        sorting and PLAYER_SORT_BY_TITLE != 0 -> title
        else -> trackCount.toString()
    }
}

fun ArrayList<Playlist>.sortSafely(sorting: Int) = sortSafely(Playlist.getComparator(sorting))
