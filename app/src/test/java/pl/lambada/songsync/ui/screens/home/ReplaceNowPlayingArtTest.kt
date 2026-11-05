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

    @Test
    fun `a fresh start clears old covers and keeps other caches`() {
        val current = replaceNowPlayingArt(tmp.root, null) { it.write(1) }
        val legacy = tmp.root.resolve("0b4f8c3e-1d2a-4b5c-9e8f-7a6b5c4d3e2f.jpg").apply { writeText("x") }
        val imageCache = tmp.newFolder("image_cache").resolve("entry").apply { writeText("x") }
        val other = tmp.root.resolve("notes.jpg").apply { writeText("x") }

        clearStaleCovers(tmp.root)

        current.exists() shouldBe false
        legacy.exists() shouldBe false
        imageCache.exists() shouldBe true
        other.exists() shouldBe true
    }
}
