# jstore: cụm "Ứng dụng cho Jaecoo" lấy từ server Jaecoo

- Ngày: 2026-11-21
- Phạm vi: tab Ứng dụng › Cho bạn; trang chi tiết app; tải & cài; tab Cập nhật.

## 1. Mục đích

Chủ xe mở jstore là thấy ngay những app hợp với head unit Jaecoo: app trong kho Jaecoo và các app Play
do người quản lý server chọn. Danh sách và thông tin app do **server Jaecoo** quyết định, đổi danh sách
không phải phát hành lại APK.

Quy tắc dữ liệu (do người dùng chốt):

- Mọi app trong cụm đều đến từ server Jaecoo.
- Mục server chỉ có `packageName` (và có thể `name`): mọi chi tiết lấy từ CH Play.
- Mục server có thêm thông tin: **thông tin server được ưu tiên** hơn Play, trường nào server bỏ trống
  thì lấy của Play. Ví dụ quan trọng nhất: `downloadUrl` → tải APK từ server thay cho Play.

Thành công khi:

1. Trên tab Ứng dụng › Cho bạn, cụm "Ứng dụng cho Jaecoo" nằm ngay sau thẻ nổi bật, trước cụm Play đầu
   tiên ("Ứng dụng phổ biến").
2. Bấm app Play mở trang chi tiết Play, với tên/icon/mô tả theo server nếu server có.
3. App có `downloadUrl` trên server được tải từ server, hiện tiến độ ở tab Tải xuống, cài qua
   `Installer.JAECOO` như mọi app khác.
4. App chỉ có trên server (Play không có) vẫn xem chi tiết, cài, mở được.
5. (Đợt 2) Bản mới hơn trên server của app đã cài hiện trong tab Cập nhật và cập nhật từ server.

## 2. Mục tiêu / không làm / không được xảy ra

**Mục tiêu:** như §1.

**Không làm:**

- Không hiện cụm ở tab Trò chơi.
- Không đổi hợp đồng AIDL với JaecooApp (`docs/jstore-contract.md`).
- Không thêm giao diện chọn server; không thêm việc người dùng tự ghim app.
- Không đụng tới tab Kho ứng dụng nhúng trong JaecooApp (`StoreScreen`), ngoài việc dùng chung cache.

**Không được xảy ra:**

- Trang Cho bạn chậm đi hoặc trống vì server Jaecoo chậm/không tới được: cụm Jaecoo tải riêng, các cụm
  Play hiện như cũ dù cụm Jaecoo lỗi.
- Nới kiểm tra toàn vẹn cho file tải từ Play (chỉ file của server Jaecoo được thiếu mã băm, xem 5.3).
- Tab Cập nhật của jstore và trình quản lý app trong JaecooApp báo khác nhau cho cùng một app.
- Vòng lặp cập nhật: app vừa cập nhật từ server lại báo có bản cập nhật (vì so với `versionCode` Play).

## 3. Ràng buộc

- Code mới trong `com.aurora.store.jstore` (UI ở `com.aurora.store.jstore.ui`); sửa file upstream thì
  diff tối thiểu và đánh dấu `// jstore: ...`.
- Chuỗi mới có đủ trong 11 file `values*/strings_jstore.xml`.
- Log bằng `JstoreLog`.
- Touch target ≥ 64dp; cụm dùng lại `JstoreClusterCard` + `JstoreAppTile` để giống các cụm khác.
- Màn Play chính **không** nhận `server_urls`; chỉ màn nhúng `StoreScreen` nhận qua `StoreHostConfig`.
  `StoreScreen` lưu catalogue + server cuối cùng tải được vào `cacheDir/jstore_store/`
  (`apps.json`, `server.txt`, xem `StoreCatalog.FileCatalogCache`).
- `DownloadWorker.verifyFile` từ chối file không có `sha256`/`sha1` (trả false → `VerificationFailed`).
  Tab Kho ứng dụng hiện cài file catalogue không có mã băm (`StoreCatalog.sha256Matches` trả true khi rỗng);
  người dùng chốt giữ cùng hành vi đó cho jstore.
- Trình quản lý app trong JaecooApp (`JstoreUpdateService` → `PlayUpdates`, danh sách cập nhật từ
  `ManageUpdates.pick`) đã coi **mọi** package trong `apps.json` là do catalogue quản lý: lấy cập nhật từ
  catalogue (nếu có `downloadUrl` và tên phiên bản mới hơn), không bao giờ hỏi Play.
