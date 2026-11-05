package pl.lambada.songsync.data.remote

import android.content.Context
import kotlinx.coroutines.flow.flow
import pl.lambada.songsync.data.remote.github.GithubAPI
import pl.lambada.songsync.domain.model.Release
import pl.lambada.songsync.util.ext.getVersion

class UpdateService {

    /**
     * Checks for updates by comparing the latest release version with the current version.
     * @param context The context of the application.
     * @return A flow emitting the update state.
     */
    fun checkForUpdates(context: Context) = flow {
        emit(UpdateState.Checking)

        try {
            val latest = GithubAPI.getLatestRelease()
            val isUpdate = isNewerRelease(context, latest)

            emit(
                if (isUpdate)
                    UpdateState.UpdateAvailable(latest)
                else
                    UpdateState.UpToDate
            )

        } catch (e: Exception) {
            emit(UpdateState.Error(e))
        }
    }

    /**
     * Checks if the latest release is newer than the current version.
     * @param context The context of the application.
     * @param latestRelease The latest release from the GitHub API.
     * @return True if the latest release is newer, false otherwise.
     */
    private fun isNewerRelease(context: Context, latestRelease: Release): Boolean =
        isNewerVersion(latest = latestRelease.tagName, current = context.getVersion())

    companion object {
        /**
         * Compares dotted versions part by part, so 4.3.10 is newer than 4.3.9 and older than 4.4.0.
         * A leading "v" and any suffix ("-beta", "+build") are ignored; missing parts count as 0.
         */
        fun isNewerVersion(latest: String, current: String): Boolean {
            val a = parts(latest)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        private fun parts(version: String): List<Int> = version
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .split('.')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
    }
}

/**
 * Defines the state of the update check.
 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class UpdateAvailable(val release: Release) : UpdateState
    data class Error(val reason: Throwable) : UpdateState
}