# JStore

Google Play client (`com.example.jstore`, namespace `com.aurora.store`, GPL-3.0) cho head unit Jaecoo J7, fork từ Aurora Store và chạy cùng `JaecooApp` (`../JaecooApp`). Yêu cầu JDK 21 (`minSdk = 29`).

## Ranh giới & giao tiếp với JaecooApp

- **Cô lập GPL-3.0:** không import code của `JaecooApp` và không đưa code/thư viện GPL của `jstore` sang `JaecooApp`.
- **Hợp đồng AIDL & cài đặt:** trao đổi qua Binder và `FileProvider` theo [docs/jstore-contract.md](docs/jstore-contract.md) (`JaecooInstallerService` ↔ `JstoreUpdateService`, quyền `protectionLevel="signature"`). Đổi hợp đồng thì phải sửa cả hai repo.
- **Log:** dùng `JstoreLog` (`com.aurora.store.jstore`) cho code `jstore` để đẩy warning, error và crash về cửa nhận log của `JaecooApp` theo [docs/log-sink-contract.md](docs/log-sink-contract.md) (file giống nhau ở `JaecooApp`, `JaecooVoice`, `JStore`). Không tự mở kết nối lên logserver.
- **Đăng nhập & cài đặt:** đăng nhập Google qua microG (không dùng Aurora anonymous dispenser); mặc định cài qua `Installer.JAECOO` khi có `JaecooApp` (`InstallPermission.kt`).

## Cô lập code & đồng bộ upstream

- Code riêng đặt trong package `com.aurora.store.jstore` (UI head unit ở `com.aurora.store.jstore.ui`).
- Resource riêng đặt trong `dimens_jstore.xml`, `bools_jstore.xml`, và `strings_jstore.xml` (chuỗi mới phải có đủ trong cả 11 file `values*/strings_jstore.xml`: mặc định + `ar`, `es`, `fr`, `in`, `kk`, `pt`, `ru`, `th`, `vi`, `zh`).
- Khi bắt buộc sửa file upstream của Aurora, đánh dấu comment `// jstore: ...` và giữ diff tối thiểu để giảm conflict.
- Nhánh `master` bám upstream `whyorean/AuroraStore`; nhánh mặc định là `jstore`. Khi lấy thay đổi upstream: cập nhật `master` rồi `git merge master` vào `jstore`.

## UI head unit

- Phân tách theo chiều cao màn hình bằng resource qualifiers (không hardcode kích thước theo màn hình đơn lẻ):
  - `< 1024dp` (`res/values/`): dùng left rail (`@bool/jstore_nav_rail = true`).
  - `≥ 1024dp` (`res/values-h1024dp/`): dùng bottom bar (`@bool/jstore_nav_rail = false`).
- Touch target tối thiểu `64dp` (`@dimen/jstore_touch_min`).
- Spec UI: [docs/superpowers/specs/2026-10-27-jstore-headunit-ui-design.md](docs/superpowers/specs/2026-10-27-jstore-headunit-ui-design.md).

## Ký APK & phát hành

- APK phải ký cùng khóa với `JaecooApp` (qua `signing.properties` ở máy local hoặc CI secrets) kèm key rotation lineage tại [signing/jaecoo.lineage](signing/jaecoo.lineage); không sửa lineage và không commit keystore/mật khẩu vào repo công khai.
- Phát hành qua tag `vMAJOR.MINOR.PATCH` trên nhánh `jstore` theo [.github/workflows/jstore-release.yml](.github/workflows/jstore-release.yml) (workflow kiểm tra chữ ký/lineage và xuất `jstore.apk`, `jstore.apk.sha256`).

## Build & kiểm tra

- Build debug: `./gradlew.bat :app:assembleVanillaDebug`
- Kiểm tra bắt buộc trước khi hoàn thành thay đổi: `./gradlew.bat ktlintCheck testVanillaDebugUnitTest`
- Chạy và kiểm tra UI trên emulator (`otoAutomative`): dùng skill `run-jstore` ([.agent/skills/run-jstore/SKILL.md](.agent/skills/run-jstore/SKILL.md)).
