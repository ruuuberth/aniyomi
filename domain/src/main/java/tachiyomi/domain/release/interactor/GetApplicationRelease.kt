package tachiyomi.domain.release.interactor

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService
import java.time.Instant
import java.time.temporal.ChronoUnit

class GetApplicationRelease(
    private val service: ReleaseService,
    private val preferenceStore: PreferenceStore,
) {

    private val lastChecked: Preference<Long> by lazy {
        preferenceStore.getLong(Preference.appStateKey("last_app_check"), 0)
    }

    suspend fun await(arguments: Arguments): Result {
        val now = Instant.now()

        // Limit checks to once every 3 days at most
        if (!arguments.forceCheck &&
            now.isBefore(
                Instant.ofEpochMilli(lastChecked.get()).plus(3, ChronoUnit.DAYS),
            )
        ) {
            return Result.NoNewUpdate
        }

        val release = service.latest(arguments) ?: return Result.NoNewUpdate

        lastChecked.set(now.toEpochMilli())

        // Check if latest version is different from current version
        val isNewVersion = isNewVersion(
            arguments.isPreview,
            arguments.commitCount,
            arguments.versionName,
            release.version,
        )
        return when {
            isNewVersion -> Result.NewUpdate(release)
            else -> Result.NoNewUpdate
        }
    }

    private fun isNewVersion(
        isPreview: Boolean,
        commitCount: Int,
        versionName: String,
        versionTag: String,
    ): Boolean {
        // Removes prefixes like "r" or "v"
        val newVersion = versionTag.replace("[^\\d.]".toRegex(), "")
        val previewVersion = newVersion.toIntOrNull()
        return if (isPreview && previewVersion != null) {
            // Preview builds: based on releases tagged as something like "r1234"
            previewVersion > commitCount
        } else {
            // Release builds: based on releases tagged as something like "v0.1.2"
            val oldVersion = versionName.substringBefore('-').replace("[^\\d.]".toRegex(), "")

            val newSemVer = newVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val oldSemVer = oldVersion.split(".").map { it.toIntOrNull() ?: 0 }

            oldSemVer.mapIndexed { index, i ->
                if (newSemVer.getOrElse(index) { 0 } > i) {
                    return true
                }
            }

            false
        }
    }

    data class Arguments(
        val isPreview: Boolean,
        val commitCount: Int,
        val versionName: String,
        val repository: String,
        val forceCheck: Boolean = false,
    )

    sealed interface Result {
        data class NewUpdate(val release: Release) : Result
        data object NoNewUpdate : Result
        data object OsTooOld : Result
    }
}
