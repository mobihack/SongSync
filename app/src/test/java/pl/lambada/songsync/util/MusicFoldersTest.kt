package pl.lambada.songsync.util

import io.kotest.matchers.shouldBe
import org.junit.Test

class MusicFoldersTest {

    private val music = "/storage/emulated/0/Music"

    private fun includes(folder: String, mode: FolderMode, ignored: List<String> = emptyList(), only: List<String> = emptyList()) =
        MusicFolders.includes(folder, mode, ignored, only)

    @Test
    fun `all music includes every folder`() {
        includes("$music/Rock", FolderMode.ALL, ignored = listOf("$music/Rock")) shouldBe true
    }

    @Test
    fun `ignored folders and their subfolders are hidden`() {
        includes("$music/Podcasts", FolderMode.IGNORE, ignored = listOf("$music/Podcasts")) shouldBe false
        includes("$music/Podcasts/2024", FolderMode.IGNORE, ignored = listOf("$music/Podcasts")) shouldBe false
        includes("$music/Rock", FolderMode.IGNORE, ignored = listOf("$music/Podcasts")) shouldBe true
    }

    @Test
    fun `only mode shows chosen folders and their subfolders, nothing else`() {
        includes("$music/Rock", FolderMode.ONLY, only = listOf("$music/Rock")) shouldBe true
        includes("$music/Rock/Live", FolderMode.ONLY, only = listOf("$music/Rock")) shouldBe true
        includes("$music/Jazz", FolderMode.ONLY, only = listOf("$music/Rock")) shouldBe false
        includes("$music/Rock", FolderMode.ONLY) shouldBe false
    }

    @Test
    fun `a folder whose name merely starts the same is not a subfolder`() {
        includes("$music/Rockabilly", FolderMode.ONLY, only = listOf("$music/Rock")) shouldBe false
        includes("$music/Rockabilly", FolderMode.IGNORE, ignored = listOf("$music/Rock")) shouldBe true
    }

    @Test
    fun `blank entries never match`() {
        includes("$music/Rock", FolderMode.IGNORE, ignored = listOf("")) shouldBe true
    }

    @Test
    fun `folder of a file path is its parent directory`() {
        MusicFolders.folderOf("$music/Rock/song.mp3") shouldBe "$music/Rock"
    }
}
