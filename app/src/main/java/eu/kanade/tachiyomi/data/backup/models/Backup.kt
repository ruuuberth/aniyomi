package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class LegacyBackup(
    @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(3) val backupAnime: List<BackupAnime> = emptyList(),
    @ProtoNumber(4) var backupAnimeCategories: List<BackupCategory> = emptyList(),
    // @ProtoNumber(102) var backupBrokenAnimeSources, legacy source model with non-compliant proto number,
    @ProtoNumber(103) var backupAnimeSources: List<BackupAnimeSource> = emptyList(),
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) var backupExtensions: List<BackupExtension> = emptyList(),
    @ProtoNumber(107) var backupAnimeExtensionStores: List<BackupExtensionStore> = emptyList(),
    @ProtoNumber(109) var backupCustomButton: List<BackupCustomButtons> = emptyList(),
) {
    fun toBackup(): Backup {
        return Backup(
            backupCategories = backupCategories,
            backupPreferences = backupPreferences,
            backupSourcePreferences = backupSourcePreferences,

            isLegacy = false, // Only used for detection
            backupAnime = backupAnime,
            backupAnimeCategories = backupAnimeCategories,
            backupAnimeSources = backupAnimeSources,
            backupExtensions = backupExtensions,
            backupAnimeExtensionStores = backupAnimeExtensionStores,
            backupCustomButton = backupCustomButton,
        )
    }
}

@Serializable
data class Backup(
    @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),

    // Aniyomi specific values
    @ProtoNumber(500) val isLegacy: Boolean = true,
    @ProtoNumber(501) val backupAnime: List<BackupAnime> = emptyList(),
    @ProtoNumber(502) var backupAnimeCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(503) var backupAnimeSources: List<BackupAnimeSource> = emptyList(),
    @ProtoNumber(504) var backupExtensions: List<BackupExtension> = emptyList(),
    @ProtoNumber(505) var backupAnimeExtensionStores: List<BackupExtensionStore> = emptyList(),
    @ProtoNumber(506) var backupCustomButton: List<BackupCustomButtons> = emptyList(),
)
