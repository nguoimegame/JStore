# jstore head-unit UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Màn chính jstore (For you, Top charts, Categories, Updates, header, thanh điều hướng) khớp mock đã duyệt, theo ngôn ngữ thiết kế JaecooApp.

**Architecture:** Component Compose mới trong `com.aurora.store.jstore.ui`, kích thước trong `res/values*/dimens_jstore.xml`. File upstream chỉ có điểm móc ngắn đánh dấu `// jstore:` gọi thẳng các component đó. Màu và thang chữ đã có trong `JstoreTheme`.

**Tech Stack:** Kotlin, Jetpack Compose Material3 (Expressive), Hilt, Coil3, JUnit4 + Truth.

**Spec:** `docs/superpowers/specs/2026-10-27-jstore-headunit-ui-design.md`. Mock: `docs/mockups/2026-10-27-jstore-ui-mock.html`.

## Global Constraints

- Mọi dòng thêm vào file upstream (ngoài `com.aurora.store.jstore` và `*_jstore.xml`) có comment `// jstore:` (hoặc `// jstore` cuối dòng).
- Thêm tham số vào hàm upstream chỉ ở cuối danh sách, có giá trị mặc định. Không đổi chữ ký sẵn có.
- Không sửa: `AppListItem.kt`, `SectionHeader.kt`, `AppUpdateItem.kt`, `Theme.kt`, `Color.kt`.
- Màu chỉ lấy từ `MaterialTheme.colorScheme`, chữ từ `MaterialTheme.typography`, kích thước từ `dimens_jstore.xml`. Không viết cứng dp/sp/màu trong component.
- Chuỗi mới nằm trong `res/values/strings_jstore.xml`.
- File mới có header `SPDX-FileCopyrightText: 2026 nguoimegame` + `SPDX-License-Identifier: GPL-3.0-or-later` như `JstoreTheme.kt`.
- Mỗi component mới có `@Preview` dùng `@PreviewWrapper(ThemePreviewProvider::class)` như `AppListItem.kt`.
- Lệnh build: `./gradlew.bat :app:assembleVanillaDebug`; test: `./gradlew.bat :app:testVanillaDebugUnitTest`; lint: `./gradlew.bat :app:lintVanillaDebug`.
- Chạy app trên emulator theo skill `.claude/skills/run-jstore/SKILL.md`.

## Review Focus

1. Tên app một từ rất dài (ví dụ "Bookinglandiaplus") phải dừng ở 2 dòng với "…", không tràn ra ngoài ô 150dp, không đẩy lệch hàng. Kiểm trong Preview của Task 2.
2. Bundle chỉ có một cụm (Category browse, Dev profile đi nhánh `LargeAppListItem`) thì không vẽ hero và không vẽ thẻ cụm. Hero chỉ thuộc nhánh nhiều cụm. Kiểm ở Task 3 trên Category browse.
3. Màn ngang 2544×960 @240 vẫn có rail, màn 1280×1606 @240 ra thanh dưới. Kiểm ở Task 1 bằng hai emulator.
4. Đăng nhập ẩn danh hoặc email rỗng thì header không có phụ đề và không đổi chiều cao. Kiểm trong Preview của Task 5.
5. Giao diện tối: chữ trên thẻ hero gradient và trên thẻ cụm vẫn đọc được. Kiểm ảnh tối ở Task 7.

---

### Task 1: Dimens + rail theo chiều rộng

**Files:**
- Modify: `app/src/main/res/values/dimens_jstore.xml`
- Modify: `app/src/main/res/values/bools_jstore.xml` (giá trị thành `false`, sửa comment)
- Create: `app/src/main/res/values-w1200dp/bools_jstore.xml` (`jstore_nav_rail` = `true`)
- Delete: `app/src/main/res/values-h1024dp/bools_jstore.xml`
- Modify: `app/src/main/java/com/aurora/store/jstore/ui/JstoreNavRail.kt` (KDoc: rail dành cho màn rộng ≥ 1200dp)

**Interfaces:**
- Produces: `R.dimen.jstore_tile_width` 150dp, `jstore_tile_icon` 96dp, `jstore_tile_icon_radius` 22dp, `jstore_card_radius` 20dp, `jstore_space_sm` 12dp, `jstore_space_md` 18dp, `jstore_space_lg` 24dp, `jstore_bottom_bar_height` 112dp, `jstore_hero_icon` 112dp, `jstore_segment_height` 56dp.

