package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.ktor.client.HttpClient
import android.util.Base64
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import org.w3c.dom.Element
import pl.lambada.songsync.BuildConfig
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.util.EmptyQueryException
import pl.lambada.songsync.util.ProviderUnavailableException
import pl.lambada.songsync.util.SearchMatching
import pl.lambada.songsync.util.networking.Ktor
import javax.xml.parsers.DocumentBuilderFactory

/**
 * PetitLyrics provider (strong for Japanese songs). Plain-text lyrics only.
 * Needs a client app ID, set as `petitlyrics.clientId` in local.properties.
 * Search results already contain the lyrics, so no second request is needed.
 */
class PetitLyricsAPI(
    private val client: HttpClient = Ktor.client,
    private val clientId: String = BuildConfig.PETITLYRICS_CLIENT_ID,
) {
    private val apiURL = "https://on.petitlyrics.com/api/GetPetitLyricsData.php"

    suspend fun getSongInfo(query: SongInfo, offset: Int = 0): SongInfo? {
        val title = SearchMatching.cleanTitle(query.songName)
        if (title.isBlank())
            throw EmptyQueryException()
        if (clientId.isBlank())
            throw ProviderUnavailableException("PetitLyrics needs a client ID (petitlyrics.clientId in local.properties).")

        val response = client.submitForm(
            url = apiURL,
            formParameters = parameters {
                append("key_title", title)
                append("key_artist", query.artistName.orEmpty())
                append("key_album", "")
                append("lyricsType", "1")
                append("terminalType", "10")
                append("clientAppId", clientId)
            }
        )
        if (response.status.value !in 200..299)
            return null

        val songs = parseSongs(response.bodyAsText(Charsets.UTF_8))
        return SearchMatching.rank(
            songs, query.songName, query.artistName,
            candidateTitle = { it.songName },
            candidateArtist = { it.artistName }
        ).getOrNull(offset)
    }

    private fun parseSongs(xml: String): List<SongInfo> {
        val doc = DocumentBuilderFactory.newInstance().apply {
            // Don't resolve external entities from a remote document
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(xml.byteInputStream())

        val nodes = doc.getElementsByTagName("song")
        return (0 until nodes.length).mapNotNull { i ->
            val song = nodes.item(i) as? Element ?: return@mapNotNull null
            fun text(tag: String) = song.getElementsByTagName(tag).item(0)?.textContent

            val lyricsData = text("lyricsData")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val lyrics = String(Base64.decode(lyricsData, Base64.DEFAULT), Charsets.UTF_8)

            SongInfo(
                songName = text("title"),
                artistName = text("artist"),
                songLink = text("lyricsId")?.let { "https://petitlyrics.com/lyrics/$it" },
                unsyncedLyrics = lyrics
            )
        }
    }
}
