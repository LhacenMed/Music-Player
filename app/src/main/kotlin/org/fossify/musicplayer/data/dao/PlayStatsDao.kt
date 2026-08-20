package org.fossify.musicplayer.data.dao

import androidx.room.Dao
import androidx.room.Query
import org.fossify.musicplayer.models.PlayStats

@Dao
interface PlayStatsDao {
    @Query("SELECT * FROM play_stats")
    fun getAll(): List<PlayStats>

    /** Count one more listen, starting the track's tally if this was its first. */
    @Query(
        "INSERT INTO play_stats (media_store_id, play_count) VALUES (:mediaStoreId, 1) " +
            "ON CONFLICT (media_store_id) DO UPDATE SET play_count = play_count + 1"
    )
    fun incrementPlayCount(mediaStoreId: Long)

    @Query("DELETE FROM play_stats WHERE media_store_id = :mediaStoreId")
    fun deletePlayStats(mediaStoreId: Long)
}