- [ ] **Step 1: Đo vùng app trên màn đích (spec D6).** Khởi động `otoAutomative`, `adb shell wm density 240`, mở jstore, chạy `adb shell dumpsys window displays | grep -o "app=[0-9x]*"` rồi `adb shell wm density reset`. Kết quả mong đợi: chiều cao (số thứ hai, đổi px → dp bằng ÷1,5) < 1024dp. **Nếu ≥ 1024dp: dừng, báo người dùng, không làm tiếp.**
- [ ] **Step 2: Thêm dimens và đổi qualifier của `jstore_nav_rail` như danh sách Files.**
- [ ] **Step 3: Build.** `./gradlew.bat :app:assembleVanillaDebug` → BUILD SUCCESSFUL.
- [ ] **Step 4: Kiểm trên hai emulator.** `otoAutomative` @240: thanh dưới Material hiện, không có rail. `C5Automative` (2544×960 @240): rail hiện. Chụp cả hai.
- [ ] **Step 5: Commit** `feat(ui): head-unit dimens; choose the rail by screen width`.

### Task 2: Ô app và thẻ cụm

**Files:**
- Create: `app/src/main/java/com/aurora/store/jstore/ui/JstoreAppTile.kt`
- Create: `app/src/main/java/com/aurora/store/jstore/ui/JstoreClusterCard.kt`
- Modify: `app/src/main/java/com/aurora/store/compose/composable/StreamCarousel.kt:113-131` (nhánh nhiều cụm) và `:168-171` (`ClusterRow`)

**Interfaces:**
- Consumes: dimens Task 1.
- Produces:
  - `@Composable fun JstoreAppTile(app: App, onClick: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun JstoreClusterCard(title: String, onSeeAll: (() -> Unit)?, modifier: Modifier = Modifier, content: @Composable () -> Unit)`
  - `@Composable fun JstoreCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`: khung thẻ dùng chung (nền `surfaceContainer`, viền 1dp `outlineVariant`, bo `jstore_card_radius`), nằm trong `JstoreClusterCard.kt`. Task 6 dùng lại.

- [ ] **Step 1: `JstoreAppTile`.** Cột rộng `jstore_tile_width`, `clickable`. Icon `AsyncImage` (`app.iconArtwork.url`, crossfade như `AppListItem`) cỡ `jstore_tile_icon`, bo `jstore_tile_icon_radius`. Tên: `labelMedium`, `maxLines = 2`, `TextOverflow.Ellipsis`, `TextAlign.Center`, chiều cao cố định đúng 2 dòng (`minLines = 2`) để các ô thẳng hàng. Dòng phụ `"${app.labeledRating} ★"` (`labelSmall`, `onSurfaceVariant`) chỉ khi `labeledRating` không rỗng.
- [ ] **Step 2: `JstoreCard` + `JstoreClusterCard`.** Hàng tiêu đề: `title` (`titleLarge`, 1 dòng, ellipsis) và khi `onSeeAll != null` thì `TextButton` "See all ›" (`stringResource(R.string.jstore_see_all)`, thêm vào `strings_jstore.xml`). Padding ngang `jstore_space_lg`, khoảng dưới tiêu đề `jstore_space_sm`.
- [ ] **Step 3: Preview.** `JstoreAppTile` với `AppPreviewProvider` và với `displayName = "Bookinglandiaplus Travel Planner"` (Review Focus 1); `JstoreClusterCard` có 3 ô, có và không có `onSeeAll`.
- [ ] **Step 4: Móc vào `StreamCarousel`.** Nhánh nhiều cụm: mỗi cụm thành **một** `item(key = "cluster_${cluster.id}")` gồm `JstoreClusterCard(title = cluster.clusterTitle, onSeeAll = <như onClick cũ của SectionHeader>) { ClusterRow(...) }`. `LazyColumn` nhánh này: padding ngang `jstore_space_lg`, khoảng cách item `jstore_space_md`. Trong `ClusterRow`: `AppListItem(...)` → `JstoreAppTile(...)`, `contentPadding`/`spacedBy` dùng `jstore_space_lg`/`jstore_space_sm`. Nhánh một cụm giữ nguyên.
- [ ] **Step 5: Build và chạy.** Build thành công; trên `otoAutomative` @240 tab For you: mỗi thẻ cụm thấy khoảng 5 ô, tên không cắt giữa từ. Chụp màn.
- [ ] **Step 6: Commit** `feat(ui): app tiles and cluster cards for the head unit`.

### Task 3: Thẻ nổi bật (hero)

**Files:**
- Create: `app/src/main/java/com/aurora/store/jstore/ui/JstoreHeroCard.kt`
- Test: `app/src/test/java/com/aurora/store/jstore/ui/HeroAppTest.kt`
- Modify: `StreamCarousel.kt` (tham số cuối + một item), `app/src/main/java/com/aurora/store/compose/ui/apps/ForYouPage.kt:40-47`

**Interfaces:**
- Consumes: `JstoreCard` không dùng (hero có nền riêng); dimens Task 1.
- Produces: `fun heroApp(clusters: List<StreamCluster>): App?`; `@Composable fun JstoreHeroCard(app: App, onClick: () -> Unit, modifier: Modifier = Modifier)`; tham số `StreamCarousel(..., showHero: Boolean = false)`.

