package pl.lambada.songsync.activities.quicksearch

/**
 * The song to search for from a share: the app's own "songName"/"artistName" extras when a player
 * sends them, otherwise the shared text, such as "Hello by Adele" or "Check out Hello by Adele on
 * Spotify https://open.spotify.com/…". Links are dropped; without " by ", the whole text is
 * searched as the title.
 *
 * @return title to artist ("" when unknown), or null when there is nothing to search for
 */
fun sharedSong(songName: String?, artistName: String?, text: String?): Pair<String, String>? {
    if (!songName.isNullOrBlank()) return songName.trim() to artistName.orEmpty().trim()

    val cleaned = text.orEmpty()
        .replace(Regex("""\S+://\S+"""), " ") // links
        .replace(Regex("""^\s*(check out|listen to|now playing:?|i'm listening to)\s+""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+on\s+(spotify|youtube( music)?|apple music|deezer|soundcloud|tidal)\s*[.!]?\s*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .trim('"', '“', '”', '\'')
    if (cleaned.isBlank()) return null

    val by = Regex("""^(.+?)\s+by\s+(.+)$""", RegexOption.IGNORE_CASE).find(cleaned)
    return if (by != null) by.groupValues[1].trim('"', '“', '”') to by.groupValues[2] else cleaned to ""
}
