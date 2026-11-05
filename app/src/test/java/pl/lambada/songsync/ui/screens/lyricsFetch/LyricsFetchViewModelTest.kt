package pl.lambada.songsync.ui.screens.lyricsFetch

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.data.remote.lyrics_providers.LyricsLookup
import pl.lambada.songsync.data.remote.lyrics_providers.LyricsProviderService
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.ui.LocalSong
import pl.lambada.songsync.util.ProviderChain
import pl.lambada.songsync.util.Providers
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsFetchViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val context = mockk<Context>(relaxed = true)
    private val service = mockk<LyricsProviderService>()
    private lateinit var settings: UserSettingsController

    private val song = LocalSong("3005", "Childish Gambino", null, "/storage/emulated/0/Music/3005.mp3")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settings = UserSettingsController(
            PreferenceDataStoreFactory.create { File(tmp.root, "settings.preferences_pb") }
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = LyricsFetchViewModel(song, settings, service, ioDispatcher = dispatcher)

    private fun found(lyrics: String) =
        ProviderChain.Found(Providers.LRCLIB, LyricsLookup(SongInfo("3005", "Childish Gambino"), lyrics))

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    @Test
    fun `the first search runs once, not again when the screen is recreated`() = test {
        coEvery { service.findLyrics(any(), any(), any(), any()) } returns found("[00:01.00]Hi")
        val vm = viewModel()

        vm.loadInitialSongInfo(context)
        advanceUntilIdle()
        vm.loadInitialSongInfo(context) // the screen composed again after rotation
        advanceUntilIdle()

        coVerify(exactly = 1) { service.findLyrics(any(), any(), any(), any()) }
        vm.lyricsFetchState shouldBe LyricsFetchState.Success("[00:01.00]Hi")
    }

    @Test
    fun `a newer search replaces one still running, so the old result can't win`() = test {
        val slowSearch = CompletableDeferred<ProviderChain.Found<LyricsLookup>>()
        var calls = 0
        coEvery { service.findLyrics(any(), any(), any(), any()) } coAnswers {
            if (calls++ == 0) slowSearch.await() else found("[00:01.00]new provider")
        }
        val vm = viewModel()

        vm.loadSongInfo(context) // e.g. the old provider, still searching
        advanceUntilIdle()
        vm.loadSongInfo(context) // the user picked another provider
        advanceUntilIdle()
        slowSearch.complete(found("[00:01.00]old provider")) // too late: that search was cancelled
        advanceUntilIdle()

        vm.lyricsFetchState shouldBe LyricsFetchState.Success("[00:01.00]new provider")
    }

    @Test
    fun `fetching another language replaces a search still running`() = test {
        val slowSearch = CompletableDeferred<ProviderChain.Found<LyricsLookup>>()
        coEvery { service.findLyrics(any(), any(), any(), any()) } coAnswers { slowSearch.await() }
        coEvery { service.getLyricsInLanguage(7, "es") } returns "[00:01.00]Hola"
        val vm = viewModel()

        vm.loadSongInfo(context)
        advanceUntilIdle()
        vm.fetchLyricsInLanguage(7, "es")
        advanceUntilIdle()
        slowSearch.complete(found("[00:01.00]Hello"))
        advanceUntilIdle()

        vm.lyricsFetchState shouldBe LyricsFetchState.Success("[00:01.00]Hola")
    }

    @Test
    fun `being offline shows No connection instead of an error`() = test {
        coEvery { service.findLyrics(any(), any(), any(), any()) } throws java.net.UnknownHostException("lrclib.net")
        val vm = viewModel()

        vm.loadSongInfo(context)
        advanceUntilIdle()

        vm.queryState shouldBe QueryStatus.NoConnection
    }

    @Test
    fun `nothing found anywhere is reported as no results`() = test {
        coEvery { service.findLyrics(any(), any(), any(), any()) } returns null
        val vm = viewModel()

        vm.loadSongInfo(context)
        advanceUntilIdle()

        (vm.queryState as QueryStatus.Failed).exception::class shouldBe pl.lambada.songsync.util.NoTrackFoundException::class
    }
}
