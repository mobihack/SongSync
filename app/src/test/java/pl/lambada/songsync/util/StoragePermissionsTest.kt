package pl.lambada.songsync.util

import io.kotest.matchers.shouldBe
import org.junit.Test

class StoragePermissionsTest {

    @Test
    fun `android 11 uses all files access, like setup asks for`() {
        usesAllFilesAccess(sdkInt = 30) shouldBe true
    }

    @Test
    fun `newer versions use all files access`() {
        usesAllFilesAccess(sdkInt = 35) shouldBe true
    }

    @Test
    fun `android 10 and older use the storage permissions`() {
        usesAllFilesAccess(sdkInt = 29) shouldBe false
        usesAllFilesAccess(sdkInt = 24) shouldBe false
    }
}
