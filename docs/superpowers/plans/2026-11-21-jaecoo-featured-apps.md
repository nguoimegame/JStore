# Ứng dụng cho Jaecoo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A "Ứng dụng cho Jaecoo" cluster on Apps › For you, built from the Jaecoo server (`apps.json` + `featured.json`) merged with Google Play. Server fields win over Play. Apps with a server `downloadUrl` download, install and update from the server.

**Architecture:** Pure merge logic (`ServerApps`) turns server entries + Play `App`s into gplayapi `App`s whose `fileList` points at the server, the way `SelfUpdate.toApp` does. A Hilt singleton repository (`JaecooServerApps`) loads and holds the result. Aurora's own pipeline then does the rest: the details screen, `DownloadWorker` (which skips `purchase()` when `fileList` is non-empty) and `UpdateWorker`. Each upstream file gets a small `// jstore:` hook.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, WorkManager, gplayapi, org.json, JUnit4 + Truth.

**Spec:** `docs/superpowers/specs/2026-11-21-jaecoo-featured-apps-design.md`

**Branch:** work on `feat/jaecoo-featured` cut from `jstore`, not on `jstore` itself.

## Global Constraints

- New code goes in `com.aurora.store.jstore` (UI in `com.aurora.store.jstore.ui`). Upstream edits stay minimal and are marked `// jstore: ...`.
- Every new string goes into all 11 `values*/strings_jstore.xml` files: default, `ar`, `es`, `fr`, `in`, `kk`, `pt`, `ru`, `th`, `vi`, `zh`.
- Log with `JstoreLog`, never `Log`, in jstore code.
- Touch targets ≥ 64dp (`@dimen/jstore_touch_min`). The cluster reuses `JstoreClusterCard` + `JstoreAppTile`.
- Server file: `<server>/api/featured.json`, same schema as `apps.json`; only `packageName` is required.
- Server: the base URL in `cacheDir/jstore_store/server.txt` (written by `StoreCatalog.FileCatalogCache`). No cache → no cluster, no server ownership.
- Versions are compared by name only, with `ManageUpdates.isVersionNewer`; there is no server `versionCode`.
- A file that comes from the Jaecoo server may lack `sha256`. In that case it is accepted unverified. Play files are always verified.
- Ownership: every `apps.json` package is server-owned. A `featured.json` entry is server-owned only if it has a `downloadUrl`. Server-owned packages never take a Play update.
- The cluster appears on the Apps tab only (`pageType == 0`), never on Games.
- Before finishing: `./gradlew.bat ktlintCheck testVanillaDebugUnitTest` passes.

## Review Focus

1. **Server unreachable at For-you load.** The Play clusters render exactly as before, and the Jaecoo cluster uses the cached files or is absent. It must never be a spinner or crash. Test in Task 3.
2. **App just updated from the server.** It must not show "Update" again (Play versionCode > installed, or synthetic +1 loop). Tests in Tasks 1 and 7.
3. **`featured.json` entry with only `packageName` and no `name`.** The cluster shows Play's name, never "Unknown App". Test in Task 1.
4. **Server-only app opened from Downloads/Updates/Installed.** The details page shows server data and must not show an error. Test in Task 5.
5. **Play session missing or expired while building the cluster.** Server-only apps with a `downloadUrl` still show, and Play-only entries drop. Test in Task 1.

---

## File Structure

| File | Responsibility |
|---|---|
| Create `jstore/featured/ServerApps.kt` | Pure logic: merge lists, ownership, versionCode, `CatalogApp` + Play `App` → `App` |
| Create `jstore/featured/FeaturedList.kt` | Fetch + cache `featured.json` for a base URL |
| Create `jstore/featured/JaecooServerApps.kt` | `@Singleton` repository: loads server data, Play lookup, holds the merged list and lookups |
| Create `jstore/ui/JaecooFeaturedViewModel.kt` | Exposes the cluster list to Compose |
| Create `jstore/ui/JstoreServerCluster.kt` | The cluster composable (card + tile row) |
| Modify `jstore/ui/store/StoreCatalog.kt` | `parse` gets a `defaultName` parameter |
| Modify `compose/composable/StreamCarousel.kt` | Slot after hero |
| Modify `compose/ui/apps/ForYouPage.kt` | Feed the slot on the Apps tab |
| Modify `viewmodel/details/AppDetailsViewModel.kt` | Server overrides + server-only fallback |
| Modify `data/work/DownloadWorker.kt` | Accept hashless server files |
| Modify `data/work/UpdateWorker.kt` | Server-owned updates (phase 2) |
| Modify `jstore/ui/store/ManageUpdates.kt`, `StoreScreen.kt` | Embedded manager reads `featured.json` too (phase 2) |

