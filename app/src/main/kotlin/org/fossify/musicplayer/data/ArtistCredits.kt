package org.fossify.musicplayer.data

/** Separators taggers use to list more than one performer in a single artist credit. */
private val ARTIST_CREDIT_SEPARATORS = Regex("""\s*(?:&|,|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b)\s*""", RegexOption.IGNORE_CASE)

/**
 * Split a raw artist credit into the individual performers named in it, e.g.
 * "Billie Eilish & Justin Bieber" -> ["Billie Eilish", "Justin Bieber"].
 *
 * MediaStore (and the ID3 ARTIST tag it reads from) groups every track by this whole string, so a
 * collaboration becomes its own artist distinct from either performer's solo work. This is what
 * lets a track credited to several performers be found again under each of their names, without
 * having to know upfront whether "A & B" is two credited performers or one duo's actual name - see
 * [org.fossify.musicplayer.data.AudioHelper.getArtistTracks], which only trusts a split name once
 * it already matches an existing artist.
 */
fun String.splitArtistCredits(): List<String> = split(ARTIST_CREDIT_SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }
