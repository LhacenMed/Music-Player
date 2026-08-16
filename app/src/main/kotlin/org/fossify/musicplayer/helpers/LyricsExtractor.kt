package org.fossify.musicplayer.helpers

import org.fossify.musicplayer.models.Lyrics
import org.fossify.musicplayer.models.Track
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.KeyNotFoundException
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagTextField
import java.io.File

/**
 * Reads the lyrics of a single track straight from its audio file.
 *
 * Lyrics are deliberately kept out of the indexing pipeline. They are large, only ever needed for
 * whatever is playing right now, and caching them for an entire library would cost far more memory
 * than re-reading a few kilobytes of tags on each track change.
 */
object LyricsExtractor {
    /**
     * Raw tag ids that [FieldKey.LYRICS] does not map to, in descending order of how likely they
     * are to be canonical for their format. ID3v2 dedicates SYLT to timed lyrics, Xiph has no
     * standard field so all three conventions are checked, and MP4 keeps iTunes-style freeform
     * lyrics outside the ©lyr atom.
     */
    private val RAW_LYRIC_FIELD_IDS = listOf(
        "SYLT",
        "TXXX:LYRICS",
        "TXXX:UNSYNCEDLYRICS",
        "SYNCEDLYRICS",
        "UNSYNCEDLYRICS",
        "----:COM.APPLE.ITUNES:LYRICS"
    )

    /** Read the lyrics embedded in [track]'s audio file, or null if it has none. */
    fun extract(track: Track): Lyrics? {
        val file = File(track.path)
        if (!file.canRead()) {
            return null
        }

        return try {
            val tag = AudioFileIO.read(file).tag ?: return null
            LyricsParser.parse(tag.collectLyricFields())
        } catch (e: Exception) {
            // A missing or unreadable file must degrade to "no lyrics", never crash the playback
            // UI that is waiting on this.
            null
        }
    }

    private fun Tag.collectLyricFields(): List<String> = buildList {
        // Portable accessor, which maps to USLT / LYRICS / ©lyr depending on the format.
        try {
            addAll(getAll(FieldKey.LYRICS))
        } catch (e: KeyNotFoundException) {
            // This format has no mapped lyrics field; the raw ids below still apply.
        }

        for (id in RAW_LYRIC_FIELD_IDS) {
            getFields(id).filterIsInstance<TagTextField>().mapTo(this) { it.content }
        }
    }
}
