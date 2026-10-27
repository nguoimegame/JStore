# jstore: giao diện head unit theo JaecooApp

- Ngày: 2026-10-27
- Mock đã duyệt: [docs/mockups/2026-10-27-jstore-ui-mock.html](../../mockups/2026-10-27-jstore-ui-mock.html)
- Phạm vi: màn chính (Apps/Games › For you, Top charts, Categories; Updates), header, thanh điều hướng.

## 1. Mục tiêu

Màn chính của jstore dùng được khi lái: tên app đọc được, mục tiêu chạm đủ lớn, cùng ngôn ngữ thiết kế
với JaecooApp. Đồng thời, việc merge upstream Aurora Store vẫn nhẹ như hiện nay.

Thành công khi:

1. Trên màn 1280×1606 @240dpi, giao diện khớp mock: mỗi hàng 5 app, tên app tối đa 2 dòng và không bị
   cắt giữa từ, nhóm nằm trong thẻ, có ô tìm kiếm cố định, tab dạng segment, thanh dưới 3 mục.
2. Mọi mục chạm từ 64dp trở lên.
3. Các file upstream chỉ có thay đổi nhỏ, đánh dấu `// jstore:` (xem bảng §5). Thay đổi thật nằm trong
   `com.aurora.store.jstore.ui` và resource `*_jstore.xml`.
4. Màn ngang 2544×960 @240dpi (rail bên trái) không bị hỏng.

Ngoài phạm vi: màn chi tiết app, tìm kiếm, onboarding, cài đặt. Đây là các vòng sau.

## 2. Những gì đã có (không làm lại)

| Có sẵn | Nơi | Vai trò |
|---|---|---|
| `JstoreTheme` | `jstore/ui/JstoreTheme.kt`, móc ở `Theme.kt` | Màu sáng/tối của JaecooApp, thang chữ, `LocalMinimumInteractiveComponentSize` = 64dp |
| Thang chữ theo chiều cao | `values/dimens_jstore.xml`, `values-h1024dp/dimens_jstore.xml` | Màn thấp body 27sp / title 30sp; màn cao 36/40sp |
| `JstoreNavRail` + `bool jstore_nav_rail` | `jstore/ui/JstoreNavRail.kt`, `MainScreen.kt` | Rail cho màn thấp, thanh dưới cho màn cao |

## 3. Quyết định thiết kế

Bạn đã duyệt mock nhưng chưa trả lời 4 câu hỏi kèm theo. Spec chọn đúng như mock vẽ:

| # | Câu hỏi | Chọn | Lý do |
|---|---|---|---|
| D1 | Thanh dưới hay rail | **Thanh dưới** ở màn hẹp, rail ở màn rộng | Rail 120dp chiếm 14% chiều ngang khi màn chỉ rộng 853dp |
| D2 | Thẻ nổi bật đầu trang | **Giữ**, chỉ ở For you | Lấy app đầu tiên của nhóm đầu tiên; Play không có dữ liệu "featured" riêng |
| D3 | Sáng/tối | **Theo hệ thống** (giá trị mặc định hiện có của `PREFERENCE_THEME_STYLE`) | Không đổi hành vi |
| D4 | Mock thêm màn khác | **Không** | Để vòng sau |

Các quyết định kỹ thuật:

- **D5. Rail chọn theo chiều rộng, không theo chiều cao.** Hiện `jstore_nav_rail` = true cho mọi màn
  dưới 1024dp chiều cao. Màn 1280×1606 @240 chỉ cao khoảng 1070dp trước khi trừ thanh hệ thống, nên
  vùng app nhiều khả năng dưới 1024dp và sẽ bị rail. Đổi thành: mặc định `false`, bật `true` ở
  `values-w1200dp/bools_jstore.xml`. Màn ngang 1696dp vẫn có rail. Xoá `values-h1024dp/bools_jstore.xml`
  vì không còn cần.
