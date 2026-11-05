package pl.lambada.songsync.build

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.Test
import java.io.File

/** Release-build settings that keep private data out of the shipped app. */
class ReleaseConfigTest {

    @Test
    fun `release builds strip verbose, debug and info logging`() {
        // Those calls log track titles and file paths; checked in a release dex when added
        val rules = File("proguard-rules.pro").readText().replace(Regex("\\s+"), " ")
        rules shouldContain "-assumenosideeffects class android.util.Log {"
        listOf("v", "d", "i").forEach { rules shouldContain "public static int $it(...);" }
        rules shouldNotContain "public static int e(...);"
    }
}
