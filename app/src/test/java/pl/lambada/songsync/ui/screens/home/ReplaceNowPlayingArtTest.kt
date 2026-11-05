package pl.lambada.songsync.ui.screens.home

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReplaceNowPlayingArtTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `each new cover replaces the last, so covers don't pile up`() {
        val first = replaceNowPlayingArt(tmp.root, null) { it.write(1) }
        val second = replaceNowPlayingArt(tmp.root, first) { it.write(2) }
        val third = replaceNowPlayingArt(tmp.root, second) { it.write(3) }

        tmp.root.listFiles()!!.toList() shouldContainExactly listOf(third)
        third.readBytes().toList() shouldBe listOf<Byte>(3)
    }

    @Test
    fun `each cover gets a new name, so the image view reloads`() {
        val first = replaceNowPlayingArt(tmp.root, null) { it.write(1) }
        val second = replaceNowPlayingArt(tmp.root, first) { it.write(2) }

        (first.name == second.name) shouldBe false
    }
}
