# jstore UI Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve all 9 UI and ergonomics issues identified across jstore (translations, dark mode contrast, dimension ratios, hero deduplication, card list styling, TopAppBar touch sizes, details screen buttons, shimmer layout shift, and onboarding layout on portrait head units).

**Architecture:** Maintain strict segregation of jstore-specific code. Reusable composables and extensions reside in `com.aurora.store.jstore.ui` and `*_jstore.xml`. Minimal hooks in upstream files are labeled with `// jstore:` comments. Retain existing architectural patterns (Compose Material3 Expressive, Hilt, Coil3).

**Tech Stack:** Kotlin, Jetpack Compose Material3 (Expressive), Android Resources, JUnit4 + Google Truth.

**Spec:** [docs/superpowers/specs/2026-10-27-jstore-headunit-ui-design.md](file:///d:/WorkSpace/AndroidStudioProjects/JStore/docs/superpowers/specs/2026-10-27-jstore-headunit-ui-design.md).

## Global Constraints

- Every modification in upstream files (outside `com.aurora.store.jstore` and `*_jstore.xml`) must include a `// jstore:` comment.
- New public functions or modified function signatures in upstream files must only append parameters with default values.
- New strings belong in `res/values/strings_jstore.xml` and localized variants in `res/values-*/strings_jstore.xml`.
- Colors must be sourced from `MaterialTheme.colorScheme` or `JstoreTheme`, typography from `MaterialTheme.typography`, dimensions from `dimens_jstore.xml`. No hardcoded dp/sp values.
- All new Kotlin files must include the SPDX Apache/GPL license header.
- Verification must pass: `./gradlew.bat :app:assembleVanillaDebug` and `./gradlew.bat :app:testVanillaDebugUnitTest`.

## Review Focus

1. **Dark Mode Visibility:** Selected tab in `JstoreSegmentedTabs` must have clear visual distinction and higher luminance than the track in dark mode.
2. **First Cluster Deduplication:** When `showHero` is active, the hero app must not be duplicated as the first item in the first cluster.
3. **Button Text Truncation in Details:** Long button labels ("Manual download", "Tải thủ công") must not truncate with ellipsis on standard automotive portrait screens.
4. **Onboarding Portrait Layout:** On portrait automotive screens (width ~853dp), onboarding should not squeeze into a 2-column layout with half the screen taken by a logo.
5. **Shimmer Card Alignment:** Loading shimmer sections must visually match the size, margins, and card outlines of loaded clusters.

---

### Task 1: Localization & String Resources (Issue 1)

**Files:**
- Modify: `app/src/main/res/values-vi/strings_jstore.xml`
- Modify: `app/src/main/res/values-zh/strings_jstore.xml`
- Modify: `app/src/main/res/values-ru/strings_jstore.xml`

**Interfaces:**
- Produces: Translated string resources for `jstore_see_all`, `jstore_open`, `jstore_search_hint`.

- [ ] **Step 1: Add Vietnamese translations to `values-vi/strings_jstore.xml`**
  Add:
  - `jstore_see_all`: "Tất cả ›"
  - `jstore_open`: "Mở"
  - `jstore_search_hint`: "Tìm kiếm ứng dụng &amp; trò chơi"

- [ ] **Step 2: Add Chinese and Russian translations to respective `strings_jstore.xml`**
  - `values-zh/strings_jstore.xml`: `jstore_see_all` ("查看全部 ›"), `jstore_open` ("打开"), `jstore_search_hint` ("搜索应用和游戏")
  - `values-ru/strings_jstore.xml`: `jstore_see_all` ("Все ›"), `jstore_open` ("Открыть"), `jstore_search_hint` ("Поиск приложений и игр")

- [ ] **Step 3: Verify build**
  Run `./gradlew.bat :app:assembleVanillaDebug` to confirm resource compilation succeeds.

- [ ] **Step 4: Commit**
  `git commit -m "feat(i18n): add missing translations for head-unit UI strings"`

---

### Task 2: Segmented Tabs Contrast & Tall Screen Dimens (Issues 2 & 3)

**Files:**
- Modify: `app/src/main/java/com/aurora/store/jstore/ui/JstoreSegmentedTabs.kt`
- Modify: `app/src/main/res/values-h1024dp/dimens_jstore.xml`

**Interfaces:**
- Consumes: `MaterialTheme.colorScheme`, `JstoreDarkColorScheme`.
- Produces: Enhanced dark mode selected background in `JstoreSegmentedTabs`, scaled `jstore_tile_width` (188dp) in `values-h1024dp`.

- [ ] **Step 1: Improve dark mode contrast in `JstoreSegmentedTabs`**
  In `JstoreSegmentedTabs.kt`, update the selected tab pill background:
  When dark mode (`colors.background.luminance() < 0.5f`), use `colors.surfaceContainerHighest` or `colors.surfaceContainerHigh` with a subtle outline (`BorderStroke(1.dp, colors.outline.copy(alpha = 0.5f))`) so the selected tab is visibly lighter and crisper than the `surfaceVariant` (`#24272B`) track.

- [ ] **Step 2: Add proportional `jstore_tile_width` in `values-h1024dp/dimens_jstore.xml`**
  Add `<dimen name="jstore_tile_width">188dp</dimen>` to `values-h1024dp/dimens_jstore.xml` so the 120dp icon has comfortable 34dp horizontal margins and 28sp caption text doesn't wrap prematurely.

- [ ] **Step 3: Verify build and previews**
  Run `./gradlew.bat :app:assembleVanillaDebug`. Check preview rendering of `JstoreSegmentedTabsPreview`.

- [ ] **Step 4: Commit**
  `git commit -m "fix(ui): improve segmented tabs contrast in dark mode and scale tile width on tall screens"`

---

### Task 3: Deduplicate Hero App in First Cluster (Issue 4)

**Files:**
- Modify: `app/src/main/java/com/aurora/store/compose/composable/StreamCarousel.kt`
- Modify: `app/src/test/java/com/aurora/store/jstore/ui/HeroAppTest.kt`

**Interfaces:**
- Consumes: `heroApp(clusters): App?`.
- Produces: Clean cluster listing where the hero app is not duplicated in the first cluster when `showHero` is true.

- [ ] **Step 1: Write unit test verifying deduplication logic**
  In `HeroAppTest.kt`, add a test `firstClusterWithoutHero()` verifying that when a cluster's apps are filtered by excluding the hero app, the hero app does not appear in the resulting list.

- [ ] **Step 2: Update `StreamCarousel.kt`**
  When `showHero` is true and `heroApp(clusters)` returns non-null, filter out the hero package from the first cluster's app list (`if (index == 0) cluster.copy(clusterAppList = cluster.clusterAppList.filter { it.packageName != hero.packageName }) else cluster`).

- [ ] **Step 3: Run unit tests**
  Run `./gradlew.bat :app:testVanillaDebugUnitTest --tests "*HeroAppTest"`. Verify all pass.

- [ ] **Step 4: Commit**
  `git commit -m "fix(ui): prevent duplicate hero app from appearing in the first cluster"`

---

### Task 4: Card Styling across App Lists & Top Charts Tab Bar (Issue 5)

**Files:**
- Modify: `app/src/main/java/com/aurora/store/compose/ui/apps/TopChartsPage.kt`
- Modify: `app/src/main/java/com/aurora/store/compose/ui/apps/CategoriesPage.kt`
- Modify: `app/src/main/java/com/aurora/store/compose/ui/search/SearchScreen.kt`
- Modify: `app/src/main/java/com/aurora/store/compose/ui/installed/InstalledScreen.kt`
- Modify: `app/src/main/java/com/aurora/store/compose/ui/downloads/DownloadsScreen.kt`
- Modify: `app/src/main/java/com/aurora/store/compose/ui/commons/StreamBrowseScreen.kt`

**Interfaces:**
- Consumes: `Modifier.jstoreCard()` from `com.aurora.store.jstore.ui.JstoreClusterCard`.
- Produces: Harmonized card container appearance for all app rows.

- [ ] **Step 1: Apply `jstoreCard()` in `TopChartsPage.kt` & replace tab row with `JstoreSegmentedTabs`**
  - Replace `SecondaryScrollableTabRow` with `JstoreSegmentedTabs` (or segmented pill row with padding) matching the style of Apps/Games.
  - Apply `Modifier.jstoreCard()` to `LargeAppListItem`.
  - Adjust vertical spacing to `dimensionResource(R.dimen.jstore_space_sm)`.

- [ ] **Step 2: Apply `jstoreCard()` in `CategoriesPage.kt`**
  - In `CategoriesBody`, wrap `CategoryItem` with `Modifier.jstoreCard()` and set list content padding to `jstore_space_sm`.

- [ ] **Step 3: Apply `jstoreCard()` in `SearchScreen.kt`**
  - Apply `Modifier.jstoreCard()` to search result `LargeAppListItem` items.

- [ ] **Step 4: Apply `jstoreCard()` in `InstalledScreen.kt`, `DownloadsScreen.kt`, and `StreamBrowseScreen.kt`**
  - Apply `Modifier.jstoreCard()` to list items in these screens.

- [ ] **Step 5: Verify build**
  Run `./gradlew.bat :app:assembleVanillaDebug`.

- [ ] **Step 6: Commit**
  `git commit -m "feat(ui): unify card styling across top charts, categories, search, installed, and downloads"`

---

### Task 5: Head-Unit Ergonomics for `TopAppBar` (Issue 6)

**Files:**
- Modify: `app/src/main/java/com/aurora/store/compose/composable/TopAppBar.kt`

**Interfaces:**
- Produces: Larger back navigation touch targets and icons adhering to automotive UI standards.

- [ ] **Step 1: Enlarge navigation icon and touch size in `TopAppBar.kt`**
  - Increase back button `IconButton` size to `dimensionResource(R.dimen.jstore_header_action_size)` (72dp) or minimum `jstore_touch_min` (64dp).
  - Increase `Icon` size to `dimensionResource(R.dimen.jstore_header_icon)` (48dp).
  - Update title typography to `MaterialTheme.typography.titleLarge` or `headlineSmall`.

- [ ] **Step 2: Verify build and preview**
  Run `./gradlew.bat :app:assembleVanillaDebug`.

- [ ] **Step 3: Commit**
  `git commit -m "feat(ui): scale TopAppBar back button and title for head unit touch targets"`

---

### Task 6: App Details Screen Ergonomics & Buttons (Issue 7)

**Files:**
- Modify: `app/src/main/java/com/aurora/store/compose/ui/details/composable/Actions.kt`
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/main/res/values-h1024dp/dimens_jstore.xml`

**Interfaces:**
- Produces: Flexible, full-width action buttons that never truncate, with touch height ≥ 64dp.

- [ ] **Step 1: Enhance `Actions.kt` button sizing**
  - Allow action buttons in `Actions.kt` to share available width with `Modifier.weight(1f)` (or minimum touch height `dimensionResource(R.dimen.jstore_touch_min)`), eliminating unnecessary ellipsis truncation on automotive screens.
  - Set button height to minimum 64dp for easy tapping while driving.

- [ ] **Step 2: Provide responsive screenshot dimensions**
  - Ensure screenshots in `Screenshots.kt` display at an appropriate head-unit size on large portrait displays.

- [ ] **Step 3: Verify build**
  Run `./gradlew.bat :app:assembleVanillaDebug`.

- [ ] **Step 4: Commit**
  `git commit -m "fix(ui): expand action buttons to prevent text truncation in app details"`

---

### Task 7: Shimmer Placeholder Realignment (Issue 8)

**Files:**
- Modify: `app/src/main/java/com/aurora/store/compose/composable/Shimmer.kt`

**Interfaces:**
- Produces: Shimmer placeholders matching `JstoreClusterCard` and `jstoreCard()` dimensions.

- [ ] **Step 1: Align `ShimmerCarouselSection` with `JstoreClusterCard`**
  - Wrap `ShimmerCarouselSection` in a card container with `jstore_card_radius`, `surfaceContainer` background and horizontal padding `jstore_space_lg`.
  - Update tile shimmers to match `JstoreAppTile` icon size and text heights.

- [ ] **Step 2: Align `ShimmerAppRow` and `ShimmerUpdateItem`**
  - Ensure list shimmers use `Modifier.jstoreCard()` so transitions from loading to loaded state cause zero layout jump.

- [ ] **Step 3: Verify build**
  Run `./gradlew.bat :app:assembleVanillaDebug`.

- [ ] **Step 4: Commit**
  `git commit -m "fix(ui): harmonize shimmer loading states with head-unit card layouts"`

---

### Task 8: Onboarding Screen Layout for Automotive Portrait (Issue 9)

**Files:**
- Modify: `app/src/main/java/com/aurora/store/compose/ui/onboarding/OnboardingScreen.kt`

**Interfaces:**
- Produces: Single-column onboarding on portrait automotive displays, keeping two-column only on landscape/ultra-wide displays.

- [ ] **Step 1: Update column split condition in `OnboardingScreen.kt`**
  - Check `LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE` alongside `!windowAdaptiveInfo.isWindowCompact` before rendering the side-by-side logo.
  - When in portrait (even with width > 600dp like 853dp), use single-column layout with pager occupying full width and a compact header logo if needed.

- [ ] **Step 2: Verify build**
  Run `./gradlew.bat :app:assembleVanillaDebug`.

- [ ] **Step 3: Commit**
  `git commit -m "fix(ui): adapt onboarding screen layout for portrait head-unit displays"`

---

### Task 9: Comprehensive Verification & Test Suite

**Files:**
- None (verification only)

- [ ] **Step 1: Run full unit test suite**
  Run `./gradlew.bat :app:testVanillaDebugUnitTest` to ensure all tests pass.

- [ ] **Step 2: Run full project assembly**
  Run `./gradlew.bat :app:assembleVanillaDebug` to ensure no compile or packaging issues.

- [ ] **Step 3: Review git diff**
  Inspect `git diff HEAD~8` to verify all upstream changes have `// jstore:` comments and no unnecessary edits exist.