- **D6. Thang chữ.** Mock dùng thang màn thấp (body 27sp). Thang này tự áp dụng khi vùng app dưới
  1024dp. Bước 0 của kế hoạch đo `screenHeightDp` thật trên emulator. Nếu kết quả từ 1024dp trở lên,
  dừng lại hỏi bạn trước khi đổi qualifier.
- **D7. Điểm móc là lời gọi thẳng, không qua interface.** Cách A ban đầu đề xuất một `CompositionLocal`
  chứa các composable để ghi đè. jstore luôn dùng giao diện của mình và không cần đổi lúc chạy, nên
  interface đó chỉ thêm một tầng gián tiếp. Một dòng `JstoreAppTile(...)` trong file upstream gây xung
  đột bằng đúng một dòng `JstoreUi.current.AppTile(...)`. Nguyên tắc của cách A vẫn giữ: thêm file mới,
  chỉ để điểm móc ngắn trong upstream. Kích thước vẫn đi qua resource như `JstoreTheme`.
- **D8. Không viết lại logic upstream.** Hàng cập nhật bọc `AppUpdateItem` gốc trong thẻ thay vì viết
  lại, vì nó chứa logic tải, huỷ, đang kiểm tra (upstream sửa 7 lần/năm).

## 4. Component mới (`com.aurora.store.jstore.ui`)

Mỗi file một component, có `@Preview` sáng và tối.

| File | Component | Mô tả |
|---|---|---|
| `JstoreAppTile.kt` | `JstoreAppTile(app, onClick)` | Ô rộng `jstore_tile_width` (150dp), icon `jstore_tile_icon` (96dp) bo 22dp, tên `labelMedium` tối đa 2 dòng, ellipsis ở cuối; dòng phụ là điểm đánh giá nếu có |
| `JstoreClusterCard.kt` | `JstoreClusterCard(title, onSeeAll, content)` | Thẻ `surfaceContainer`, viền `outlineVariant`, bo `jstore_card_radius` (20dp). Tiêu đề `titleLarge`; "See all ›" màu `primary` khi `onSeeAll != null`, vùng chạm ≥ 64dp |
| `JstoreHeroCard.kt` | `JstoreHeroCard(app, onClick)` + `heroApp(clusters): App?` | Thẻ gradient `secondary → primary`, icon 112dp, tên, mô tả ngắn, nút mở chi tiết. `heroApp` là hàm thuần, có unit test |
| `JstoreMainHeader.kt` | `JstoreMainHeader(title, subtitle, actions)`, `JstoreSearchField(onClick)` | Header không có nút back: tiêu đề `headlineMedium`, phụ đề là email tài khoản (ẩn nếu rỗng), nút hành động tròn 64dp ở cuối. Ô tìm kiếm dạng pill cao 64dp, chỉ là nút mở `Destination.Search` |
| `JstoreSegmentedTabs.kt` | `JstoreSegmentedTabs(titles, selected, onSelect)` | Track `surfaceVariant` dạng pill, mục chọn nền `surfaceContainer` chữ `primary`, mỗi mục cao 56dp |
| `JstoreBottomBar.kt` | `JstoreBottomBar(items, selected, onSelect)` | Dùng lại `JstoreNavItem` của rail. Cao `jstore_bottom_bar_height` (112dp), pill chỉ mục chọn, badge số |
| `JstoreCard.kt` | `JstoreCard(content)` | Thẻ bọc chung cho hàng danh sách (Updates), padding 24dp |

Resource thêm vào `values/dimens_jstore.xml` (một giá trị cho mọi màn, trừ khi ghi khác):
`jstore_tile_width` 150dp, `jstore_tile_icon` 96dp, `jstore_card_radius` 20dp, `jstore_space_sm` 12dp,
`jstore_space_md` 18dp, `jstore_space_lg` 24dp, `jstore_bottom_bar_height` 112dp,
`jstore_hero_icon` 112dp. Chuỗi mới (nếu có, ví dụ "Search apps & games") đặt trong `strings_jstore.xml`.

## 5. Điểm móc vào upstream

Số lần sửa là số commit upstream trong 12 tháng qua (`git log --since=2025-10-27 upstream/master`).

