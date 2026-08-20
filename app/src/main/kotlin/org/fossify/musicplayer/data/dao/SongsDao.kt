package org.fossify.musicplayer.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.musicplayer.models.Track

@Dao
interface SongsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(track: Track)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(tracks: List<Track>)

    @Query("SELECT * FROM tracks")
    fun getAll(): List<Track>

    @Query("SELECT * FROM tracks WHERE playlist_id = :playlistId")
    fun getTracksFromPlaylist(playlistId: Int): List<Track>

    // Rows are stamped as they join the playlist, so the newest stamp is the latest addition. The
    // id breaks ties between tracks added within the same second.
    @Query("SELECT * FROM tracks WHERE playlist_id = :playlistId ORDER BY date_added_to_playlist DESC, id DESC LIMIT 1")
    fun getLatestTrackAddedToPlaylist(playlistId: Int): Track?

    @Query("SELECT * FROM tracks WHERE artist_id = :artistId")
    fun getTracksFromArtist(artistId: Long): List<Track>

    @Query("SELECT * FROM tracks WHERE album_id = :albumId")
    fun getTracksFromAlbum(albumId: Long): List<Track>

    @Query("SELECT COUNT(*) FROM tracks WHERE playlist_id = :playlistId")
    fun getTracksCountFromPlaylist(playlistId: Int): Int

    @Query("SELECT EXISTS(SELECT 1 FROM tracks WHERE media_store_id = :mediaStoreId AND playlist_id = :playlistId)")
    fun isTrackInPlaylist(mediaStoreId: Long, playlistId: Int): Boolean

    @Query("SELECT * FROM tracks WHERE folder_name = :folderName COLLATE NOCASE GROUP BY media_store_id")
    fun getTracksFromFolder(folderName: String): List<Track>

    @Query("SELECT * FROM tracks WHERE media_store_id = :mediaStoreId")
    fun getTrackWithMediaStoreId(mediaStoreId: Long): Track?

    @Query("SELECT * FROM tracks WHERE genre_id = :genreId")
    fun getGenreTracks(genreId: Long): List<Track>

    @Query("DELETE FROM tracks WHERE media_store_id = :mediaStoreId")
    fun removeTrack(mediaStoreId: Long)

    @Query("DELETE FROM tracks WHERE playlist_id = :playlistId")
    fun removePlaylistSongs(playlistId: Int)

    @Query("DELETE FROM tracks WHERE playlist_id = :playlistId AND media_store_id IN (:mediaStoreIds)")
    fun removeTracksFromPlaylist(playlistId: Int, mediaStoreIds: List<Long>)

    @Query("UPDATE tracks SET path = :newPath, artist = :artist, title = :title WHERE path = :oldPath")
    fun updateSongInfo(newPath: String, artist: String, title: String, oldPath: String)

    @Query("UPDATE tracks SET cover_art = :coverArt WHERE media_store_id = :id")
    fun updateCoverArt(coverArt: String, id: Long)

    @Query("UPDATE tracks SET order_in_playlist = :index WHERE id = :id")
    fun updateOrderInPlaylist(index: Int, id: Long)

    /**
     * Copy every mutable field from each track's canonical (playlist_id = 0) row, freshly written
     * by the last scan, onto its copies sitting in every other playlist.
     *
     * A track is duplicated into one row per playlist it belongs to, so a rescan that only touches
     * the canonical row - a retag, a rename, new artwork - would otherwise leave Favorites,
     * History, Most Played, "All tracks", and user playlists showing what the file used to be.
     */
    @Query(
        """
        UPDATE tracks SET
            title = (SELECT c.title FROM tracks c WHERE c.media_store_id = tracks.media_store_id AND c.playlist_id = 0),
            artist = (SELECT c.artist FROM tracks c WHERE c.media_store_id = tracks.media_store_id AND c.playlist_id = 0),
            album = (SELECT c.album FROM tracks c WHERE c.media_store_id = tracks.media_store_id AND c.playlist_id = 0),
            path = (SELECT c.path FROM tracks c WHERE c.media_store_id = tracks.media_store_id AND c.playlist_id = 0),
            duration = (SELECT c.duration FROM tracks c WHERE c.media_store_id = tracks.media_store_id AND c.playlist_id = 0),
            cover_art = (SELECT c.cover_art FROM tracks c WHERE c.media_store_id = tracks.media_store_id AND c.playlist_id = 0),
            genre = (SELECT c.genre FROM tracks c WHERE c.media_store_id = tracks.media_store_id AND c.playlist_id = 0),
            genre_id = (SELECT c.genre_id FROM tracks c WHERE c.media_store_id = tracks.media_store_id AND c.playlist_id = 0),
            disc_number = (SELECT c.disc_number FROM tracks c WHERE c.media_store_id = tracks.media_store_id AND c.playlist_id = 0),
            year = (SELECT c.year FROM tracks c WHERE c.media_store_id = tracks.media_store_id AND c.playlist_id = 0),
            folder_name = (SELECT c.folder_name FROM tracks c WHERE c.media_store_id = tracks.media_store_id AND c.playlist_id = 0),
            album_id = (SELECT c.album_id FROM tracks c WHERE c.media_store_id = tracks.media_store_id AND c.playlist_id = 0),
            artist_id = (SELECT c.artist_id FROM tracks c WHERE c.media_store_id = tracks.media_store_id AND c.playlist_id = 0)
        WHERE playlist_id != 0
          AND EXISTS (SELECT 1 FROM tracks c WHERE c.media_store_id = tracks.media_store_id AND c.playlist_id = 0)
        """
    )
    fun syncMetadataFromCanonicalTracks()
}
