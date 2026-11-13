# AGENTS.md

Aniyomi (anime-only fork): Android anime client (mpv player). Kotlin, Jetpack Compose, Voyager navigation. Fork lineage: Mihon ← Tachiyomi. Multi-module Gradle, JDK 17 (auto-provisioned via `gradle/gradle-daemon-jvm.properties` + foojay), compileSdk/targetSdk 36, minSdk 26.

**This branch has all manga functionality removed (code, DB, UI, services). Do not reintroduce manga code.**

## Commands

CI order (`build_pull_request.yml`): `spotlessCheck` → `assembleRelease` → `testReleaseUnitTest`. Match it before declaring work done.

```sh
./gradlew spotlessCheck        # ktlint via Spotless; spotlessApply to fix
./gradlew assembleDebug        # fast local build
./gradlew testReleaseUnitTest  # unit tests (what CI runs)
./gradlew :domain:testReleaseUnitTest --tests "tachiyomi.domain.SomeTest"  # single test/module
./gradlew assembleRelease -Penable-updater          # release parity w/ CI updater builds
./gradlew assembleRelease -Pdisable-code-shrink     # release without R8 (much faster)
```

- R8 minification is ON by default for release (see `buildSrc/.../BuildConfig.kt`); `assembleRelease` is slow without `-Pdisable-code-shrink`.
- Build types: `debug`, `release`, `preview` (self-signed w/ debug key, `.debug` appId suffix — installable release-like build), `benchmark`.
- Unit tests are few: `app/src/test` (migration), `domain/src/test`, `core/common/src/test` (JUnit Platform, `useJUnitPlatform()`).
- Fast anime-only compile check: `.github/workflows/anime_only_compile.yml` (push to `anime-only` → `:app:compileReleaseKotlin` + `assembleDebug` + APK artifact).

## Architecture

Layering: `domain` (use cases + repository interfaces) → `data` (repo impls, SQLDelight) → `app` (screens, DI wiring). Extension API lives in `source-api`; `presentation-core`/`presentation-widget` hold shared Compose UI.

- **Anime-only**: no `.manga` subpackages exist anymore. Manga code, manga DB, manga readers, manga trackers, manga extension loaders/installers, manga widgets and manga backup models were deleted — don't recreate them. Anime naming is authoritative (`anime`, `episode`, `season`).
- **DI is Injekt** (not Hilt/Koin). Modules registered in `app/src/main/java/eu/kanade/tachiyomi/App.kt`: `AppModule`, `PreferenceModule`, `DomainModule` (in `eu/kanade/domain/`).
- **Navigation**: Voyager screens + ScreenModel ViewModels in `app/.../ui/<feature>/`; pure Compose UI in `app/.../presentation/<feature>/`. Exception: `PlayerActivity` (mpv via `AniyomiMPVView`) is a plain Activity.
- **Strings (moko-resources)**: edit ONLY the `base/` locale files. Shared strings → module `:i18n`, referenced in code as `MR.strings.*`; anime-only strings → module `:i18n-aniyomi`, referenced as `AYMR.strings.*` (import `tachiyomi.i18n.aniyomi.AYMR`). All other locales are Weblate-managed — never hand-edit. Legacy manga-named `MR.strings.*` keys still exist (e.g. `remove_manga`, `manga_download`, `manga_cover`) and are intentionally reused for anime UI — never rename or delete them.
- **Databases (SQLDelight)**: one database in `:data` — anime `AnimeDatabase` (`data/src/main/sqldelightanime`, package `tachiyomi.mi.data`). The manga `Database` (`data/src/main/sqldelight`) is deleted. Schema changes need a new numbered `.sqm` migration in the sibling `migrations/` dir; `.sq` files and generated code stay in-repo.
- **Native player libs** (mpv, ffmpeg-kit, TorrServer) are prebuilt JitPack artifacts built in separate repos (e.g. `aniyomiorg/aniyomi-mpv-lib`) — not buildable from here.
- **Extensions** are runtime-loaded APKs from separate repos. Extension compatibility is gated by `SUPPORTED_LIB_VERSIONS` in `app/.../extension/anime/util/AnimeExtensionLoader.kt` (bump when supporting a new extensions-lib).

## Conventions

- Gradle version catalogs are split across five TOMLs in `gradle/` (`libs`, `kotlinx`, `androidx`, `compose`, `aniyomi`); accessors are unqualified in .kts (`libs.x`, `compose.x`, `androidx.x`, `kotlinx.x`, `aniyomilibs.x`).
- `TYPESAFE_PROJECT_ACCESSORS` is on: reference modules as `projects.domain`, `projects.i18nAniyomi`, etc.
- ktlint rules live in `.editorconfig` (intellij_idea style, max line 120, trailing commas allowed; Composable naming exempt). Build-config quirk: `Config.*` flags map to `-P` properties (`enable-updater`, `disable-code-shrink`, `include-dependency-info`).
- `versionName`/`versionCode` live in `app/build.gradle.kts` `defaultConfig`; each release adds a `CHANGELOG.md` entry (sections: Added/Changed/Improved/Removed/Fixed/Other).

## Data-compatibility rules (anime-only fork)

Removing features must not break existing user data or old backups:

- **Backup proto field numbers are frozen**: never renumber or reuse fields in `Backup.kt` / `LegacyBackup.kt` (old anime backups must still restore). `BackupOptions` keeps its 12-field shape (positional array indices are used by `SettingsDataScreen`).
- **Stored preference keys are frozen**: legacy keys holding real user state keep their old names even if they mention manga — e.g. `ENTRY_NON_COMPLETED = "manga_ongoing"`, `ENTRY_HAS_UNVIEWED = "manga_fully_read"`, `ENTRY_NON_VIEWED = "manga_started"`, `ENTRY_OUTSIDE_RELEASE_PERIOD = "manga_outside_release_period"`, `pref_remove_bookmarked`, `pref_auto_clear_chapter_cache`, `pref_auto_update_manga_sync_key`, `pref_mangasync_username_*`/`pref_mangasync_password_*`, notification channel `new_chapters_episodes_channel` (`Notifications.CHANNEL_NEW_CHAPTERS_EPISODES`), and MAL's raw URL `users/@me/mangalist`.
- **Trackers shipped**: myAnimeList, aniList, kitsu, shikimori, bangumi, simkl, jellyfin.
- **Anime "chapters"**: video chapters in the player (`ChapterType` in `source-api`, `ChaptersSheet`, `AniSkipApi`) are a legitimate anime feature — keep them.
