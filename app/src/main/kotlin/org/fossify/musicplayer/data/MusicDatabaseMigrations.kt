package org.fossify.musicplayer.data

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.fossify.musicplayer.helpers.FAVORITES_PLAYLIST_ID
import org.fossify.musicplayer.helpers.HISTORY_PLAYLIST_ID
import org.fossify.musicplayer.helpers.MOST_PLAYED_PLAYLIST_ID

// removing the "type" value of Song
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL(
                "CREATE TABLE songs_new (media_store_id INTEGER NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, path TEXT NOT NULL, duration INTEGER NOT NULL, " +
                    "album TEXT NOT NULL, playlist_id INTEGER NOT NULL, PRIMARY KEY(path, playlist_id))"
            )

            execSQL(
                "INSERT INTO songs_new (media_store_id, title, artist, path, duration, album, playlist_id) " +
                    "SELECT media_store_id, title, artist, path, duration, album, playlist_id FROM songs"
            )

            execSQL("DROP TABLE songs")
            execSQL("ALTER TABLE songs_new RENAME TO songs")

            execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_playlists_id` ON `playlists` (`id`)")
        }
    }
}

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE songs ADD COLUMN track_id INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE songs ADD COLUMN cover_art TEXT default '' NOT NULL")
    }
}

internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE `queue_items` (`track_id` INTEGER NOT NULL PRIMARY KEY, `track_order` INTEGER NOT NULL, `is_current` INTEGER NOT NULL, `last_position` INTEGER NOT NULL)")
    }
}

// change the primary keys from path + playlist_id to media_store_id + playlist_id
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL(
                "CREATE TABLE songs_new (media_store_id INTEGER NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, path TEXT NOT NULL, duration INTEGER NOT NULL, " +
                    "album TEXT NOT NULL, cover_art TEXT default '' NOT NULL, playlist_id INTEGER NOT NULL, track_id INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(media_store_id, playlist_id))"
            )

            execSQL(
                "INSERT OR IGNORE INTO songs_new (media_store_id, title, artist, path, duration, album, cover_art, playlist_id, track_id) " +
                    "SELECT media_store_id, title, artist, path, duration, album, cover_art, playlist_id, track_id FROM songs"
            )

            execSQL("DROP TABLE songs")
            execSQL("ALTER TABLE songs_new RENAME TO tracks")
        }
    }
}

// adding an autoincrementing "id" field, replace primary keys with indices
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL(
                "CREATE TABLE tracks_new (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `media_store_id` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `path` TEXT NOT NULL, `duration` INTEGER NOT NULL, " +
                    "`album` TEXT NOT NULL, `cover_art` TEXT default '' NOT NULL, `playlist_id` INTEGER NOT NULL, `track_id` INTEGER NOT NULL DEFAULT 0)"
            )

            execSQL(
                "INSERT OR IGNORE INTO tracks_new (media_store_id, title, artist, path, duration, album, cover_art, playlist_id, track_id) " +
                    "SELECT media_store_id, title, artist, path, duration, album, cover_art, playlist_id, track_id FROM tracks"
            )

            execSQL("DROP TABLE tracks")
            execSQL("ALTER TABLE tracks_new RENAME TO tracks")

            execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tracks_id` ON `tracks` (`media_store_id`, `playlist_id`)")
        }
    }
}

internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("CREATE TABLE `artists` (`id` INTEGER NOT NULL PRIMARY KEY, `title` TEXT NOT NULL, `album_cnt` INTEGER NOT NULL, `track_cnt` INTEGER NOT NULL, `album_art_id` INTEGER NOT NULL)")
            execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_artists_id` ON `artists` (`id`)")

            execSQL("CREATE TABLE `albums` (`id` INTEGER NOT NULL PRIMARY KEY, `artist` TEXT NOT NULL, `title` TEXT NOT NULL, `cover_art` TEXT NOT NULL, `year` INTEGER NOT NULL)")
            execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_albums_id` ON `albums` (`id`)")
        }
    }
}

internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE tracks ADD COLUMN folder_name TEXT default '' NOT NULL")
    }
}

internal val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE albums ADD COLUMN track_cnt INTEGER NOT NULL DEFAULT 0")
    }
}

internal val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE albums ADD COLUMN artist_id INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE tracks ADD COLUMN album_id INTEGER NOT NULL DEFAULT 0")
    }
}

internal val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE tracks ADD COLUMN order_in_playlist INTEGER NOT NULL DEFAULT 0")
    }
}

internal val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("ALTER TABLE tracks ADD COLUMN artist_id INTEGER NOT NULL DEFAULT 0")
            execSQL("ALTER TABLE tracks ADD COLUMN year INTEGER NOT NULL DEFAULT 0")
            execSQL("ALTER TABLE tracks ADD COLUMN flags INTEGER NOT NULL DEFAULT 0")

            execSQL("CREATE TABLE `artists_new` (`id` INTEGER NOT NULL PRIMARY KEY, `title` TEXT NOT NULL, `album_cnt` INTEGER NOT NULL, `track_cnt` INTEGER NOT NULL, `album_art` TEXT NOT NULL)")
            execSQL("INSERT OR IGNORE INTO artists_new (id, title, album_cnt, track_cnt) SELECT id, title, album_cnt, track_cnt FROM artists")
            execSQL("DROP TABLE artists")
            execSQL("ALTER TABLE artists_new RENAME TO artists")
            execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_artists_id` ON `artists` (`id`)")

            database.execSQL("ALTER TABLE tracks ADD COLUMN date_added INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE albums ADD COLUMN date_added INTEGER NOT NULL DEFAULT 0")
        }
    }
}

internal val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("ALTER TABLE tracks ADD COLUMN genre TEXT NOT NULL DEFAULT ''")
            execSQL("ALTER TABLE tracks ADD COLUMN genre_id INTEGER NOT NULL DEFAULT 0")

            execSQL("CREATE TABLE `genres` (`id` INTEGER NOT NULL PRIMARY KEY, `title` TEXT NOT NULL, `track_cnt` INTEGER NOT NULL, `album_art` TEXT NOT NULL)")
            execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_genres_id` ON `genres` (`id`)")
        }
    }
}

