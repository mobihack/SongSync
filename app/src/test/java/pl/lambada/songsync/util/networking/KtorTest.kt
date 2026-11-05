package pl.lambada.songsync.util.networking

import io.kotest.matchers.shouldBe
import org.junit.Test

class KtorTest {

    @Test
    fun `every request has an overall time limit`() {
        Ktor.okHttp.callTimeoutMillis shouldBe Ktor.CALL_TIMEOUT_SECONDS.toInt() * 1000
        Ktor.baseOkHttp.callTimeoutMillis shouldBe Ktor.CALL_TIMEOUT_SECONDS.toInt() * 1000
    }
}
