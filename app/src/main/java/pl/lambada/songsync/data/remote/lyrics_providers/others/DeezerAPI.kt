package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.util.EmptyQueryException
import pl.lambada.songsync.util.ProviderUnavailableException
import pl.lambada.songsync.util.SearchMatching
import pl.lambada.songsync.util.Providers
import pl.lambada.songsync.util.networking.Ktor
import pl.lambada.songsync.util.networking.Ktor.json

/**
 * Deezer lyrics provider, using an anonymous session and Deezer's GraphQL API.
 */
class DeezerAPI(private val client: HttpClient = Ktor.clientFor(Providers.DEEZER)) {
    private val authURL = "https://auth.deezer.com/login/anonymous?jo=p"
    private val graphqlURL = "https://pipe.deezer.com/api"
    private val infoURL = "https://api.deezer.com/infos"

    /**
     * Searches Deezer for a track.
     * @return The track at [offset] in the results, or null if there is none.
     * @throws ProviderUnavailableException if Deezer isn't available in the user's region.
     */
    suspend fun getSongInfo(query: SongInfo, offset: Int = 0): SongInfo? {
        val search = "${SearchMatching.cleanTitle(query.songName)} ${query.artistName.orEmpty()}".trim()
        if (search.isBlank())
            throw EmptyQueryException()

        val response = graphql(SEARCH_QUERY, buildJsonObject {
            put("query", search)
            put("pictureRequest", buildJsonObject {
                put("width", 500)
                put("height", 500)
            })
        })
        val edges = response.obj("data")?.obj("search")?.obj("results")?.obj("tracks")
            ?.get("edges")?.let { it as? JsonArray } ?: return null

        // Tracks Deezer can't serve in this region come back as null nodes with errors
        val tracks = edges.mapNotNull { (it as? JsonObject)?.obj("node") }
        if (tracks.isEmpty()) {
            if (edges.isNotEmpty() && !isAvailableInRegion())
                throw ProviderUnavailableException("Deezer isn't available in your region.")
            return null
        }

        return SearchMatching.rank(
            tracks.mapNotNull { toSongInfo(it) },
            query.songName, query.artistName,
            candidateTitle = { it.songName },
            candidateArtist = { it.artistName },
            durationMs = query.durationMs,
            candidateDuration = { it.durationMs },
        ).getOrNull(offset)
    }

    private fun toSongInfo(track: JsonObject): SongInfo? {
        val id = track.string("id")?.toLongOrNull() ?: return null

        val artist = track.obj("contributors")?.get("edges")?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { edge -> edge["roles"]?.jsonArray?.any { it.jsonPrimitive.contentOrNull == "MAIN" } == true }
            ?.obj("node")?.string("name")

        val cover = track.obj("album")?.obj("cover")?.get("urls")
            ?.let { it as? JsonArray }?.firstOrNull()?.jsonPrimitive?.contentOrNull

        return SongInfo(
            songName = track.string("title"),
            artistName = artist,
            songLink = "https://www.deezer.com/track/$id",
            albumCoverLink = cover,
            deezerID = id,
            durationMs = track.string("duration")?.toLongOrNull()?.let { if (it < 36_000) it * 1000 else it }
        )
    }

    /**
     * Gets lyrics for a Deezer track: synced (LRC) when available, plain text otherwise.
     */
    suspend fun getSyncedLyrics(id: Long): String? {
        val response = graphql(LYRICS_QUERY, buildJsonObject { put("trackId", id.toString()) })
        val lyrics = response.obj("data")?.obj("track")?.obj("lyrics") ?: return null

        val synced = (lyrics["synchronizedLines"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.joinToString("\n") { "${it.string("lrcTimestamp").orEmpty()}${it.string("line").orEmpty()}" }
            ?.takeIf { it.isNotBlank() }

        return synced ?: lyrics.string("text")?.takeIf { it.isNotBlank() }
    }

    private suspend fun graphql(query: String, variables: JsonObject, retry: Boolean = true): JsonObject {
        val body = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }
        val response = client.post(graphqlURL) {
            header("Authorization", "Bearer ${getAuthToken()}")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }

        // Anonymous tokens expire; fetch a new one and retry once
        if (response.status.value == 401 && retry) {
            authToken = null
            return graphql(query, variables, retry = false)
        }

        return response.jsonObject()
    }

    /**
     * The body of a successful JSON response. Deezer's edge answers blocked networks (some regions,
     * VPNs, datacenter IPs) with an HTML "Access Denied" page, which would otherwise fail to parse.
     */
    private suspend fun HttpResponse.jsonObject(): JsonObject {
        val body = bodyAsText(Charsets.UTF_8)
        if (status.value !in 200..299 || !body.trimStart().startsWith("{"))
            throw ProviderUnavailableException(
                "Deezer is blocking requests from your network right now. Try again later or use a proxy."
            )
        return json.parseToJsonElement(body).jsonObject
    }

    private suspend fun getAuthToken(): String {
        authToken?.let { return it }
        return client.get(authURL).jsonObject().string("jwt")
            ?.also { authToken = it }
            ?: throw ProviderUnavailableException("Couldn't start a Deezer session.")
    }

    private suspend fun isAvailableInRegion(): Boolean = try {
        val info = json.parseToJsonElement(client.get(infoURL).bodyAsText(Charsets.UTF_8)).jsonObject
        info["open"]?.jsonPrimitive?.booleanOrNull != false
    } catch (e: Exception) {
        true // can't tell; don't claim it's unavailable
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    companion object {
        @Volatile
        private var authToken: String? = null

        /** Forgets the cached anonymous session (for tests). */
        internal fun clearSession() {
            authToken = null
        }

        private val SEARCH_QUERY = """
            query Search(${'$'}query: String, ${'$'}pictureRequest: PictureRequest!) {
              search(query: ${'$'}query) {
                results {
                  tracks {
                    edges {
                      node {
                        id
                        title
                        duration
                        album { cover { urls(pictureRequest: ${'$'}pictureRequest) } }
                        contributors { edges { node { ... on Artist { name } } roles } }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        private val LYRICS_QUERY = """
            query Lyrics(${'$'}trackId: String!) {
              track(trackId: ${'$'}trackId) {
                lyrics {
                  text
                  synchronizedLines { lrcTimestamp line }
                }
              }
            }
        """.trimIndent()
    }
}
