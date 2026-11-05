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

    @Test
    fun `folders with commas in their names survive saving`() {
        val folders = listOf(
            "$music/Tyler, The Creator - CHROMAKOPIA (2024)",
            "$music/Rock",
            "$music/Hardwell & Friends, Vol. 1 (Extended Mix)",
        )
        MusicFolders.decode(MusicFolders.encode(folders)) shouldBe folders
    }

    @Test
    fun `a single comma folder saved by the old comma format is read back whole`() {
        val folder = "$music/Tiësto - Club Life, Vol. 4 - New York City"
        MusicFolders.decode(folder) shouldBe listOf(folder)
    }

    @Test
    fun `old comma-joined lists are split only where a new path starts`() {
        val old = "$music/Tyler, The Creator - CHROMAKOPIA (2024),$music/Rock,$music/DTG, Vol. 1"
        MusicFolders.decode(old) shouldBe listOf(
            "$music/Tyler, The Creator - CHROMAKOPIA (2024)",
            "$music/Rock",
            "$music/DTG, Vol. 1",
        )
    }

    @Test
    fun `an empty setting is an empty list`() {
        MusicFolders.decode("") shouldBe emptyList()
        MusicFolders.encode(emptyList()) shouldBe ""
    }
}
