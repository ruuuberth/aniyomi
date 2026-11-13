package mihon.core.migration.migrations

import mihon.core.migration.Migration

val migrations: List<Migration>
    get() = listOf(
        SetupBackupCreateMigration(),
        SetupAnimeLibraryUpdateMigration(),
        CoverToExternalFileMigration(),
        MigrateToTriStateMigration(),
        ForceMALLogOutMigration(),
        DOHMigration(),
        ResetRotationMigration(),
        SetupBackgroundTasksMigration(),
        AddAllLangMigration(),
        CombineUpdateRestrictionMigration(),
        MigrateSecureScreenMigration(),
        RenameEnumMigration(),
        EnableAutoBackupMigration(),
        SplitPreferencesMigration(),
        PlayerPreferenceMigration(),
        MovePlayerPreferencesMigration(),
        UseWorkManagerMigration(),
        RemoveBackgroundJobsMigration(),
        MigrateTriStateMigration(),
        DontRunJobsMigration(),
        RelativeTimestampMigration(),
        NoAppStateMigration(),
        PrivatePreferenceMigration(),
        PermaTrustExtensionsMigration(),
        NavigationOptionsMigration(),
        LogOutMALMigration(),
        EnumsMigration(),
        TrustExtensionRepositoryMigration(),
        VideoPlayerPreferenceMigration(),
        VideoOrientationMigration(),
        PrefLangMigration(),
    )