- `apps.json` chỉ có `version` dạng tên (`"2.1.0"`), không có `versionCode`; người dùng chọn **so theo
  tên phiên bản** (như `ManageUpdates.isVersionNewer`), không thêm `versionCode` vào server.

## 4. Cách làm (đã chọn: A — gắn vào đường đi sẵn có của Aurora)

| Cách | Mô tả | Đánh đổi |
|---|---|---|
| **A (chọn)** | Ghép dữ liệu server + Play thành `App` của gplayapi; file tải = link server (`PlayFile`) giống `SelfUpdate.toApp`; tải qua `DownloadWorker`; cập nhật thêm vào `UpdateWorker` giống self-update | Sửa vài file upstream (mỗi chỗ vài dòng). App server hoạt động như app Play ở mọi tab (Tải xuống, Cập nhật, cài qua JaecooApp) |
| B | Đường đi riêng: cụm, chi tiết, tải (`StoreOps`), cài (`JaecooConnection`) tự làm | Ít đụng upstream nhưng app server không có trong tab Tải xuống/Cập nhật; phải làm danh sách cập nhật thứ hai |
| C (loại) | Lấy chi tiết qua JaecooApp | Phải đổi AIDL ở hai repo, không được thêm gì |

Người dùng chọn A vì app từ server phải hoạt động y như app Play.

Tiền lệ trong code: `DownloadWorker` dùng `download.fileList` nếu không rỗng, chỉ `purchase()` Play khi
rỗng (dòng ~173); `SelfUpdate.toApp()` đã biến một mục feed ngoài thành `App` có `fileList` là URL và đi
trọn đường Update → Download → Install.

## 5. Thiết kế

### 5.1 Server

Thêm `<server>/api/featured.json`: mảng các mục **cùng định dạng `apps.json`**, chỉ `packageName` là bắt
buộc, thứ tự trong file là thứ tự hiển thị:

```json
[
  { "packageName": "com.spotify.music" },
  { "packageName": "com.waze", "downloadUrl": "/apk/waze-hu.apk", "version": "5.1.0", "sha256": "…" }
]
```

Thiếu file hoặc file hỏng: coi như danh sách chọn rỗng (vẫn hiện app kho Jaecoo).

### 5.2 Tải dữ liệu — `jstore/featured/` (tên gói là đề xuất)

1. Lấy server: thử server trong `server.txt` của cache `jstore_store` (nếu có).
   Chưa có cache (chưa từng mở tab Kho ứng dụng) → không hiện cụm.
2. Tải `apps.json` (lỗi → dùng `apps.json` cache) và `featured.json` (lỗi → cache riêng của nó, cùng
   thư mục). Dùng lại `StoreCatalog.parse` / `resolveDescription` / `resolveUrl` (cần cho `parse` bỏ qua
   mục thiếu field, hiện đã dùng `optString` nên ổn).
3. Danh sách = toàn bộ `apps.json` (theo thứ tự file) **rồi** `featured.json`; bỏ trùng theo
   `packageName`, giữ lần xuất hiện đầu (bản kho Jaecoo thắng). Bỏ mục `packageName` rỗng.
4. Gọi Play một lần cho cả danh sách: `AppDetailsHelper.getAppByPackageName(List)`. Play lỗi (mất mạng,
   hết phiên) → coi như Play không có app nào, không làm hỏng cụm.
5. Ghép mỗi mục thành một `App` (xem 5.3). Mục không có trên Play **và** không có `downloadUrl` → bỏ.
6. Kết quả giữ trong bộ nhớ (singleton/repository được Hilt inject), có hàm làm mới; tải lại khi trang
   Cho bạn mở lần đầu trong phiên và khi người dùng kéo làm mới (nếu trang có).

Phần 2–5 là logic thuần (đầu vào: chuỗi JSON + danh sách `App` Play) để unit test được.

### 5.3 Quy tắc ghép (server ưu tiên)

| Trường server | Ghi vào `App` | Khi server bỏ trống |
|---|---|---|
| `name` | `displayName` | Play |
| `iconUrl` (resolve theo server) | `iconArtwork.url` | Play |
| `description` (theo ngôn ngữ, `resolveDescription`) | `shortDescription` + `description` | Play |
| `version` | `versionName` | Play |
| `fileSize` | `size` (`StoreOps.parseEstimatedBytes`) | Play |
| `downloadUrl` + `sha256` | `fileList = [PlayFile(url, name, size, sha256)]`, app đánh dấu "nguồn server" | Play (không đụng `fileList`) |
| `category` | không dùng ở đợt này | — |

