package pl.lambada.songsync.ui.screens.home

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.provider.MediaStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeViewModelLoadTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val sortOrders = mutableListOf<String?>()
    private var queryError: Exception? = null
    private lateinit var settings: UserSettingsController

    private val context = mockk<Context>(relaxed = true).also { ctx ->
        val resolver = mockk<ContentResolver> {
            every { query(any(), any(), any(), any(), any<String>()) } answers {
                sortOrders += arg<String?>(4)
                queryError?.let { throw it }
                MatrixCursor(
                    arrayOf(
                        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
                    )
                ).apply { addRow(arrayOf<Any>(1L, "Hello", "Adele", "/storage/emulated/0/Music/hello.mp3", 5L, 295000L)) }
            }
        }
        every { ctx.contentResolver } returns resolver
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settings = UserSettingsController(
            PreferenceDataStoreFactory.create { File(tmp.root, "settings.preferences_pb") }
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = HomeViewModel(
        settings, mockk(relaxed = true),
        ioDispatcher = dispatcher, mainDispatcher = dispatcher, computeDispatcher = dispatcher,
    )

    @Test
    fun `coming back with the same sort doesn't rescan the library`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.loadSongs(context, SortValues.TITLE, SortOrders.ASCENDING)
        advanceUntilIdle()
        vm.loadSongs(context, SortValues.TITLE, SortOrders.ASCENDING) // back from the lyrics screen
        advanceUntilIdle()

        sortOrders.size shouldBe 1
        vm.allSongs?.map { it.title } shouldBe listOf("Hello")
    }

    @Test
    fun `a new sort loads again, and the newest sort wins`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.loadSongs(context, SortValues.TITLE, SortOrders.ASCENDING)
        vm.loadSongs(context, SortValues.ARTIST, SortOrders.DESCENDING) // before the first ran
        advanceUntilIdle()

        sortOrders shouldBe listOf("ARTIST DESC") // the replaced load never queried
    }

    @Test
    fun `refresh rescans and the indicator comes down`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.loadSongs(context, SortValues.TITLE, SortOrders.ASCENDING)
        advanceUntilIdle()

        vm.refreshSongs(context, minIndicatorMs = 0)
        vm.isRefreshing shouldBe true
        advanceUntilIdle()

        sortOrders.size shouldBe 2
        vm.isRefreshing shouldBe false
    }

    @Test
    fun `a failed rescan still brings the indicator down, without crashing`() = runTest(dispatcher) {
        val vm = viewModel()
        queryError = SecurityException("storage permission revoked")

        vm.refreshSongs(context, minIndicatorMs = 0)
        advanceUntilIdle()

        vm.isRefreshing shouldBe false
    }
}
