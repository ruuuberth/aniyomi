# AGENTS.md

Aniyomi **anime-only fork** (branch `anime-only`, remote `ruuuberth/aniyomi`). Android anime client with an
mpv player. Kotlin, Jetpack Compose, Voyager navigation. Fork lineage: Mihon ← Tachiyomi. Multi-module Gradle,
JDK 17 (auto-provisioned via `gradle/gradle-daemon-jvm.properties` + foojay), compileSdk/targetSdk 36, minSdk 26.

**All manga functionality was removed (code, DB, UI, services). Do not reintroduce manga code.**

`README.md` is still upstream's and advertises the manga reader and manga trackers — it is not a spec of this
fork. This file plus the code is the source of truth.

## Git identity (hard rule)

- Commit as the GitHub account the CLI is authenticated as: `ruuuberth <77597261+ruuuberth@users.noreply.github.com>`.
  It is already set in this repo (`git config --local user.name` / `user.email`).
- Re-derive it from `gh` instead of hardcoding: `gh api user --jq '"\(.id)+\(.login)@users.noreply.github.com"'`
  (the account's real email is private, so use the `users.noreply.github.com` form).
- **Never copy another author's identity** — e.g. the author of the last commit on `main` — to get past git's
  "Author identity unknown". That publishes commits signed as somebody else and needs a history rewrite
  (`git rebase --exec "git commit --amend --no-edit --reset-author" main` + force-push + retagging).
- The config is repo-local: a fresh clone has no identity until you set it again. Verify with
  `git config --local --get user.email` before committing.

## Commands

CI order (`.github/workflows/build_push.yml`, "CI"): `spotlessCheck` → `assembleRelease -Penable-updater` →
`testReleaseUnitTest`. The PR workflow (`build_pull_request.yml`) is the same minus the updater flag.

```sh
./gradlew spotlessCheck        # ktlint 1.7.0 via Spotless; spotlessApply to fix
./gradlew assembleDebug        # fast local build
./gradlew testReleaseUnitTest  # unit tests (what CI runs)
./gradlew :domain:testReleaseUnitTest --tests "tachiyomi.domain.SomeTest"  # single test/module
./gradlew assembleRelease -Penable-updater          # release parity w/ CI updater builds
./gradlew assembleRelease -Pdisable-code-shrink     # release without R8 (much faster)
```

- R8 minification is ON by default for release (`Config.enableCodeShrink` = `!disable-code-shrink`); `assembleRelease`
  is slow without `-Pdisable-code-shrink`. Build-config flags map to `-P` properties: `enable-updater`,
  `disable-code-shrink`, `include-dependency-info` (`buildSrc/src/main/kotlin/mihon/buildlogic/BuildConfig.kt`).
- Build types: `debug`, `release`, `preview` (release-like, debug-signed, `.debug` appId suffix), `benchmark`.
- Unit tests are few: `app/src/test` (migration), `domain/src/test`, `core/common/src/test` (JUnit Platform +
  kotest/mockk, `useJUnitPlatform()`).
- **This workspace (Termux) has no Android SDK**, so `./gradlew` cannot run here: push to `anime-only` and let
  GitHub Actions verify. Fast loop: `Anime-only fast compile` (`:app:compileReleaseKotlin` + `assembleDebug`,
  ~10 min) then `CI` (spotless + release + tests, ~12 min). Inspect with
  `gh run list --branch anime-only` / `gh run view <id> --log-failed`.
- Format can be checked locally without Gradle: download `ktlint-cli` 1.7.0 and run
  `java -jar ktlint.jar --relative <files>` from the repo root (it reads `.editorconfig`).
  `app/src/main/java/eu/kanade/presentation/util/ExceptionFormatter.kt` reports one pre-existing
  `context-receiver-list-wrapping` violation that upstream CI tolerates — leave it alone.

## Architecture

Layering: `domain` (use cases + repository interfaces) → `data` (repo impls, SQLDelight) → `app` (screens, DI
wiring). Extension API lives in `source-api`; `presentation-core`/`presentation-widget` hold shared Compose UI.

- **Anime-only**: no `.manga` subpackages exist. Manga code, manga DB, readers, trackers, extension
  loaders/installers, widgets and backup models were deleted — don't recreate them. Anime naming is
  authoritative (`anime`, `episode`, `season`).
- **DI is Injekt** (not Hilt/Koin). Modules registered in `app/src/main/java/eu/kanade/tachiyomi/App.kt`:
  `AppModule`, `PreferenceModule`, `DomainModule` (in `eu/kanade/domain/`).
- **Navigation**: Voyager screens + ScreenModel ViewModels in `app/.../ui/<feature>/`; pure Compose UI in
  `app/.../presentation/<feature>/`. Exception: `PlayerActivity` (mpv via `AniyomiMPVView`) is a plain Activity.
- **Strings (moko-resources)**: edit ONLY the `base/` locale files. Shared strings → module `:i18n`
  (`MR.strings.*`); anime-only strings → module `:i18n-aniyomi` (`AYMR.strings.*`, import
  `tachiyomi.i18n.aniyomi.AYMR`). All other locales are Weblate-managed — never hand-edit. Legacy manga-named
  `MR.strings.*` keys still exist (e.g. `remove_manga`, `manga_download`, `manga_cover`) and are intentionally
  reused for anime UI — never rename or delete them.
- **Databases (SQLDelight)**: one database in `:data` — anime `AnimeDatabase` (package `tachiyomi.mi.data`,
  schema in `data/src/main/sqldelightanime/`, migrations in `data/src/main/sqldelightanime/migrations/*.sqm`,
  latest `138.sqm`). The manga `Database` (`data/src/main/sqldelight/`) is deleted; schema changes need a new
  numbered `.sqm`.
- **Native player libs** (mpv, ffmpeg-kit, TorrServer) are prebuilt JitPack artifacts from separate repos
  (e.g. `aniyomiorg/aniyomi-mpv-lib`) — not buildable from here.
- **Extensions** are runtime-loaded APKs from separate repos. Compatibility is gated by `SUPPORTED_LIB_VERSIONS`
  in `app/.../extension/anime/util/AnimeExtensionLoader.kt`.

## Conventions

- Gradle version catalogs are split across five TOMLs in `gradle/` (`libs`, `kotlinx`, `androidx`, `compose`,
  `aniyomi`); accessors are unqualified in .kts (`libs.x`, `compose.x`, `androidx.x`, `kotlinx.x`, `aniyomilibs.x`).
- `TYPESAFE_PROJECT_ACCESSORS` is on: reference modules as `projects.domain`, `projects.i18nAniyomi`, etc.
- ktlint rules live in `.editorconfig` (intellij_idea style, max line 120, trailing commas allowed, Composable
  naming exempt). A file holding a single top-level declaration must be named after it (e.g. `ArchiveAnime.kt`).
  Imports must be lexicographically ordered.
- `versionName`/`versionCode` live in `app/build.gradle.kts` `defaultConfig`; each release adds a `CHANGELOG.md`
  entry (sections: Added/Changed/Improved/Removed/Fixed/Other).

## Kotlin/Android gotchas hit in this fork

- `combine(a, b) { x, y -> … }`: after deleting one flow, a single-flow `combine(f) { … }` silently resolves to the
  vararg overload (`Array<T>`) → use the flow directly.
- `listOf(subclassA, subclassB, …)` infers `Any` (LUB) when the call sites need a common supertype — declare the
  type explicitly (`val trackers: List<Tracker> = listOf(...)`).
- `setClipToPadding` / `setClipChildren` are `ViewGroup` methods, not `View` ones (an `ImageView`/`PhotoView` has
  neither).
- ktlint `filename` rule: deleting declarations can leave a file whose name no longer matches its single
  remaining declaration → rename the file and fix the imports.

## Release process

- Bump `versionCode`/`versionName` in `app/build.gradle.kts` and add the `CHANGELOG.md` section, then tag
  `v<version>`; `.github/workflows/anime_only_release.yml` ("Anime-only release") builds the APKs, writes
  `checksums.txt`, and publishes the GitHub release with the changelog section of the tag.
- The `release` build type produces **unsigned** APKs (the signing key lives in upstream secrets), so the
  published artifacts are `preview` builds: debug-signed, `applicationId` `xyz.jmir.tachiyomi.mi.debug` → they
  install side by side with the normal app. APK names: `app/build/outputs/apk/preview/app-<abi>-preview.apk`.
- Re-running the release requires re-pushing the tag (`workflow_dispatch` skips the release steps: they are
  gated on `startsWith(github.ref, 'refs/tags/')`).
- The in-app updater points at `ruuuberth/aniyomi` (`AppUpdateChecker.kt`), not upstream — keep it that way, and
  keep `GetApplicationRelease.isNewVersion` tolerant of semver tags (it must not throw on non-numeric tags).

## Dependencies

- `flexible-adapter-core` is pinned to `3268efe4`, not upstream's `c8013533`: JitPack can no longer build
  `c8013533` (404 + failed build log), which breaks resolution for every build of this repo *and* of upstream
  `aniyomiorg/aniyomi`. `3268efe4` is the previous commit of the same 2.0.0-rc line with byte-identical sources.
  Do not "restore" the old pin; if JitPack purges `3268efe4` too, the download queue UI needs vendoring.

## Data-compatibility rules (anime-only fork)

Removing features must not break existing user data or old backups:

- **Backup proto field numbers are frozen**: never renumber or reuse fields in `Backup.kt` / `LegacyBackup.kt`
  (old anime backups must still restore). `BackupOptions` keeps its 12-field shape (positional array indices are
  used by `SettingsDataScreen`).
- **Stored preference keys are frozen**: legacy keys holding real user state keep their old names even if they
  mention manga — e.g. `ENTRY_NON_COMPLETED = "manga_ongoing"`, `ENTRY_HAS_UNVIEWED = "manga_fully_read"`,
  `ENTRY_NON_VIEWED = "manga_started"`, `ENTRY_OUTSIDE_RELEASE_PERIOD = "manga_outside_release_period"`,
  `pref_remove_bookmarked`, `pref_auto_clear_chapter_cache`, `pref_auto_update_manga_sync_key`,
  `pref_mangasync_username_*`/`pref_mangasync_password_*`, notification channel
  `new_chapters_episodes_channel` (`Notifications.CHANNEL_NEW_CHAPTERS_EPISODES`), and MAL's raw URL
  `users/@me/mangalist`.
- **Trackers shipped**: myAnimeList, aniList, kitsu, shikimori, bangumi, simkl, jellyfin.
- **Anime "chapters"**: video chapters in the player (`ChapterType` in `source-api`, `ChaptersSheet`, `AniSkipApi`)
  are a legitimate anime feature — keep them.