Paths are relative to `app/src/main/java/com/aurora/store/`. Tests mirror them under `app/src/test/java/com/aurora/store/`.

---

## Phase 1 — cluster, details, install

### Task 1: `ServerApps` merge logic

**Files:**
- Modify: `jstore/ui/store/StoreCatalog.kt` (`parse`)
- Create: `jstore/featured/ServerApps.kt`
- Test: `jstore/featured/ServerAppsTest.kt`

**Interfaces:**
- Produces:
  - `StoreCatalog.parse(json: String, language: String, defaultName: String = "Unknown App"): List<CatalogApp>`. The default keeps `StoreScreen` unchanged.
  - `data class ServerEntry(val app: StoreCatalog.CatalogApp, val fromCatalogue: Boolean)`
  - `data class Installed(val versionName: String, val versionCode: Long, val certHashes: List<String>)`
  - `object ServerApps`:
    - `fun entries(catalogue: List<CatalogApp>, featured: List<CatalogApp>): List<ServerEntry>`: catalogue first, then featured. Drops blank `packageName`. Dedupes by package, and the first occurrence wins.
    - `fun isServerOwned(entry: ServerEntry): Boolean`: `fromCatalogue || app.downloadUrl.isNotEmpty()`.
    - `fun versionCode(serverVersion: String, playVersionCode: Long?, installed: Installed?): Long`
    - `fun toApp(entry: ServerEntry, baseUrl: String, play: App?, installed: Installed?): App?`
    - `fun assemble(entries: List<ServerEntry>, baseUrl: String, play: Map<String, App>, installed: (String) -> Installed?): List<App>`: keeps the order of `entries` and drops nulls.

