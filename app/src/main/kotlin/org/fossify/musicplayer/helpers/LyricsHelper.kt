package org.fossify.musicplayer.helpers

import org.fossify.musicplayer.models.Lyrics
import org.fossify.musicplayer.models.TimedLine
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

/**
 * Resolves [Lyrics] out of the raw lyric tags of an audio file.
 *
 * Taggers are wildly inconsistent about which field they put lyrics in, and whether the text in
 * that field is timed. Rather than maintaining a fragile field-to-format table, every candidate
 * field is simply run through the LRC parser: if timestamps come out, the lyrics are synced.
 * Synchronized lyrics always win over plain ones, regardless of field order.
 */
object LyricsParser {
    private const val NO_TIMESTAMP = -1L
    private const val MAX_MINUTE_DIGITS = 4

    /** Parse the first usable lyrics out of [rawFields], or null if none carry any. */
    fun parse(rawFields: List<String>): Lyrics? {
        var plain: Lyrics.Plain? = null
        for (raw in rawFields.filter { it.isNotBlank() }) {
            when (val lyrics = parseRaw(raw)) {
                is Lyrics.Synced -> return lyrics
                is Lyrics.Plain -> plain = plain ?: lyrics
                null -> continue
            }
        }

        return plain
    }

    /**
     * Parse one raw tag value, treating it as LRC if it yields any timestamps and as plain text
     * otherwise. Returns null if there is nothing displayable in it.
     */
    private fun parseRaw(raw: String): Lyrics? {
        val timed = mutableListOf<TimedLine>()
        val plain = mutableListOf<String>()

        for (line in raw.trim().lineSequence()) {
            // Consume the run of "[mm:ss.xx]" stamps that prefixes a timed line. A line may carry
            // several when a repeated lyric was de-duplicated by the tagger.
            var cursor = 0
            var stamps = 0
            while (cursor < line.length && line[cursor] == '[') {
                val close = line.indexOf(']', cursor + 1)
                if (close == -1) break
                val startMs = parseTimestamp(line, cursor + 1, close)
                if (startMs == NO_TIMESTAMP) break
                // The text isn't known until the stamps are exhausted, so backfill it below.
                timed.add(TimedLine(startMs, ""))
                stamps++
                cursor = close + 1
            }

            if (stamps == 0) {
                // Either genuinely plain lyrics or an LRC header like "[ar:Artist]". Headers are
                // harmless here, as any parse that finds timestamps discards this list.
                plain.add(line.trim())
                continue
            }

            val text = line.substring(cursor).trim()
            for (i in timed.size - stamps until timed.size) {
                timed[i] = timed[i].copy(text = text)
            }
        }

        return when {
            // sortBy is stable, so lines sharing a timestamp keep their authored order.
            timed.isNotEmpty() -> Lyrics.Synced(timed.apply { sortBy(TimedLine::startMs) })
            plain.any(String::isNotEmpty) -> Lyrics.Plain(plain)
            else -> null
        }
    }

    /**
     * Parse the LRC timestamp in `line[from, to)`, returning its position in milliseconds or
     * [NO_TIMESTAMP] if the span isn't a timestamp. Hand-rolled rather than a regex, as this runs
     * over every line of every lyric field encountered.
     */
    private fun parseTimestamp(line: String, from: Int, to: Int): Long {
        var cursor = from

        var minutes = 0L
        while (cursor < to && line[cursor].isAsciiDigit()) {
            // Bail on absurd values rather than letting a long digit run overflow.
            if (cursor - from == MAX_MINUTE_DIGITS) return NO_TIMESTAMP
            minutes = minutes * 10 + (line[cursor] - '0')
            cursor++
        }
        if (cursor == from || cursor == to || line[cursor] != ':') return NO_TIMESTAMP
        cursor++

        val secondsStart = cursor
        var seconds = 0L
        while (cursor < to && line[cursor].isAsciiDigit()) {
            seconds = seconds * 10 + (line[cursor] - '0')
            cursor++
        }
        if (cursor - secondsStart != 2 || seconds > 59) return NO_TIMESTAMP

        val wholeMs = minutes * 60_000L + seconds * 1000L
        // The fractional part is optional, as some taggers emit bare "[mm:ss]".
        if (cursor == to) return wholeMs
        if (line[cursor] != '.' && line[cursor] != ':') return NO_TIMESTAMP
        cursor++

        val fractionStart = cursor
        var fraction = 0L
        while (cursor < to && line[cursor].isAsciiDigit()) {
            fraction = fraction * 10 + (line[cursor] - '0')
            cursor++
        }
        if (cursor != to) return NO_TIMESTAMP

        return wholeMs + when (cursor - fractionStart) {
            2 -> fraction * 10 // Centiseconds, the LRC standard.
            3 -> fraction // Milliseconds, as written by some taggers.
            else -> return NO_TIMESTAMP
        }
    }

    /** Digit check that ignores the non-ASCII digits the stdlib accepts, which LRC never uses. */
    private fun Char.isAsciiDigit() = this in '0'..'9'
}
