package pl.lambada.songsync.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.lambada.songsync.data.remote.UpdateService.Companion.isNewerVersion

class UpdateServiceTest {
    @Test
    fun `a higher patch, minor or major is newer`() {
        assertTrue(isNewerVersion(latest = "v4.3.4", current = "4.3.3"))
        assertTrue(isNewerVersion(latest = "v4.4.0", current = "4.3.3"))
        assertTrue(isNewerVersion(latest = "v5.0.0", current = "4.3.3"))
    }

    @Test
    fun `the same version is not newer`() {
        assertFalse(isNewerVersion(latest = "v4.3.3", current = "4.3.3"))
    }

    @Test
    fun `parts are compared as numbers, not digits glued together`() {
        assertTrue(isNewerVersion(latest = "v4.3.10", current = "4.3.9"))
        assertFalse(isNewerVersion(latest = "v4.3.10", current = "4.4.0"))
    }

    @Test
    fun `an older release is not newer`() {
        assertFalse(isNewerVersion(latest = "v4.3.2", current = "4.3.3"))
    }

    @Test
    fun `suffixes and missing parts don't break the comparison`() {
        assertTrue(isNewerVersion(latest = "v4.3.4-beta", current = "4.3.3"))
        assertFalse(isNewerVersion(latest = "v4.3", current = "4.3.0"))
        assertTrue(isNewerVersion(latest = "4.4", current = "4.3.9"))
    }
}
