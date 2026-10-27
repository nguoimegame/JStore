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

## Package và ký APK

| | JaecooApp | jstore |
|---|---|---|
| package | `com.example.jaecooapp` | `com.example.jstore` |
| service | `com.example.jaecooapp.JaecooInstallerService` | `com.aurora.store.jstore.JstoreUpdateService` |
| action bind | `com.example.jaecooapp.action.BIND_INSTALLER` | `com.example.jstore.action.BIND_UPDATES` |
| AIDL | `com.example.jaecooapp.installer.IJaecooInstaller`, `IInstallCallback` | `com.example.jstore.updates.IJstoreUpdates`, `IDownloadCallback` |

Hai APK **phải ký cùng một khóa**. Khóa không bao giờ được commit vào repo jstore (repo công khai): build jstore đọc khóa từ biến môi trường `JSTORE_KEYSTORE`, `JSTORE_KEYSTORE_PASSWORD`, `JSTORE_KEY_ALIAS`, `JSTORE_KEY_PASSWORD` (workflow phát hành), hoặc từ `signing.properties` ở máy cá nhân. Ai có khóa này thì cài được app im lặng trên xe qua JaecooApp.

## Quyền

Cả hai quyền dùng `protectionLevel="signature"`, và **cả hai app đều khai báo cả hai quyền**. Như vậy cài app nào trước cũng được; nếu khai báo ở một bên thì app cài trước sẽ không được cấp quyền.

| Quyền | Ai xin | Bảo vệ |
|---|---|---|
| `com.example.jaecooapp.permission.INSTALL_PACKAGES` | jstore | `JaecooInstallerService` |
| `com.example.jstore.permission.PLAY_UPDATES` | JaecooApp | `JstoreUpdateService` |

Quyền thôi chưa đủ. Mỗi service còn kiểm tra package của `Binder.getCallingUid()`, sai thì ném `SecurityException`.

## `IJaecooInstaller` (JaecooApp phục vụ)

```aidl
interface IJaecooInstaller {
    int apiVersion();                                         // 1
    void install(String packageName, in List<Uri> apks, IInstallCallback callback);
    void uninstall(String packageName, IInstallCallback callback);
}
oneway interface IInstallCallback {
    void onResult(int status, String message);
}
```

- `status`: `0` đã cài, `1` đã mở màn xác nhận của hệ thống (JaecooApp không phải Device Owner), `2` lỗi (`message` giải thích), `3` đã gỡ.
- `install` trả về ngay; `onResult` được gọi đúng một lần.
- `apks` là các URI `content://` trong `FileProvider` của jstore (authority `com.example.jstore.fileProvider`). jstore cấp quyền đọc cho `com.example.jaecooapp` bằng `grantUriPermission` trước khi gọi. JaecooApp chép các file vào cache của mình rồi cài như APK tự tải, vì vậy khi có `onResult` thì jstore xóa file và thu hồi quyền được ngay.

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

## Đổi phiên bản

`apiVersion()` hiện bằng `1` ở cả hai bên. Khi thêm hàm hoặc đổi nghĩa thì tăng số; bên gọi kiểm tra `apiVersion()` trước khi dùng hàm mới. Gặp bản cũ hơn thì coi như bên kia không có (jstore báo "Cần JaecooApp 4.8.0 trở lên", JaecooApp bỏ qua cập nhật từ Play).

## Phát hành jstore

jstore không có trong `apps.json`. JaecooApp cài và cập nhật jstore từ GitHub Release mới nhất của `nguoimegame/JStore`, và tên hai file đính kèm là một phần của hợp đồng:

- `jstore.apk`: APK đã ký bằng khóa chung.
- `jstore.apk.sha256`: mã SHA-256 của `jstore.apk`, chỉ gồm chuỗi hex.

JaecooApp kiểm tra sha256 rồi kiểm tra chứng chỉ ký của APK trùng với của chính mình trước khi cài.