internal val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("ALTER TABLE tracks ADD COLUMN disc_number INTEGER DEFAULT NULL")
        }
    }
}

internal val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL(
                "CREATE TABLE tracks_new (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `media_store_id` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `path` TEXT NOT NULL, `duration` INTEGER NOT NULL, " +
                    "`album` TEXT NOT NULL, genre TEXT NOT NULL DEFAULT '', `cover_art` TEXT default '' NOT NULL, `playlist_id` INTEGER NOT NULL, `track_id` INTEGER DEFAULT NULL, disc_number INTEGER DEFAULT NULL, " +
                    "folder_name TEXT default '' NOT NULL, album_id INTEGER NOT NULL DEFAULT 0, artist_id INTEGER NOT NULL DEFAULT 0, genre_id INTEGER NOT NULL DEFAULT 0, year INTEGER NOT NULL DEFAULT 0, date_added INTEGER NOT NULL DEFAULT 0, " +
                    "order_in_playlist INTEGER NOT NULL DEFAULT 0, flags INTEGER NOT NULL DEFAULT 0)"
            )
            execSQL(
                "INSERT INTO tracks_new(id,media_store_id,title,artist,path,duration,album,genre,cover_art,playlist_id,track_id,disc_number,folder_name,album_id,artist_id,genre_id,year,date_added,order_in_playlist,flags) " +
                    "SELECT id,media_store_id,title,artist,path,duration,album,genre,cover_art,playlist_id,track_id,disc_number,folder_name,album_id,artist_id,genre_id,year,date_added,order_in_playlist,flags " +
                    "FROM tracks"
            )
            execSQL("DROP TABLE tracks")
            execSQL("ALTER TABLE tracks_new RENAME to tracks")
            execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tracks_id` ON `tracks` (`media_store_id`, `playlist_id`)")
        }
    }
}

internal fun migration15To16(favoritesTitle: String) = object : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.reserveManagedPlaylist(FAVORITES_PLAYLIST_ID, favoritesTitle)
    }
}

/**
 * Give every playlist row the moment it joined its playlist, and open the tally that counts
 * how often each track is listened to.
 */
internal fun migration16To17(historyTitle: String) = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.apply {
            execSQL("ALTER TABLE tracks ADD COLUMN date_added_to_playlist INTEGER NOT NULL DEFAULT 0")
            // Rows that predate the column joined their playlist at a moment nobody recorded.
            // Seeding them with the file's own date keeps both readings of "date added"
            // agreeing on day one, and lets them part company from the next addition on.
            execSQL("UPDATE tracks SET date_added_to_playlist = date_added")

            execSQL("CREATE TABLE IF NOT EXISTS `play_stats` (`media_store_id` INTEGER NOT NULL, `play_count` INTEGER NOT NULL, PRIMARY KEY(`media_store_id`))")

            reserveManagedPlaylist(HISTORY_PLAYLIST_ID, historyTitle)
        }
    }
}

/** Claims the id the most played playlist reads from, alongside favorites and the history. */
internal fun migration17To18(mostPlayedTitle: String) = object : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.reserveManagedPlaylist(MOST_PLAYED_PLAYLIST_ID, mostPlayedTitle)
    }
}

/** Seeds the playlists the app maintains into a database being created from scratch. */
internal class ManagedPlaylistsCallback(
    private val favoritesTitle: String,
    private val historyTitle: String,
    private val mostPlayedTitle: String
) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.reserveManagedPlaylist(FAVORITES_PLAYLIST_ID, favoritesTitle)
        db.reserveManagedPlaylist(HISTORY_PLAYLIST_ID, historyTitle)
        db.reserveManagedPlaylist(MOST_PLAYED_PLAYLIST_ID, mostPlayedTitle)
    }
}

/**
 * Claim [playlistId] for a playlist that ships with the app, seeding it with the database
 * itself rather than with the scanner: it is there before anything reads a playlist.
 *
 * Playlist ids are handed out by SQLite, so on an install old enough to hold playlists the
 * user made themselves, the reserved id is already taken. That playlist is moved to a free
 * id, taking the track rows pointing at it along with it.
 */
private fun SupportSQLiteDatabase.reserveManagedPlaylist(playlistId: Int, title: String) {
    execSQL(
        "UPDATE tracks SET playlist_id = (SELECT MAX(id) + 1 FROM playlists) " +
            "WHERE playlist_id = $playlistId AND EXISTS (SELECT 1 FROM playlists WHERE id = $playlistId)"
    )
    execSQL("UPDATE playlists SET id = (SELECT MAX(id) + 1 FROM playlists) WHERE id = $playlistId")
    execSQL("INSERT OR REPLACE INTO playlists (id, title) VALUES ($playlistId, ?)", arrayOf(title))

    // Moving a row does not advance the autoincrement counter, so the id just vacated would
    // be handed out again to the next playlist the user creates.
    execSQL(
        "UPDATE sqlite_sequence SET seq = (SELECT MAX(id) FROM playlists) " +
            "WHERE name = 'playlists' AND seq < (SELECT MAX(id) FROM playlists)"
    )
}
