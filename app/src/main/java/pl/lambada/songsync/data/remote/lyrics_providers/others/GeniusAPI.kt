package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import pl.lambada.songsync.BuildConfig
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.util.EmptyQueryException
import pl.lambada.songsync.util.ProviderUnavailableException
import pl.lambada.songsync.util.SearchMatching
import pl.lambada.songsync.util.networking.Ktor.client
import pl.lambada.songsync.util.networking.Ktor.json

/**
 * Genius lyrics provider. Plain-text lyrics only (Genius has no synced lyrics).
 * Needs a Client Access Token from https://genius.com/api-clients, set as
 * `genius.token` in local.properties.
 */
class GeniusAPI {
    private val baseURL = "https://api.genius.com/"
    private val token = BuildConfig.GENIUS_TOKEN

    suspend fun getSongInfo(query: SongInfo, offset: Int = 0): SongInfo? {
        val search = "${SearchMatching.cleanTitle(query.songName)} ${query.artistName.orEmpty()}".trim()
        if (search.isBlank())
            throw EmptyQueryException()

        val response = request("search") { parameter("q", search) } ?: return null
        val hits = response.obj("response")?.get("hits") as? JsonArray ?: return null
        // Genius ranks loosely, so pick the hit that actually matches the requested song
        val song = SearchMatching.rank(
            hits.mapNotNull { (it as? JsonObject)?.obj("result") },
            query.songName, query.artistName,
            candidateTitle = { it.string("title") },
            candidateArtist = { it.obj("primary_artist")?.string("name") }
        ).getOrNull(offset) ?: return null

        return SongInfo(
            songName = song.string("title"),
            artistName = song.obj("primary_artist")?.string("name"),
            songLink = song.string("url"),
            albumCoverLink = song.string("header_image_thumbnail_url"),
            geniusID = song.string("id")?.toLongOrNull() ?: return null
        )
    }

    suspend fun getLyrics(id: Long): String? {
        val response = request("songs/$id") { parameter("text_format", "dom") } ?: return null
        val dom = response.obj("response")?.obj("song")?.obj("lyrics")?.get("dom") ?: return null
        return domToText(dom).trim().takeIf { it.isNotBlank() }
    }

    private suspend fun request(
        path: String,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit
    ): JsonObject? {
        if (token.isBlank())
            throw ProviderUnavailableException("Genius needs an API token (genius.token in local.properties).")

        val response = client.get(baseURL + path) {
            header("Authorization", "Bearer $token")
            block()
        }
        if (response.status.value == 401)
            throw ProviderUnavailableException("Genius rejected the API token.")
        if (response.status.value !in 200..299)
            return null
        return json.parseToJsonElement(response.bodyAsText(Charsets.UTF_8)).jsonObject
    }

    /** Flattens Genius' lyrics DOM tree into text, turning <br> into newlines. */
    private fun domToText(node: JsonElement): String = when (node) {
        is JsonPrimitive -> node.contentOrNull.orEmpty()
        is JsonObject ->
            if (node.string("tag") == "br") "\n"
            else (node["children"] as? JsonArray)?.joinToString("") { domToText(it) }.orEmpty()
        else -> ""
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}