- [ ] **Step 1: Test thất bại.** `HeroAppTest` (JUnit4 + Truth như `JaecooConnectionTest`):
  - `noClustersGivesNull`: `heroApp(emptyList())` là `null`.
  - `emptyFirstClusterGivesNull`: cụm đầu có `clusterAppList = emptyList()` → `null`.
  - `firstAppOfFirstCluster`: hai cụm `[App("a"), App("b")]`, `[App("c")]` → `packageName == "a"`.
- [ ] **Step 2: Chạy** `./gradlew.bat :app:testVanillaDebugUnitTest --tests "*HeroAppTest"` → FAIL (chưa có `heroApp`).
- [ ] **Step 3: Viết `heroApp`** (trả `clusters.firstOrNull()?.clusterAppList?.firstOrNull()`) và `JstoreHeroCard`: hàng nền `Brush.linearGradient(secondary, primary)` bo `jstore_card_radius`, padding `jstore_space_lg`; icon nền trắng cỡ `jstore_hero_icon`; tên `headlineSmall` + `app.developerName` (`bodyMedium`) màu `onPrimary`, mỗi dòng tối đa 1 dòng; nút `FilledTonalButton` "Open" (`R.string.jstore_open`) gọi `onClick`. Cả thẻ cũng `clickable(onClick)`.
- [ ] **Step 4: Chạy test** → PASS.
- [ ] **Step 5: Móc.** `StreamCarousel` thêm tham số cuối `showHero: Boolean = false // jstore`. Chỉ trong nhánh nhiều cụm, trước các cụm: `if (showHero) heroApp(clusters)?.let { item(key = "jstore_hero") { JstoreHeroCard(it) { onAppClick(it) } } }`. `ForYouPage` truyền `showHero = true`.
- [ ] **Step 6: Kiểm.** For you có hero đầu trang; mở một Category browse (bundle một cụm) không có hero (Review Focus 2). Chụp cả hai.
- [ ] **Step 7: Commit** `feat(ui): featured app card on For you`.

### Task 4: Tab dạng segment

**Files:**
- Create: `app/src/main/java/com/aurora/store/jstore/ui/JstoreSegmentedTabs.kt`
- Modify: `app/src/main/java/com/aurora/store/compose/ui/apps/AppsGamesScreen.kt:74-89`

**Interfaces:**
- Produces: `@Composable fun JstoreSegmentedTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Viết component.** `Row` nền `surfaceVariant`, `RoundedCornerShape(50)`, padding trong 4dp. Mỗi mục `weight(1f)`, cao `jstore_segment_height`, bo pill, `selectable(role = Role.Tab)`. Mục chọn nền `surfaceContainer`, chữ `primary` đậm; còn lại chữ `onSurfaceVariant`. Chữ `bodyMedium`, 1 dòng. Preview 3 tab.
- [ ] **Step 2: Móc.** Thay `PrimaryTabRow { … }` bằng `JstoreSegmentedTabs(titles = tabs.map { stringResource(it.titleRes) }, selected = pagerState.currentPage, onSelect = { coroutineScope.launch { pagerState.animateScrollToPage(it) } }, modifier = Modifier.padding(horizontal = jstore_space_lg, vertical = jstore_space_sm))`.
- [ ] **Step 3: Build, chạy, bấm qua 3 tab** → trang đổi đúng, mục chọn nổi bật. Chụp.
- [ ] **Step 4: Commit** `feat(ui): segmented tabs on Apps and Games`.

### Task 5: Header, ô tìm kiếm, thanh dưới

**Files:**
- Create: `app/src/main/java/com/aurora/store/jstore/ui/JstoreMainHeader.kt`
- Create: `app/src/main/java/com/aurora/store/jstore/ui/JstoreBottomBar.kt`
- Modify: `app/src/main/java/com/aurora/store/MainViewModel.kt` (inject `AuthProvider`, thêm `val accountEmail: String get() = authProvider.authData?.email.orEmpty() // jstore`)
- Modify: `app/src/main/java/com/aurora/store/compose/ui/main/MainScreen.kt:181-250`

