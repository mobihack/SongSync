package pl.lambada.songsync.ui.screens.home

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.provider.MediaStore
import androidx.compose.runtime.snapshots.Snapshot
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.domain.model.SortOrders
import pl.lambada.songsync.domain.model.SortValues
import pl.lambada.songsync.ui.screens.home.components.UiState
import pl.lambada.songsync.util.FolderMode
import java.io.File

/** The library's folder and "no lyrics only" filters, and the batch dialog's states. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeViewModelFilterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var settings: UserSettingsController
    private lateinit var rock: File
    private lateinit var jazz: File

    private fun context(vararg paths: File): Context = mockk<Context>(relaxed = true).also { ctx ->
        val resolver = mockk<ContentResolver> {
            every { query(any(), any(), any(), any(), any<String>()) } answers {
                MatrixCursor(
                    arrayOf(
                        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
                    )
                ).apply {
                    paths.forEachIndexed { i, f -> addRow(arrayOf<Any>(i.toLong(), f.nameWithoutExtension, "Artist", f.path, 1L, 1000L)) }
                }
            }
        }
        every { ctx.contentResolver } returns resolver
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settings = UserSettingsController(PreferenceDataStoreFactory.create { File(tmp.root, "s.preferences_pb") })
        rock = tmp.newFolder("Rock")
        jazz = tmp.newFolder("Jazz")
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = HomeViewModel(
        settings, mockk(relaxed = true),
        ioDispatcher = dispatcher, mainDispatcher = dispatcher, computeDispatcher = dispatcher,
    )

    /** Lets snapshot state changes reach the flows that watch them, then runs what they started. */
    private fun TestScope.settle() {
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
    }

    private fun HomeViewModel.titles() = displaySongs.map { it.title }

    @Test
    fun `no lyrics only hides songs that already have an lrc beside them`() = runTest(dispatcher) {
        val done = File(rock, "done.mp3").apply { writeText("") }
        File(rock, "done.lrc").writeText("[00:01.00]x")
        val todo = File(rock, "todo.mp3").apply { writeText("") }
        val vm = viewModel()
        vm.loadSongs(context(done, todo), SortValues.TITLE, SortOrders.ASCENDING)
        settle()
        vm.titles() shouldBe listOf("done", "todo")

        settings.updateHideLyrics(true)
        vm.filterSongs()
        settle()

        vm.titles() shouldBe listOf("todo")
    }

    @Test
    fun `ignored folders are left out`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.loadSongs(context(File(rock, "a.mp3"), File(jazz, "b.mp3")), SortValues.TITLE, SortOrders.ASCENDING)
        settle()

        settings.updateBlacklistedFolders(listOf(jazz.path))
        settings.updateFolderMode(FolderMode.IGNORE)
        vm.filterSongs()
        settle()

        vm.titles() shouldBe listOf("a")
    }

    @Test
    fun `only these folders, with none chosen, shows no songs rather than all`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.loadSongs(context(File(rock, "a.mp3")), SortValues.TITLE, SortOrders.ASCENDING)
        settle()

        settings.updateOnlyFolders(emptyList())
        settings.updateFolderMode(FolderMode.ONLY)
        vm.filterSongs()
        settle()

        vm.displaySongs.shouldBeEmpty()
    }

    @Test
    fun `a batch of no songs ends at Done, and closing resets the dialog`() = runTest(dispatcher) {
        val vm = viewModel()
        settings.updateOnlyFolders(emptyList())
        settings.updateFolderMode(FolderMode.ONLY)
        vm.loadSongs(context(File(rock, "a.mp3")), SortValues.TITLE, SortOrders.ASCENDING)
        settle()

        vm.batchDownloadLyrics(mockk(relaxed = true))
        settle()
        vm.batchUiState shouldBe UiState.Done

        vm.closeBatchDownload()
        vm.batchUiState shouldBe UiState.Warning
        vm.batchCounts.done shouldBe 0
    }
}