| File upstream (sửa/năm) | Thay đổi | Ước lượng diff |
|---|---|---|
| `composable/StreamCarousel.kt` (5) | Thêm tham số cuối `showHero: Boolean = false`; một `item` hero khi `showHero`; thay cặp `SectionHeader` + `ClusterRow` bằng `JstoreClusterCard { ClusterRow(...) }`; trong `ClusterRow` đổi `AppListItem` → `JstoreAppTile` | ~15 dòng |
| `ui/apps/ForYouPage.kt` (thấp) | Truyền `showHero = true` | 1 dòng |
| `ui/apps/AppsGamesScreen.kt` (2) | Thay `PrimaryTabRow { … }` bằng `JstoreSegmentedTabs(...)` | ~15 dòng thành 5 |
| `ui/main/MainScreen.kt` (8) | `topBar` → `JstoreMainHeader` + `JstoreSearchField` (giữ cả 3 nút: thông báo, tải xuống, thêm); bỏ `floatingActionButton`; `bottomBar` → `JstoreBottomBar` | ~40 dòng; file sửa nhiều nhất, giữ khối thay đổi liền nhau |
| `ui/updates/UpdatesScreen.kt` (7) | Bọc `AppUpdateItem` trong `JstoreCard`; `TextButton` "Update all" → `Button` | ~6 dòng |
| `res/values*/bools_jstore.xml` (của jstore) | D5: đổi qualifier | không đụng upstream |

Email cho phụ đề header: đọc `authProvider.authData?.email` qua ViewModel mà `MainScreen` đang dùng
(thêm một thuộc tính, đánh dấu `// jstore:`).

Không sửa: `AppListItem.kt`, `SectionHeader.kt`, `AppUpdateItem.kt`, `Theme.kt`, `Color.kt`. Màn khác vẫn
dùng component gốc, chỉ đổi màu và thang chữ qua `JstoreTheme`.

## 6. Xử lý lỗi và trạng thái rỗng

- Đang tải: giữ shimmer của upstream. Shimmer có kích thước khác ô mới, chấp nhận ở vòng này.
- Không có cụm nào: giữ `Placeholder` của upstream.
- `heroApp` trả `null` khi không có cụm hoặc cụm rỗng; khi đó không vẽ thẻ hero.
- Email rỗng (đăng nhập ẩn danh): ẩn phụ đề, header không đổi chiều cao.

## 7. Kiểm thử

1. Unit test `heroApp` (không có cụm, cụm rỗng, bình thường).
2. `@Preview` sáng/tối cho mọi component mới.
3. `./gradlew :app:assembleVanillaDebug :app:lintVanillaDebug :app:testVanillaDebugUnitTest`: không có lỗi mới.
4. Emulator theo skill `run-jstore`:
   - **Màn cao:** `otoAutomative` với `wm density 240` (1280×1606 @240). Chụp For you (đầu trang và
     đã cuộn), Top charts, Categories, Updates, cả sáng lẫn tối. So với mock. Luôn `wm density reset`
     khi xong.
   - **Màn ngang:** `C5Automative` (2544×960 @240): rail vẫn hiện, không lỗi layout.
5. Bước merge thử: `git merge --no-commit upstream/master` trên một nhánh tạm, đếm xung đột, rồi huỷ.

## 8. Thứ tự triển khai (mỗi bước một commit)

0. Đo `screenHeightDp` trên `otoAutomative` @240dpi (D6).
1. Dimens mới + D5 (rail theo chiều rộng).
2. `JstoreAppTile` + `JstoreClusterCard`, móc vào `StreamCarousel`.
3. `JstoreHeroCard` + `heroApp` (kèm test), móc `showHero`.
4. `JstoreSegmentedTabs`, móc `AppsGamesScreen`.
5. `JstoreMainHeader` + `JstoreSearchField` + `JstoreBottomBar`, móc `MainScreen`.
6. `JstoreCard`, móc `UpdatesScreen`.
7. Kiểm thử §7, cập nhật phần "Known UI observations" trong skill `run-jstore`.