**Interfaces:**
- Consumes: `JstoreNavItem` (đã có trong `JstoreNavRail.kt`).
- Produces:
  - `@Composable fun JstoreMainHeader(title: String, subtitle: String?, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {})`
  - `@Composable fun JstoreSearchField(onClick: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun JstoreBottomBar(items: List<JstoreNavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: `JstoreMainHeader`.** Hàng padding ngang `jstore_space_lg`, trên `jstore_space_md`. Cột trái `weight(1f)`: `title` (`headlineMedium`, đậm), `subtitle` (`bodySmall`, `onSurfaceVariant`) chỉ khi không rỗng. `actions` ở cuối, cách nhau `jstore_space_sm`. Không có nút back. Áp `WindowInsets.statusBars` như `TopAppBar` gốc đang làm.
- [ ] **Step 2: `JstoreSearchField`.** `Surface(onClick)` pill cao `jstore_touch_min`, nền `surfaceContainer`, viền `outlineVariant`, icon `R.drawable.ic_round_search` + chữ `R.string.jstore_search_hint` = "Search apps & games" (`bodyMedium`, `onSurfaceVariant`). Margin ngang `jstore_space_lg`.
- [ ] **Step 3: `JstoreBottomBar`.** `Surface` nền `surfaceContainer`, viền trên `outlineVariant`, cao `jstore_bottom_bar_height` + `navigationBars` inset. Mỗi mục `weight(1f)`, `selectable(role = Role.Tab)`: pill 60×30dp (nền `secondaryContainer` khi chọn) chứa icon, dưới là nhãn `bodySmall` (chọn: `primary` đậm). Badge như `JstoreNavRail`.
- [ ] **Step 4: Preview.** Header có email, header email rỗng (Review Focus 4: cùng chiều cao với trường hợp không phụ đề), ô tìm kiếm, thanh dưới có badge 3.
- [ ] **Step 5: Móc vào `MainScreen`.** `topBar` → `Column { JstoreMainHeader(title, mainViewModel.accountEmail, actions = { <3 IconButton cũ giữ nguyên> }); JstoreSearchField { onNavigateTo(Destination.Search) } }`. Xoá khối `floatingActionButton` (`// jstore: search lives in the header`). `bottomBar`: giữ dòng `if (useRail) return@Scaffold`, thay `NavigationBar { … }` bằng `JstoreBottomBar(items = <cùng danh sách như rail>, …)`. Tách `MainTab.entries.map { … JstoreNavItem(…) }` thành một `val navItems` dùng chung cho rail và thanh dưới.
- [ ] **Step 6: Build, chạy.** Header hiện `quangit22@gmail.com`; bấm ô tìm kiếm mở màn Search; 3 nút góc phải hoạt động; thanh dưới chuyển tab, badge Updates đúng số. Chụp.
- [ ] **Step 7: Commit** `feat(ui): header with account and search field, head-unit bottom bar`.

### Task 6: Updates

**Files:**
- Modify: `app/src/main/java/com/aurora/store/compose/ui/updates/UpdatesScreen.kt` (các chỗ gọi `AppUpdateItem`, kể cả trong `updateItems`; `TextButton` "Update all" ở dòng ~159)

**Interfaces:**
- Consumes: `JstoreCard` (Task 2).

- [ ] **Step 1: Móc.** Mỗi `AppUpdateItem(...)` bọc trong `JstoreCard(Modifier.padding(horizontal = jstore_space_lg)) { … }`. `TextButton` "Update all"/"Cancel all" → `Button`. `LazyColumn` khoảng cách item `jstore_space_sm`.
- [ ] **Step 2: Build, chạy.** Tab Updates: mỗi app trong thẻ trắng bo 20dp, nút Update/huỷ vẫn hoạt động (bấm thử Update một app rồi huỷ). Nếu emulator không có bản cập nhật, cài bản cũ của một app nhỏ bằng adb để tạo. Chụp.
- [ ] **Step 3: Commit** `feat(ui): update rows as cards`.

### Task 7: Kiểm thử tổng và cập nhật skill

**Files:**
- Modify: `.claude/skills/run-jstore/SKILL.md` (mục "Known UI observations")

- [ ] **Step 1:** `./gradlew.bat :app:assembleVanillaDebug :app:testVanillaDebugUnitTest :app:lintVanillaDebug` → thành công, lint không có lỗi mới so với trước Task 1.
- [ ] **Step 2: Ảnh màn đích.** `otoAutomative`, `wm density 240`: For you (đầu trang, đã cuộn), Top charts, Categories, Updates, ở cả sáng (`Settings → Theme`) lẫn tối (Review Focus 5). So với mock; ghi lại chỗ lệch. `wm density reset`.
- [ ] **Step 3: Màn ngang.** `C5Automative`: rail hiện, For you và Updates không vỡ layout.
- [ ] **Step 4: Merge thử.** Nhánh tạm: `git fetch upstream && git switch -c tmp-merge-check && git merge --no-commit --no-ff upstream/master`; ghi số file xung đột; `git merge --abort && git switch - && git branch -D tmp-merge-check`.
- [ ] **Step 5: Cập nhật skill** (bỏ ghi chú cũ về tên bị cắt, thêm ghi chú mới và lệnh `wm density 240`).
- [ ] **Step 6: Commit** `docs: refresh run-jstore notes after the head-unit UI`.
