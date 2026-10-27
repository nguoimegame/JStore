# jstore

Google Play client for the Jaecoo J7 head unit, used together with **JaecooApp**.

jstore is a fork of [Aurora Store](https://gitlab.com/AuroraOSS/AuroraStore) by Aurora OSS,
licensed under the **GNU General Public License v3.0** (see [LICENSE](LICENSE) and
[LICENSES/](LICENSES)). It is not affiliated with, endorsed by or supported by Aurora OSS, and
it has no approval, sponsorship or authorization from Google. Please do not report jstore
problems to the Aurora Store project.

## What is different from Aurora Store

- **Installs through JaecooApp.** jstore downloads the APKs; JaecooApp installs and updates
  them silently with its Device Owner rights. The two apps talk only over the interface in
  [docs/jstore-contract.md](docs/jstore-contract.md), and JaecooApp's app manager can ask
  jstore for Google Play updates.
- **Requires microG.** Sign-in goes through the Google account in microG, including
  repackaged builds such as MicroG RE. There is no anonymous login: Aurora's token dispenser
  only serves Aurora Store itself.
- **Head-unit layout.** JaecooApp's colours, larger text and touch targets, and a navigation
  rail on short screens.
- **Fewer features.** Reviews, beta programs, Exodus Privacy, Plexus, manual downloads, other
  installers (root, Shizuku, services, …), self-update and the huawei/preload flavours are
  switched off.
- **Updated through JaecooApp.** Every release is published here, and JaecooApp installs the
  latest one.

## Branches

- `master` mirrors upstream Aurora Store and is only used to bring its changes in.
- `jstore` is the default branch with all jstore changes; release tags live here.

To take upstream changes: sync `master` with `whyorean/AuroraStore`, then
`git merge master` into `jstore`. jstore's own code sits in `com.aurora.store.jstore`, its
strings in `strings_jstore.xml`, and its build additions at the end of `app/build.gradle.kts`,
so conflicts stay in a few known places.

## Releases

Pushing a tag `vMAJOR.MINOR.PATCH` on `jstore` runs
[.github/workflows/jstore-release.yml](.github/workflows/jstore-release.yml). It checks,
builds and signs the APK, refuses to publish unless it carries JaecooApp's certificate, and
attaches `jstore.apk` and `jstore.apk.sha256` to a GitHub Release. The tag is the complete
corresponding source of that APK.

The repository needs these secrets: `JSTORE_KEYSTORE_BASE64`, `JSTORE_KEYSTORE_PASSWORD`,
`JSTORE_KEY_ALIAS`, `JSTORE_KEY_PASSWORD`. The key never goes into the repository.

## Building locally

```
./gradlew assembleVanillaDebug
./gradlew ktlintCheck testVanillaDebugUnitTest
```

Gradle needs JDK 21. To build a jstore JaecooApp accepts, put the key in `signing.properties`
at the repository root (ignored by git):

```
STORE_FILE=/path/to/key.keystore
KEY_ALIAS=...
KEY_PASSWORD=...
```

## Credits

jstore is built on Aurora Store and the projects it credits: YalpStore, AppCrawler, Raccoon
and SAI.
