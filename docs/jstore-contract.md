<!--
  ~ SPDX-FileCopyrightText: 2026 nguoimegame
  ~ SPDX-License-Identifier: GPL-3.0-or-later
  -->
# Hợp đồng JaecooApp ↔ jstore

JaecooApp (mã nguồn đóng) và jstore (GPL-3.0, fork của Aurora Store ở `nguoimegame/JStore`) là **hai chương trình riêng**. Chúng chỉ trao đổi dữ liệu đơn giản qua Binder (AIDL) và `content://` URI, không dùng chung thư viện hay module Gradle. Tài liệu này có ở cả hai repo với nội dung giống nhau; đổi hợp đồng thì sửa cả hai bên cùng lúc.

Thiết kế: `docs/superpowers/specs/2026-10-26-jstore-split-design.md` và `2026-10-26-jstore-aurora-fork-design.md` (repo JaecooApp).

## Quy tắc

- JaecooApp **không** được thêm `gplayapi` hay bất kỳ thư viện GPL nào, không import class của jstore. jstore cũng không import code của JaecooApp.
- Hai bên chỉ chép **file `.aidl`** (cùng package, cùng nội dung; bản trong jstore có thêm dòng SPDX ở đầu) và các hằng số dưới đây.
- Mọi giá trị qua kênh: `String`, `int`, `long`, `boolean`, `Uri`, `Bundle`, `List` của chúng, và interface callback.
- Log (cảnh báo, lỗi, crash) không đi qua hợp đồng này: jstore đẩy chúng vào cửa nhận log của JaecooApp theo [log-sink-contract.md](log-sink-contract.md).

## Package và ký APK

| | JaecooApp | jstore |
|---|---|---|
| package | `com.example.jaecooapp` | `com.example.jstore` |
| service | `com.example.jaecooapp.JaecooInstallerService` | `com.aurora.store.jstore.JstoreUpdateService`, `com.aurora.store.jstore.embed.StoreUiService` |
| action bind | `com.example.jaecooapp.action.BIND_INSTALLER` | `com.example.jstore.action.BIND_UPDATES`, `com.example.jstore.action.BIND_STORE_UI` |
| AIDL | `com.example.jaecooapp.installer.IJaecooInstaller`, `IInstallCallback` | `com.example.jstore.updates.IJstoreUpdates`, `IDownloadCallback`; `com.example.jstore.store.IJstoreStoreUi`, `IJstoreStoreUiCallback` |

Hai APK **phải ký cùng một khóa**. Khóa không bao giờ được commit vào repo jstore (repo công khai): build jstore đọc khóa từ biến môi trường `JSTORE_KEYSTORE`, `JSTORE_KEYSTORE_PASSWORD`, `JSTORE_KEY_ALIAS`, `JSTORE_KEY_PASSWORD` (workflow phát hành), hoặc từ `signing.properties` ở máy cá nhân. Ai có khóa này thì cài được app im lặng trên xe qua JaecooApp.

## Quyền

Cả hai quyền dùng `protectionLevel="signature"`, và **cả hai app đều khai báo cả hai quyền**. Như vậy cài app nào trước cũng được; nếu khai báo ở một bên thì app cài trước sẽ không được cấp quyền.

| Quyền | Ai xin | Bảo vệ |
|---|---|---|
| `com.example.jaecooapp.permission.INSTALL_PACKAGES` | jstore | `JaecooInstallerService` |
| `com.example.jstore.permission.PLAY_UPDATES` | JaecooApp | `JstoreUpdateService`, `StoreUiService`, `StoreDialogActivity` |

Quyền thôi chưa đủ. Mỗi service còn kiểm tra package của `Binder.getCallingUid()`, sai thì ném `SecurityException`.

## `IJaecooInstaller` (JaecooApp phục vụ)

```aidl
interface IJaecooInstaller {
    int apiVersion();                                         // 2
    void install(String packageName, in List<Uri> apks, IInstallCallback callback);
    void uninstall(String packageName, IInstallCallback callback);
    // Added in version 2. Declared last: transaction codes follow declaration order.
    boolean canManageApps();                                  // Device Owner or Profile Owner
    List<Bundle> listApps();
    boolean setHidden(String packageName, boolean hidden);
}
oneway interface IInstallCallback {
    void onResult(int status, String message);
}
```

- `status`: `0` đã cài, `1` đã mở màn xác nhận của hệ thống (JaecooApp không phải Device Owner), `2` lỗi (`message` giải thích), `3` đã gỡ.
- `install` trả về ngay; `onResult` được gọi đúng một lần.
- `canManageApps` cho biết JaecooApp có quyền ẩn app không. `listApps` trả đúng danh sách `AppVisibilityManager.listApps`: chỉ app người dùng, không có JaecooApp và launcher. Mỗi `Bundle`: `packageName`, `label` (String), `versionCode` (long), `versionName` (String), `hidden` (boolean), `protected` (boolean), `icon` (byte[] PNG, chỉ có khi `hidden`, vì app ẩn trông như chưa cài). `setHidden` trả `false` khi bị từ chối hoặc lỗi.
- `apks` là các URI `content://` trong `FileProvider` của jstore (authority `com.example.jstore.fileProvider`). jstore cấp quyền đọc cho `com.example.jaecooapp` bằng `grantUriPermission` trước khi gọi. JaecooApp chép các file vào cache của mình rồi cài như APK tự tải, vì vậy khi có `onResult` thì jstore xóa file và thu hồi quyền được ngay.

### Luật an toàn của JaecooApp

JaecooApp tự thực thi, không tin vào jstore:

