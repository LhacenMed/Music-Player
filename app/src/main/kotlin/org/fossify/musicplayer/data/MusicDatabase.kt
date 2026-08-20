package org.fossify.musicplayer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.fossify.musicplayer.R
import org.fossify.musicplayer.data.dao.*
import org.fossify.musicplayer.models.*
import java.util.concurrent.Executors

@Database(
    entities = [Track::class, Playlist::class, QueueItem::class, Artist::class, Album::class, Genre::class, PlayStats::class],
    version = 18
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun SongsDao(): SongsDao

    abstract fun PlaylistsDao(): PlaylistsDao

    abstract fun PlayStatsDao(): PlayStatsDao

    abstract fun QueueItemsDao(): QueueItemsDao

    abstract fun ArtistsDao(): ArtistsDao

    abstract fun AlbumsDao(): AlbumsDao

    abstract fun GenresDao(): GenresDao

    companion object {
        private var db: MusicDatabase? = null
        private val queryExecutor = Executors.newSingleThreadExecutor()

        fun getInstance(context: Context): MusicDatabase {
            if (db == null) {
                synchronized(MusicDatabase::class) {
                    if (db == null) {
                        val favoritesTitle = context.getString(org.fossify.commons.R.string.favorites)
                        val historyTitle = context.getString(R.string.recent)
                        val mostPlayedTitle = context.getString(R.string.most_played)
                        db = Room.databaseBuilder(context.applicationContext, MusicDatabase::class.java, "songs.db")
                            .setQueryExecutor(queryExecutor)
                            .addMigrations(MIGRATION_1_2)
                            .addMigrations(MIGRATION_2_3)
                            .addMigrations(MIGRATION_3_4)
                            .addMigrations(MIGRATION_4_5)
                            .addMigrations(MIGRATION_5_6)
                            .addMigrations(MIGRATION_6_7)
                            .addMigrations(MIGRATION_7_8)
                            .addMigrations(MIGRATION_8_9)
                            .addMigrations(MIGRATION_9_10)
                            .addMigrations(MIGRATION_10_11)
                            .addMigrations(MIGRATION_11_12)
                            .addMigrations(MIGRATION_12_13)
                            .addMigrations(MIGRATION_13_14)
                            .addMigrations(MIGRATION_14_15)
                            .addMigrations(migration15To16(favoritesTitle))
                            .addMigrations(migration16To17(historyTitle))
                            .addMigrations(migration17To18(mostPlayedTitle))
                            .addCallback(ManagedPlaylistsCallback(favoritesTitle, historyTitle, mostPlayedTitle))
                            .build()
                    }
                }
            }
            return db!!
        }

        fun destroyInstance() {
            db = null
        }
    }
}
