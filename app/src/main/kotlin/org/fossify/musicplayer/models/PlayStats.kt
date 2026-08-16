package org.fossify.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * How often a track has been listened to.
 *
 * Kept per track rather than per playlist row, so that a track carries the same count everywhere it
 * appears and every playlist can be sorted by it. The row is created by the first counted listen.
 */
@Entity(tableName = "play_stats")
data class PlayStats(
    @PrimaryKey @ColumnInfo(name = "media_store_id") var mediaStoreId: Long,
    @ColumnInfo(name = "play_count") var playCount: Int
)