- **Package được bảo vệ**: `com.example.jaecooapp`, `com.example.jstore`. `setHidden` trả `false` và `uninstall` trả `status 2` cho chúng. Nếu không, người lái ẩn hoặc gỡ jstore thì mất cả tab Kho ứng dụng mà không còn công tắc để hiện lại. `listApps` vẫn trả jstore với `protected = true`.
- **Không cài JaecooApp qua jstore**: `install("com.example.jaecooapp", …)` bị từ chối (`status 2`). JaecooApp tự cập nhật bằng OTA.
- **App đồng hành** (`com.example.jaecooassistant`, `com.example.jaecoovoice`, `com.example.jstore`): `install` kiểm chứng chỉ ký của APK gốc trùng với JaecooApp. Cài xong JaecooAssistant hoặc JaecooVoice, JaecooApp chạy `AssistantSetup.ensure()`.
- **sha256**: jstore kiểm `sha256` trong `apps.json` trước khi gọi `install`. JaecooApp không kiểm lại hash.

## `IJstoreUpdates` (jstore phục vụ)

```aidl
interface IJstoreUpdates {
    int apiVersion();                                         // 1
    List<Bundle> latestVersions(in List<String> packages);
    void download(String packageName, IDownloadCallback callback);
    void release(String packageName);
}
oneway interface IDownloadCallback {
    void onProgress(int percent);
    void onReady(in List<Uri> apks);
    void onError(String message);
}
```

- `latestVersions` chặn luồng. Mỗi `Bundle` gồm: `packageName` (String), `versionCode` (long), `versionName` (String), `isFree` (boolean). Trả về rỗng khi chưa đăng nhập Google trong jstore hoặc Play lỗi.
- `download` trả về ngay. jstore tải về `cache/jaecoo/<package>/` (không qua hàng đợi tải của jstore), gọi `onProgress` (0–100), rồi `onReady` với URI đã cấp quyền đọc cho JaecooApp, hoặc `onError` với thông báo đã dịch theo ngôn ngữ của jstore. Mỗi package chỉ tải một lần tại một thời điểm.
- JaecooApp cài xong (thành công hay lỗi) phải gọi `release(packageName)` để jstore xóa file và thu hồi quyền.

## `IJstoreStoreUi` (jstore phục vụ): màn Kho ứng dụng nhúng trong JaecooApp

jstore vẽ toàn bộ tab Kho ứng dụng (ba tab con Jaecoo, Google Play, Quản lý app); JaecooApp nhúng qua `SurfaceControlViewHost` (Android 11 trở lên), cùng cách tab Giọng nói nhúng JaecooAssistant. Service `StoreUiService` được bảo vệ bằng quyền `PLAY_UPDATES` và kiểm package của người gọi (`com.example.jaecooapp`).

```aidl
interface IJstoreStoreUi {
    int apiVersion();                                         // 1
    Bundle attach(IBinder hostToken, int displayId, int width, int height,
                  in Bundle hostConfig, IJstoreStoreUiCallback callback); // "surface_package"
    void relayout(int width, int height, in Bundle hostConfig);
    void showSection(String section);                         // "jaecoo" | "play" | "manage"
    void detach();
}
oneway interface IJstoreStoreUiCallback {
    void onStartActivity(in Intent intent);
    void onRequestPhoneUpload();
    void onRequestJstoreUpdate();
    void onShowToast(String message);
}
```

`hostConfig`: `language` (String), `night_mode` (boolean), `screen_height_dp` (int; từ 1024 trở lên là màn cao), `server_urls` (ArrayList de String, theo thứ tự thử), `initial_section` (String, tùy chọn).

- `onStartActivity`: JaecooApp chỉ khởi chạy intent có component thuộc `com.example.jstore`, launch intent (`ACTION_MAIN` + `CATEGORY_LAUNCHER`) của app đã cài, `Settings.ACTION_SYNC_SETTINGS`, hoặc `ShellActivity` của JaecooApp với extra `tab=system`. Còn lại bỏ qua; cờ `FLAG_GRANT_*` bị gỡ trước khi mở.
- `onRequestPhoneUpload`: JaecooApp mở hộp thoại cài APK từ điện thoại (máy chủ HTTP nằm ở JaecooApp).
- `onRequestJstoreUpdate`: JaecooApp gỡ màn nhúng, hiện tiến trình, cài bản jstore mới từ GitHub Release rồi gắn lại.
- `onShowToast`: JaecooApp hiện thông báo thay jstore.

## Đổi phiên bản

Ba số riêng, không dùng chung: `IJaecooInstaller` = `2`, `IJstoreUpdates` = `1`, `IJstoreStoreUi` = `1`. Khi thêm hàm hoặc đổi nghĩa thì tăng số của đúng interface đó; bên gọi kiểm tra `apiVersion()` trước khi dùng hàm mới. Gặp bản cũ hơn thì coi như bên kia không có (jstore báo "Cần JaecooApp 4.8.0 trở lên" hoặc tab Quản lý app hiện "Cần cập nhật JaecooApp" khi installer dưới 2; JaecooApp bỏ qua cập nhật từ Play; JaecooApp hiện panel cập nhật jstore khi không có `StoreUiService`). Thêm hàm vào cuối interface là an toàn với bản cũ.

## Phát hành jstore

jstore không có trong `apps.json`. JaecooApp cài và cập nhật jstore từ GitHub Release mới nhất của `nguoimegame/JStore`, và tên hai file đính kèm là một phần của hợp đồng:

- `jstore.apk`: APK đã ký bằng khóa chung.
- `jstore.apk.sha256`: mã SHA-256 của `jstore.apk`, chỉ gồm chuỗi hex.

JaecooApp kiểm tra sha256 rồi kiểm tra chứng chỉ ký của APK trùng với của chính mình trước khi cài.