Đánh dấu "nguồn server": giữ một bản đồ `packageName → ServerApp` trong repository để các bước sau
(chi tiết, cập nhật) biết app nào tải từ server, thay vì nhét cờ vào `App`.

**App nào thuộc server** (quyết định của người viết, để khớp `ManageUpdates` sẵn có):

- Mọi package trong `apps.json`: thuộc server, có hay không có `downloadUrl`. Không có `downloadUrl` thì
  không cập nhật từ đâu cả (như `ManageUpdates` hôm nay), nhưng vẫn cài được từ Play nếu Play có.
- Mục `featured.json` có `downloadUrl`: thuộc server.
- Mục `featured.json` không có `downloadUrl`: thuộc Play (chỉ mượn tên/icon/mô tả từ server).

**Thiếu `sha256`** (người dùng chốt: cho phép, như tab Kho ứng dụng): `DownloadWorker.verifyFile`
(upstream, `// jstore:`) coi file là hợp lệ khi không có mã băm **và** URL của file là link server Jaecoo
đã ghép (repository giữ tập URL này). File Play vẫn kiểm tra như cũ. Có `sha256` thì vẫn kiểm tra. Ghi
`JstoreLog.w` mỗi lần cài file không có mã băm.

`versionCode` cho app nguồn server (vì server chỉ có tên phiên bản):

- Chưa cài: dùng `versionCode` của Play nếu có, không thì `1`.
- Đã cài và `isVersionNewer(server.version, installed.versionName)`: `installedVersionCode + 1`.
- Đã cài và không mới hơn: `installedVersionCode` (→ không phải "Updatable").

`versionCode` này chỉ để Aurora đặt thư mục tải và quyết định trạng thái nút; Android vẫn cài theo
`versionCode` thật trong APK.

### 5.4 Cụm trên trang Cho bạn

- `ForYouPage.kt` (upstream, `// jstore:`): chỉ với `pageType` của tab Ứng dụng, lấy danh sách từ repository
  (StateFlow) và truyền vào `StreamCarousel`.
- `StreamCarousel.kt` (upstream, `// jstore:`): thêm tham số kiểu
  `jstoreTopCluster: (LazyListScope.() -> Unit)?` hoặc một `List<App>` + tiêu đề; vẽ ngay sau
  `jstore_hero`, trước `displayClusters`. Không có app → không vẽ gì.
- Cụm: `JstoreClusterCard(title = "Ứng dụng cho Jaecoo", onSeeAll = null)` + hàng `JstoreAppTile`.
  App không có số sao (chỉ có trên server) thì dòng sao để trống.
- Thẻ nổi bật (`heroApp`) và `deduplicateClusters` giữ nguyên; app trùng giữa cụm Jaecoo và cụm Play được
  phép (không lọc chéo).
- Chuỗi mới: `jstore_featured_title` ("Ứng dụng cho Jaecoo") × 11 ngôn ngữ.

### 5.5 Bấm vào app & trang chi tiết

- App có trên Play: mở trang chi tiết Play như hiện nay (cùng `onAppClick`).
  `AppDetailsViewModel.fetchAppDetails` (upstream, `// jstore:`): sau khi lấy app từ Play, nếu repository có
  mục server cho package này thì áp quy tắc 5.3 lên `_app`. Từ đó nút Cài/Cập nhật → `enqueueDownload` →
  `Download.fromApp` mang `fileList` server → `DownloadWorker` không `purchase`, tải từ server.
  Trạng thái nút (Cài / Cập nhật / Mở) cho app nguồn server dựa trên `versionCode` tính ở 5.3, không dựa
  trên `versionCode` Play (chặn vòng lặp cập nhật).
- App chỉ có trên server (quyết định của người viết, thay cho trang riêng): vẫn mở **trang chi tiết Play**.
  Trong `fetchAppDetails`, khi Play không trả được app mà repository có mục server, dựng `_app` từ dữ liệu
  server (như `SelfUpdate.toApp`) thay vì báo lỗi. Các phần chỉ Play mới có (đánh giá, data safety, gợi ý)
  đã chạy sau và tự bỏ qua khi lỗi; bỏ hẳn các lệnh gọi đó cho app chỉ server để khỏi gọi Play vô ích.
  Lợi ích: app chỉ server mở được từ mọi nơi (cụm, Tải xuống, Cập nhật, Đã cài) mà không phải rẽ nhánh từng
  chỗ, không cần route mới.

