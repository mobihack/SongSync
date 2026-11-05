package pl.lambada.songsync.data.remote.lyrics_providers.others

import android.util.Base64
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.testutil.FakeHttp
import pl.lambada.songsync.testutil.action
import pl.lambada.songsync.testutil.respondJson
import pl.lambada.songsync.util.EmptyQueryException
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream

class KugouAPITest {

    @Before
    fun mockBase64() {
        // android.util.Base64 isn't available in JVM unit tests; delegate to java.util.Base64
        mockkStatic(Base64::class)
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getMimeDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun unmock() = unmockkStatic(Base64::class)

    private val key = intArrayOf(0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47, 0x51, 0x36, 0x31, 0x2d, 0xce, 0xd2, 0x6e, 0x69)

    /** Builds a KRC payload the way KuGou does: zlib, XOR with the key, "krc1" header, base64. */
    private fun encodeKrc(text: String): String {
        val zipped = ByteArrayOutputStream().also { out ->
            DeflaterOutputStream(out).use { it.write(text.toByteArray()) }
        }.toByteArray()
        val body = zipped.mapIndexed { i, b -> (b.toInt() xor key[i % key.size]).toByte() }.toByteArray()
        return java.util.Base64.getEncoder().encodeToString("krc1".toByteArray() + body)
    }

    private val krc = """
        [ti:晴天]
        [ar:周杰伦]
        [29000,2500]<0,500,0>故<500,500,0>事<1000,1500,0>的
        [31500,1000]<0,1000,0>小黄花
    """.trimIndent()

    private val query = SongInfo(songName = "晴天", artistName = "周杰伦", durationMs = 269_500)

    private fun song(name: String, duration: Int, hash: String, language: String = "国语") =
        """{"songname":"$name","singername":"周杰伦","album_name":"叶惠美","duration":$duration,"hash":"$hash",
            "trans_param":{"language":"$language"}}"""

    private fun backend(
        songs: String = "[${song("晴天 (Live)", 299, "live")},${song("晴天", 269, "studio")}]",
        candidates: String = """[{"id":"274944371","accesskey":"KEY","krctype":1}]""",
        krcContent: String = encodeKrc(krc),
        lrcContent: String = java.util.Base64.getEncoder().encodeToString("[00:29.00]故事的\n[00:31.50]小黄花".toByteArray()),
    ) = FakeHttp { request ->
        when {
            request.url.host == "mobilecdn.kugou.com" -> respondJson("""{"data":{"info":$songs}}""")
            request.action == "search" -> respondJson("""{"candidates":$candidates}""")
            request.url.parameters["fmt"] == "krc" -> respondJson("""{"status":200,"content":"$krcContent"}""")
            else -> respondJson("""{"status":200,"content":"$lrcContent"}""")
        }
    }

    @Test
    fun `search picks the song whose duration matches`() = runTest {
        val http = backend()
        val song = KugouAPI(http.client).getSongInfo(query)!!

        song.songName shouldBe "晴天"
        song.artistName shouldBe "周杰伦"
        song.kugouHash shouldBe "studio"
        song.durationMs shouldBe 269_000
        http.requests.single().url.parameters["keyword"] shouldBe "晴天 周杰伦"
    }

    @Test
    fun `instrumental tracks are skipped`() = runTest {
        val http = backend(songs = "[${song("晴天", 269, "studio", language = "纯音乐")}]")
        KugouAPI(http.client).getSongInfo(query).shouldBeNull()
    }

    @Test
    fun `no songs gives null`() = runTest {
        KugouAPI(backend(songs = "[]").client).getSongInfo(query).shouldBeNull()
    }

    @Test
    fun `KRC lyrics are decrypted into word-by-word LRC`() = runTest {
        val http = backend()
        KugouAPI(http.client).getSyncedLyrics(hash = "studio", durationMs = 269_000) shouldBe
            "[00:29.000]<00:29.000>故<00:29.500>事<00:30.000>的<00:31.500>\n" +
            "[00:31.500]<00:31.500>小黄花<00:32.500>"

        val lyricsSearch = http.requests.first { it.action == "search" }
        lyricsSearch.url.parameters["hash"] shouldBe "studio"
        lyricsSearch.url.parameters["duration"] shouldBe "269"
        val download = http.requests.last()
        download.url.parameters["id"] shouldBe "274944371"
        download.url.parameters["accesskey"] shouldBe "KEY"
    }

    @Test
    fun `plain LRC is used when there are no word timings`() = runTest {
        val http = backend(candidates = """[{"id":"1","accesskey":"K","krctype":0}]""")
        KugouAPI(http.client).getSyncedLyrics("studio", 269_000) shouldBe "[00:29.00]故事的\n[00:31.50]小黄花"
        http.requests.last().url.parameters["fmt"] shouldBe "lrc"
    }

    @Test
    fun `a corrupt KRC payload falls back to LRC`() = runTest {
        val http = backend(krcContent = java.util.Base64.getEncoder().encodeToString("not krc".toByteArray()))
        KugouAPI(http.client).getSyncedLyrics("studio", 269_000) shouldBe "[00:29.00]故事的\n[00:31.50]小黄花"
    }

    @Test
    fun `no lyrics candidates gives null`() = runTest {
        KugouAPI(backend(candidates = "[]").client).getSyncedLyrics("studio", 269_000).shouldBeNull()
    }

    @Test
    fun `missing artist finds nothing, without a request`() = runTest {
        val http = backend()
        KugouAPI(http.client).getSongInfo(SongInfo("晴天", "")).shouldBeNull()
        http.requests.shouldBeEmpty()
    }

    @Test
    fun `missing title throws`() = runTest {
        shouldThrow<EmptyQueryException> { KugouAPI(backend().client).getSongInfo(SongInfo("", "周杰伦")) }
    }

    @Test
    fun `decodeKrc matches the documented algorithm`() {
        KugouAPI.decodeKrc(java.util.Base64.getDecoder().decode(encodeKrc("[0,1000]<0,500,0>hi"))) shouldBe
            "[0,1000]<0,500,0>hi"
    }
}
