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
     * Sorts [candidates] by how well they match the requested [title] and [artist], dropping ones
     * that clearly aren't the same song. Returns the remaining candidates, best first.
     */
    fun <T> rank(
        candidates: List<T>,
        title: String?,
        artist: String?,
        candidateTitle: (T) -> String?,
        candidateArtist: (T) -> String?,
    ): List<T> = candidates
        .map { it to score(title, artist, candidateTitle(it), candidateArtist(it)) }
        .filter { (_, score) -> score >= MIN_SCORE }
        .sortedByDescending { (_, score) -> score }
        .map { (candidate, _) -> candidate }

    /** 0..1 similarity; title counts 60%, artist 40%. A weak title match is always rejected. */
    fun score(title: String?, artist: String?, candidateTitle: String?, candidateArtist: String?): Double {
        val titleScore = similarity(normalize(cleanTitle(title)), normalize(cleanTitle(candidateTitle)))
        if (titleScore < MIN_TITLE_SCORE) return 0.0

        // No artist to compare against: rely on the title alone
        if (artist.isNullOrBlank()) return titleScore

        val wanted = splitArtists(artist)
        val found = splitArtists(candidateArtist)
        val artistScore = wanted.maxOfOrNull { w -> found.maxOfOrNull { f -> similarity(w, f) } ?: 0.0 } ?: 0.0

        return 0.6 * titleScore + 0.4 * artistScore
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
}