### 5.6 Đợt 2 — cập nhật từ server

Một quy tắc chọn nguồn dùng chung cho **cả hai nơi** (người dùng chốt: giống nhau), viết một lần trong
jstore và gọi từ cả hai:

- Package thuộc server (theo 5.3): cập nhật chỉ từ server khi có `downloadUrl` và tên phiên bản server mới
  hơn bản đang cài; không bao giờ lấy bản Play.
- Package khác: Play như cũ.

**Tab Cập nhật của jstore** — `UpdateWorker.checkUpdates` (upstream, `// jstore:`):

1. Lấy dữ liệu server qua repository. Không tới được server → dùng `apps.json`/`featured.json` trong cache.
   Không có cache → không biết package nào thuộc server, để Play như cũ (trường hợp này chỉ xảy ra trước
   lần đầu mở tab Kho ứng dụng).
2. Bỏ khỏi danh sách gửi Play các package thuộc server.
3. Thêm `Update.fromApp(context, mergedApp)` cho package thuộc server có bản mới hơn; chèn sau các bộ lọc
   giống `selfUpdate`, nhưng vẫn tôn trọng blacklist và "bỏ qua bản này" (`isIgnoredBy`) của người dùng.
   Không áp `isAuroraOnlyFilterEnabled` cho các package này.
- `hasValidCert`: dựng `certificateSetList` từ chứng chỉ của app đang cài như `SelfUpdate.toApp`, để
  update không bị lọc. Nếu APK server ký khác app đang cài thì Android từ chối khi cài; lỗi hiện như
  mọi lỗi cài khác.

**Trình quản lý app trong JaecooApp** — `ManageUpdates.pick` (jstore): danh sách catalogue đưa vào là
`apps.json` **cộng** các mục `featured.json` có `downloadUrl` (bỏ trùng, `apps.json` thắng). `ManagePanel` /
`JstoreUpdateService` cần tải thêm `featured.json` (cùng cache) khi dựng danh sách đó.

Hai đường cài (tab Kho ứng dụng qua `StoreOps`, jstore qua `DownloadWorker`) có thể cùng cài một package.
Quyết định của người viết: chấp nhận ở đợt này, vì JaecooApp xử lý từng yêu cầu cài; ghi lại để theo dõi
khi test trên xe.

## 6. Sửa file upstream (dự kiến)

| File | Thay đổi |
|---|---|
| `compose/composable/StreamCarousel.kt` | tham số + vẽ cụm Jaecoo sau hero |
| `compose/ui/apps/ForYouPage.kt` | truyền dữ liệu cụm khi là tab Ứng dụng |
| `viewmodel/details/AppDetailsViewModel.kt` | áp dữ liệu server sau khi lấy app Play; dựng app từ server khi Play không có |
| `data/work/DownloadWorker.kt` | cho phép file server Jaecoo thiếu mã băm |
| `data/work/UpdateWorker.kt` (đợt 2) | thêm/thay update nguồn server |

File jstore cần sửa ở đợt 2: `ManageUpdates.kt`, `ManagePanel.kt`, nơi `JstoreUpdateService` dựng catalogue.

## 7. Kiểm thử

- Unit test (logic thuần): ghép danh sách + bỏ trùng + thứ tự; quy tắc ghép từng trường; bỏ mục không có
  Play và không `downloadUrl`; `downloadUrl` thiếu `sha256`; tính `versionCode` 3 trường hợp; `featured.json`
  hỏng/thiếu; lựa chọn update đợt 2 (thay bản Play, không mới hơn).
- `./gradlew.bat ktlintCheck testVanillaDebugUnitTest`.
- Emulator (skill `run-jstore`): cụm hiện đúng vị trí; bấm app Play / app chỉ server; cài từ server qua
  JaecooApp; server không tới được → các cụm Play vẫn hiện.

## 8. Để người làm tự quyết

Tên gói/lớp; cách tiêm repository (Hilt); hình dạng tham số thêm vào `StreamCarousel`; bố cục trang chi tiết
chỉ server (miễn theo `JstoreTheme`, 64dp); thời điểm làm mới cache; tên file cache `featured.json`.
