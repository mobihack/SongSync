package pl.lambada.songsync.util

import java.text.Normalizer

/**
 * Helpers for picking the right track out of a provider's search results, for providers whose
 * search ranks loosely (e.g. Genius).
 */
object SearchMatching {
    private val bracketed = Regex("""\s*[(\[][^)\]]*[)\]]""")
    // Not "with": it's part of many real titles ("Stay With Me")
    private val featuring = Regex("""\s+(feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)
    private val artistSeparators = Regex("""\s*(,|&|\band\b|\bx\b|\bfeat\.?|\bft\.?|\bwith\b|;|/)\s*""", RegexOption.IGNORE_CASE)
    private val nonWord = Regex("""[^\p{L}\p{N}]+""")
    private val diacritics = Regex("""\p{Mn}+""")

    /** Title without "(feat. …)", "[Remastered]", "- Live" style noise, for building a search query. */
    fun cleanTitle(title: String?): String =
        title.orEmpty().replace(bracketed, "").replace(featuring, "").trim()

    /**
     * Sorts [candidates] by how well they match the requested [title], [artist] and [durationMs].
     *
     * In [strict] mode, candidates that clearly aren't the same song are dropped: use it for
     * providers whose search ranks loosely. Otherwise results are only reordered (ties keep the
     * provider's order), so a correct match the scorer can't recognise, e.g. a native-script title
     * for a romanized local one, is never lost.
     */
    fun <T> rank(
        candidates: List<T>,
        title: String?,
        artist: String?,
        candidateTitle: (T) -> String?,
        candidateArtist: (T) -> String?,
        durationMs: Long? = null,
        candidateDuration: (T) -> Long? = { null },
        strict: Boolean = true,
    ): List<T> {
        val scored = candidates.map {
            it to score(title, artist, candidateTitle(it), candidateArtist(it), durationMs, candidateDuration(it))
        }
        return scored
            .filter { (_, score) -> !strict || score >= MIN_SCORE }
            .sortedByDescending { (_, score) -> score } // stable: ties keep provider order
            .map { (candidate, _) -> candidate }
    }

    /**
     * 0..1 similarity. Title counts 60% and artist 40%; when both durations are known they take a
     * 20% share, and a duration more than [DURATION_TOLERANCE_MS] off rules the candidate out.
     * A weak title match is always rejected.
     */
    fun score(
        title: String?,
        artist: String?,
        candidateTitle: String?,
        candidateArtist: String?,
        durationMs: Long? = null,
        candidateDurationMs: Long? = null,
    ): Double {
        val durationDiff = if (durationMs != null && durationMs > 0 && candidateDurationMs != null && candidateDurationMs > 0)
            kotlin.math.abs(durationMs - candidateDurationMs) else null
        if (durationDiff != null && durationDiff > DURATION_TOLERANCE_MS) return 0.0

        val titleScore = similarity(normalize(cleanTitle(title)), normalize(cleanTitle(candidateTitle)))
        if (titleScore < MIN_TITLE_SCORE) return 0.0

        // No artist to compare against: rely on the title alone
        val textScore = if (artist.isNullOrBlank()) titleScore else {
            val wanted = splitArtists(artist)
            val found = splitArtists(candidateArtist)
            val artistScore = wanted.maxOfOrNull { w -> found.maxOfOrNull { f -> similarity(w, f) } ?: 0.0 } ?: 0.0
            0.6 * titleScore + 0.4 * artistScore
        }
        if (durationDiff == null) return textScore

        val durationScore = 1.0 - durationDiff.toDouble() / DURATION_TOLERANCE_MS
        return 0.8 * textScore + 0.2 * durationScore
    }

    private fun splitArtists(artists: String?): List<String> =
        artists.orEmpty().split(artistSeparators).map { normalize(it) }.filter { it.isNotEmpty() }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(diacritics, "")
            .lowercase()
            .replace(nonWord, " ")
            .trim()

    /** 1.0 for equal, 0.85 when one contains the other, otherwise word overlap (Jaccard). */
    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        if (a.contains(b) || b.contains(a)) return 0.85
        val wordsA = a.split(' ').toSet()
        val wordsB = b.split(' ').toSet()
        return wordsA.intersect(wordsB).size.toDouble() / wordsA.union(wordsB).size
    }

    private const val MIN_TITLE_SCORE = 0.5
    private const val MIN_SCORE = 0.55
    private const val DURATION_TOLERANCE_MS = 10_000L
}
