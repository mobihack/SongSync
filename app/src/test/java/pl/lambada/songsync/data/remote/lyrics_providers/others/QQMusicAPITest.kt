package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Test
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.domain.model.lyrics_providers.others.PaxQQPayload
import pl.lambada.songsync.testutil.FakeHttp
import pl.lambada.songsync.testutil.respondJson
import pl.lambada.songsync.testutil.textBody
import pl.lambada.songsync.util.networking.Ktor.json

class QQMusicAPITest {

    private val query = SongInfo(songName = "晴天", artistName = "周杰伦")

    private val searchResponse = """
        {"data":{"song":{"list":[
          {"title":"晴天","singer":[{"name":"周杰伦"}],"album":{"name":"叶惠美"},"id":97773}
        ]}}}
    """.trimIndent()

    private val lineLyrics = """
        {"type":"Line","content":[
          {"text":[{"text":"故事的小黄花","part":false,"timestamp":null,"endtime":null}],
           "timestamp":29000,"oppositeTurn":false,"background":false,"backgroundText":[],"endtime":31000},
          {"text":[{"text":"从出生那年就飘着","part":false,"timestamp":null,"endtime":null}],
           "timestamp":31500,"oppositeTurn":false,"background":false,"backgroundText":[],"endtime":34000}
        ]}
    """.trimIndent()

    @Test
    fun `search builds the lyrics payload from the first result`() = runTest {
        val http = FakeHttp { respondJson(searchResponse) }
        val song = QQMusicAPI(http.client).getSongInfo(query)!!

        song.songName shouldBe "晴天"
        song.artistName shouldBe "周杰伦"
        json.decodeFromString(PaxQQPayload.serializer(), song.qqPayload!!) shouldBe
            PaxQQPayload(artist = listOf("周杰伦"), album = "叶惠美", id = 97773, title = "晴天")
        http.requests.single().url.parameters["w"] shouldBe "晴天 周杰伦"
    }

    @Test
    fun `offset past the results gives null`() = runTest {
        val http = FakeHttp { respondJson(searchResponse) }
        QQMusicAPI(http.client).getSongInfo(query, offset = 1).shouldBeNull()
    }

    @Test
    fun `lyrics posts the payload and formats line lyrics as LRC`() = runTest {
        val http = FakeHttp { respondJson(lineLyrics) }
        val lyrics = QQMusicAPI(http.client).getSyncedLyrics("""{"id":97773}""")

        lyrics shouldBe "[00:29.000]故事的小黄花\n[00:31.500]从出生那年就飘着"
        http.requests.single().textBody shouldContain "97773"
    }

    @Test
    fun `a server error gives null instead of parsing the error page`() = runTest {
        // Lyrics that would parse, so only the status check can turn them away
        val http = FakeHttp { respondJson(lineLyrics, status = HttpStatusCode.BadGateway) }
        QQMusicAPI(http.client).getSyncedLyrics("{}").shouldBeNull()
    }

    @Test
    fun `empty lyrics give null`() = runTest {
        val http = FakeHttp { respondJson("""{"type":"Line","content":[]}""") }
        QQMusicAPI(http.client).getSyncedLyrics("{}").shouldBeNull()
    }
}