**Merge rules for `toApp`, from spec 5.3:**
- Returns null when `play == null && downloadUrl.isEmpty()`.
- Field overrides (a blank server value means take Play's):
  - `name` → `displayName`
  - resolved `iconUrl` → `iconArtwork.url`
  - `description` → `shortDescription` and `description`
  - `version` → `versionName`
  - `StoreOps.parseEstimatedBytes(fileSize)` → `size`
- When `downloadUrl` is non-empty:
  - `fileList = listOf(PlayFile(name = "<pkg>.apk", url = resolveUrl(baseUrl, downloadUrl), size, sha256 = app.sha256))`
  - `versionCode = versionCode(...)`
  - if `installed != null`, `certificateSetList` = `installed.certHashes` mapped to `EncodedCertificateSet(certificateSet = it, sha256 = "")`, as in `SelfUpdate.toApp`
- When `downloadUrl` is empty: `fileList`, `versionCode` and certs are Play's untouched.
- `isInstalled = installed != null`.
- Server-only apps start from `App(packageName = pkg)`.

**`versionCode` rules:**
- Not installed: `playVersionCode ?: 1`.
- Installed and `isVersionNewer(serverVersion, installed.versionName)`: `installed.versionCode + 1`.
- Otherwise: `installed.versionCode`.

- [ ] **Step 1: Write failing tests** in `ServerAppsTest`, building `CatalogApp`s with a small helper `entry(pkg, name = "", downloadUrl = "", version = "", sha256 = "", iconUrl = "")`:
  - `entriesPutCatalogueFirstAndDedupe`: catalogue `[a, b]` + featured `[b, c, ""]` → packages `[a, b, c]`, and `b.fromCatalogue == true`.
  - `ownership`: catalogue entry without URL → true; featured with URL → true; featured without URL → false.
  - `versionCodeNotInstalled`: `versionCode("2.0", 50, null) == 50`, `versionCode("2.0", null, null) == 1`.
  - `versionCodeNewerOnServer`: installed `("1.9", 40)` → 41.
  - `versionCodeNotNewer`: installed `("2.0", 40)` with server `"2.0"` → 40. This pins Review Focus 2: no loop.
  - `serverFieldsWinPlayFillsGaps`: Play has `displayName "Waze Play"`, `versionName "5.0"`; server has `name "Waze HU"`, `version ""` → `displayName == "Waze HU"`, `versionName == "5.0"`.
  - `nameOnlyEntryKeepsPlayName`: parse a `featured.json` with `[{"packageName":"com.spotify.music"}]` using `defaultName = ""`, with Play present → `displayName` is Play's. This pins Review Focus 3.
  - `downloadUrlReplacesFileList`: base `"https://s"`, URL `"/apk/w.apk"`, sha `"ab"` → one `PlayFile` with url `"https://s/apk/w.apk"` and sha256 `"ab"`.
  - `hashlessDownloadStillProducesFile`: sha `""` → file present and sha256 `""`.
  - `dropsWhenNoPlayAndNoUrl`: `toApp(..., play = null, ...) == null` when there is no URL.
  - `serverOnlyWithoutPlay`: Play map empty and entry has a URL → app present with the server name. This pins Review Focus 5.
  - `assembleKeepsOrder`.

- [ ] **Step 2: Run, expect FAIL** (unresolved references)
  Run: `./gradlew.bat testVanillaDebugUnitTest --tests "com.aurora.store.jstore.featured.ServerAppsTest"`

- [ ] **Step 3: Implement** the `defaultName` parameter in `StoreCatalog.parse` and everything in `ServerApps.kt` per the Interfaces and rules above.

- [ ] **Step 4: Run, expect PASS** with the same command, plus `--tests "*StoreCatalog*"` to confirm nothing regressed.

- [ ] **Step 5: Commit** `feat(featured): merge Jaecoo server entries with Play apps`

### Task 2: `FeaturedList` loader

**Files:**
- Create: `jstore/featured/FeaturedList.kt`
- Test: `jstore/featured/FeaturedListTest.kt`

**Interfaces:**
- Produces: `class FeaturedList(fetch: (String) -> String?, cacheFile: File)` with `fun load(baseUrl: String, language: String): List<CatalogApp>`.
  - Behaviour: GET `"$baseUrl/api/featured.json"`. If it parses (via `StoreCatalog.parse(..., defaultName = "")`), write the body to `cacheFile` and return it.
  - On a fetch failure, an `IllegalArgumentException`, or a `JSONException`, fall back to parsing `cacheFile`. If that fails too, return `emptyList()`.
  - Cache file: `File(context.cacheDir, "jstore_store/featured.json")`.

- [ ] **Step 1: Failing tests** (`TemporaryFolder`, as in `StoreCatalogTest`):
  - `fetchesFeaturedPathAndCaches`: the requested URL equals `"https://a/api/featured.json"` and the cache file is written.
  - `brokenJsonFallsBackToCache`
  - `missingEverythingIsEmpty`
- [ ] **Step 2: Run, expect FAIL**: `--tests "com.aurora.store.jstore.featured.FeaturedListTest"`
- [ ] **Step 3: Implement** `FeaturedList`.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** `feat(featured): load featured.json with cache fallback`

### Task 3: `JaecooServerApps` repository

**Files:**
- Create: `jstore/featured/JaecooServerApps.kt`
- Test: `jstore/featured/JaecooServerAppsTest.kt`

**Interfaces:**
- Consumes: `StoreCatalog`, `StoreCatalog.FileCatalogCache`, `FeaturedList`, `ServerApps.*`, and `PackageUtil` for installed info.
- Produces: `@Singleton class JaecooServerApps @Inject constructor(@ApplicationContext context, httpClient: HttpClient, appDetailsHelper: Provider<AppDetailsHelper>)`:
  - `val apps: StateFlow<List<App>>`: the cluster list, empty until loaded.
  - `suspend fun refresh()`: runs on `Dispatchers.IO` and never throws.
  - `suspend fun ensureLoaded()`: calls `refresh()` once per process, then returns immediately.
  - `fun entry(packageName: String): ServerEntry?`
  - `fun ownedPackages(): Set<String>`: packages where `isServerOwned` holds.
  - `fun app(packageName: String): App?`: the merged app from the last load, including owned apps not in the cluster.
  - `fun isServerFileUrl(url: String): Boolean`: true when the URL equals a resolved `downloadUrl` of a loaded entry.
- **Load order:**
  1. Read `cache.read()` from `FileCatalogCache(File(cacheDir, "jstore_store"))`. If null, publish an empty list and stop.
  2. Load the catalogue with `StoreCatalog(fetch, cache).load(listOf(baseUrl), language)`.
  3. Load `FeaturedList.load(catalog.baseUrl, language)`.
  4. Build `ServerApps.entries(...)`.
  5. Play lookup: `appDetailsHelper.get().getAppByPackageName(pkgs)`. On any exception use an empty map and log `JstoreLog.w`.
  6. Run `ServerApps.assemble(...)`. Installed info comes from `PackageUtil.getPackageInfo` + `CertUtil.getEncodedCertificateHashes`, or null on `NameNotFoundException`.
- To keep it testable, put steps 2–6 in an `internal fun build(fetch: (String) -> String?, cache: StoreCatalog.CatalogCache, featuredCache: File, language: String, play: (List<String>) -> List<App>, installed: (String) -> Installed?): Snapshot` that `refresh()` calls. `Snapshot(entries, apps, baseUrl)`.

- [ ] **Step 1: Failing tests** on `build`:
  - `serverDownUsesCachedCatalogue`: fetch always null and cache holds `apps.json` → apps come from the cache. This pins Review Focus 1.
  - `noCacheNoServerIsEmpty`
  - `playThrowsKeepsServerOnlyApps`: the `play` lambda throws → apps with a URL are kept and the others drop.
  - `isServerFileUrlMatchesResolvedUrl`
- [ ] **Step 2: Run, expect FAIL**: `--tests "com.aurora.store.jstore.featured.JaecooServerAppsTest"`
- [ ] **Step 3: Implement** the repository and `build`.
- [ ] **Step 4: Run, expect PASS.** Then run `./gradlew.bat :app:assembleVanillaDebug` and confirm Hilt resolves `Provider<AppDetailsHelper>`.
- [ ] **Step 5: Commit** `feat(featured): repository for Jaecoo server apps`

### Task 4: Cluster on Apps › For you

**Files:**
- Create: `jstore/ui/JaecooFeaturedViewModel.kt`, `jstore/ui/JstoreServerCluster.kt`
- Modify: `compose/composable/StreamCarousel.kt:45-56,121-135`, `compose/ui/apps/ForYouPage.kt:22-49`
- Modify: the 11 `res/values*/strings_jstore.xml`

**Interfaces:**
- Consumes: `JaecooServerApps.apps` and `ensureLoaded()`.
- Produces:
  - `@HiltViewModel class JaecooFeaturedViewModel @Inject constructor(repo: JaecooServerApps) : ViewModel`. It has `val apps: StateFlow<List<App>>`, and `init` launches `repo.ensureLoaded()`.
  - `@Composable fun JstoreServerCluster(modifier: Modifier, apps: List<App>, onAppClick: (App) -> Unit)`: `JstoreClusterCard(title = stringResource(R.string.jstore_featured_title), onSeeAll = null)` around a `LazyRow` of `JstoreAppTile`, with the same padding and spacing as `ClusterRow`. Draws nothing when `apps` is empty.
  - `StreamCarousel` gets a new trailing parameter, `jstoreTopApps: List<App> = emptyList()` with a `// jstore:` comment. When non-empty and the multi-cluster branch is taken, add `item(key = "jstore_server_cluster")` right after the `jstore_hero` item.
  - `ForYouContent`: when `pageType == 0`, collect `hiltViewModel<JaecooFeaturedViewModel>().apps` and pass it in. The Games tab passes nothing.
- **Strings:** `jstore_featured_title`:
  - default "Apps for Jaecoo"
  - vi "Ứng dụng cho Jaecoo"
  - es "Apps para Jaecoo"
  - fr "Applis pour Jaecoo"
  - pt "Apps para Jaecoo"
  - ru "Приложения для Jaecoo"
  - ar "تطبيقات لـ Jaecoo"
  - in "Aplikasi untuk Jaecoo"
  - kk "Jaecoo қолданбалары"
  - th "แอปสำหรับ Jaecoo"
  - zh "Jaecoo 专属应用"

- [ ] **Step 1: Implement** the ViewModel, composable, carousel slot, ForYou wiring and strings.
- [ ] **Step 2: Verify** with `./gradlew.bat ktlintCheck testVanillaDebugUnitTest`. Expect BUILD SUCCESSFUL.
- [ ] **Step 3: Emulator check** with skill `run-jstore`. Open the Kho ứng dụng tab once so the cache exists, then open Apps › For you.
  - Expect: cluster "Ứng dụng cho Jaecoo" sits between the WhatsApp hero and "Ứng dụng phổ biến"; Jaecoo-store apps come first.
  - Games › For you has no cluster.
  - With the network to the server blocked, the Play clusters still render.
- [ ] **Step 4: Commit** `feat(featured): Apps for Jaecoo cluster on For you`

### Task 5: Details screen uses server data

**Files:**
- Modify: `viewmodel/details/AppDetailsViewModel.kt:201-249`
- Create: `jstore/featured/ServerDetails.kt`
- Test: `jstore/featured/ServerDetailsTest.kt`

**Interfaces:**
- Consumes: `JaecooServerApps.entry`, `JaecooServerApps.app`, `ensureLoaded`, `ServerApps.toApp`.
- Produces: `object ServerDetails { fun resolve(play: Result<App>, server: App?): App? }`.
  - Play succeeded with a non-blank `displayName` and server is non-null → apply the server overrides. Use `server` for every overridden field; take everything else (screenshots, rating, etc.) from Play.
  - Play succeeded and server is null → Play.
  - Play failed or blank and server is non-null → `server`.
  - Both missing → null.
- **Hook** in `fetchAppDetails`, marked `// jstore:`:
  - Inject `JaecooServerApps`, call `ensureLoaded()`, wrap the Play call in `runCatching`, and set `_app` from `ServerDetails.resolve`.
  - Null result → the existing error path. Keep the `AuthException` branch: rethrow it from the `runCatching` when there is no server app.
  - For a server-only app, skip the Play-only follow-ups in `invokeOnCompletion`: reviews, data safety, suggestions, exodus, plexus. Keep `fetchFavourite`.

- [ ] **Step 1: Failing tests** for the four `resolve` cases. One of them, `playFailsServerOnly`, pins Review Focus 4.
- [ ] **Step 2: Run, expect FAIL**: `--tests "com.aurora.store.jstore.featured.ServerDetailsTest"`
- [ ] **Step 3: Implement** `ServerDetails` and the hook.
- [ ] **Step 4: Run, expect PASS**, then `./gradlew.bat ktlintCheck testVanillaDebugUnitTest`.
- [ ] **Step 5: Commit** `feat(featured): details screen prefers Jaecoo server data`

### Task 6: Download and install from the server

**Files:**
- Modify: `data/work/DownloadWorker.kt:660-671`
- Create: `jstore/featured/ServerFiles.kt`
- Test: `jstore/featured/ServerFilesTest.kt`

**Interfaces:**
- Consumes: `JaecooServerApps.isServerFileUrl`.
- Produces: `object ServerFiles { fun acceptUnverified(file: PlayFile, isServerUrl: (String) -> Boolean): Boolean }`. It returns true iff `sha256` and `sha1` are both blank and `isServerUrl(file.url)`.
- **Hook** in `verifyFile`, marked `// jstore:`: when `expectedSha.isBlank()` and `ServerFiles.acceptUnverified(...)`, log `JstoreLog.w(TAG, "Installing ${gFile.name} from the Jaecoo server without a hash")` and return true. Inject `JaecooServerApps` into the worker the way its other dependencies are injected.

- [ ] **Step 1: Failing tests**:
  - `hashlessServerFileAccepted`
  - `hashlessPlayFileRejected`
  - `fileWithHashNotShortCircuited`: a sha present → false, so the normal check runs.
- [ ] **Step 2: Run, expect FAIL**: `--tests "com.aurora.store.jstore.featured.ServerFilesTest"`
- [ ] **Step 3: Implement** `ServerFiles` and the hook.
- [ ] **Step 4: Run, expect PASS**, then `./gradlew.bat ktlintCheck testVanillaDebugUnitTest`.
- [ ] **Step 5: Emulator check** (`run-jstore`): tap a cluster app that has a server `downloadUrl` and press Install.
  - The Downloads tab shows progress, the install goes through `Installer.JAECOO`, and the button changes to Open.
  - `adb logcat` shows a request to the server URL and none to Play's delivery.
- [ ] **Step 6: Commit** `feat(featured): install Jaecoo server apps through the download queue`

---

## Phase 2 — updates from the server

### Task 7: `UpdateWorker` uses the server for server-owned packages

**Files:**
- Modify: `data/work/UpdateWorker.kt:194-261`
- Create: `jstore/featured/ServerUpdates.kt`
- Test: `jstore/featured/ServerUpdatesTest.kt`

**Interfaces:**
- Consumes: `JaecooServerApps.refresh`, `ownedPackages`, `app`.
- Produces: `object ServerUpdates`:
  - `fun forPlay(packages: List<String>, owned: Set<String>): List<String>`: packages minus owned.
  - `fun updates(owned: Set<String>, appOf: (String) -> App?, installed: (String) -> Installed?): List<App>`: includes an owned package iff it is installed, `appOf(pkg)` has a non-empty `fileList`, and `appOf(pkg).versionCode > installed.versionCode`.
- **Hook**, marked `// jstore:`:
  - Before the Play lookup, call `jaecooServerApps.refresh()` and filter `filteredPackages` through `forPlay`.
  - After the self-update block, add `ServerUpdates.updates(...)` to `updates`.
  - Blacklisted packages are not added, so filter owned packages through `blacklistProvider`. Ignored updates are already filtered downstream by `isIgnoredBy`.
  - Owned packages skip the `isAuroraOnlyFilterEnabled` / installer filters.

- [ ] **Step 1: Failing tests**:
  - `ownedPackagesNotSentToPlay`
  - `newerServerVersionIsUpdate`
  - `sameVersionIsNotUpdate`: installed 40 and merged app versionCode 40 → no update. This pins Review Focus 2.
  - `ownedWithoutDownloadUrlNoUpdate`
  - `notInstalledNoUpdate`
- [ ] **Step 2: Run, expect FAIL**: `--tests "com.aurora.store.jstore.featured.ServerUpdatesTest"`
- [ ] **Step 3: Implement** `ServerUpdates` and the hook.
- [ ] **Step 4: Run, expect PASS**, then `./gradlew.bat ktlintCheck testVanillaDebugUnitTest`.
- [ ] **Step 5: Emulator check:**
  - Install an older APK of a server app.
  - In the Updates tab, tap refresh → one update appears; tap it → it installs from the server.
  - After the install, refresh again → no update.
- [ ] **Step 6: Commit** `feat(featured): updates for server-owned apps come from the Jaecoo server`

### Task 8: The embedded app manager reads `featured.json`

**Files:**
- Modify: `jstore/ui/store/StoreScreen.kt:233-263` (`loadCatalog`) and `jstore/ui/store/ManageUpdates.kt`
- Test: `jstore/ui/store/ManageUpdatesTest.kt`

**Interfaces:**
- Consumes: `FeaturedList`, `ServerApps.entries`.
- Produces:
  - `ManageUpdates.withFeatured(catalog: StoreCatalog.Catalog?, featured: List<CatalogApp>): StoreCatalog.Catalog?`: appends the featured entries that have a `downloadUrl` and whose package is not already in the catalogue. Returns null when `catalog` is null.
  - `StoreScreen` keeps the plain catalogue for its own Kho list. It loads `FeaturedList` on the same thread after the catalogue, and `catalogOf` returns `withFeatured(catalog, featured)`.

- [ ] **Step 1: Failing tests** in `ManageUpdatesTest`:
  - `withFeaturedAddsDownloadableOnly`
  - `withFeaturedCatalogueWins`
  - `pickUsesFeaturedServerVersion`: an installed featured package with a newer server version gives `source == Source.JAECOO`, and the `play` lambda is not asked for it.
- [ ] **Step 2: Run, expect FAIL**: `--tests "com.aurora.store.jstore.ui.store.ManageUpdatesTest"`
- [ ] **Step 3: Implement** `withFeatured` and the `StoreScreen` wiring.
- [ ] **Step 4: Run, expect PASS**, then `./gradlew.bat ktlintCheck testVanillaDebugUnitTest`.
- [ ] **Step 5: Commit** `feat(featured): embedded app manager updates featured apps from the server`

### Task 9: Docs and final check

**Files:** `docs/jstore-contract.md` (if it describes server files), `CLAUDE.md` only if a rule changed (none expected).

- [ ] **Step 1:** Document `featured.json` (schema, ownership rule, optional `sha256`) where `apps.json` is described. If it is described nowhere in this repo, add a short section to the spec instead.
- [ ] **Step 2:** Run `./gradlew.bat ktlintCheck testVanillaDebugUnitTest`. Expect BUILD SUCCESSFUL.
- [ ] **Step 3:** Commit `docs: featured.json on the Jaecoo server`
