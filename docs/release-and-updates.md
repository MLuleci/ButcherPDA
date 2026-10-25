# Releases and self-update

## Versions (`app/build.gradle.kts`)

Both are read from git at build time:
- **`versionCode`** is `git rev-list --count HEAD`, the number of commits. Every commit on master is therefore newer than the last, which self-update relies on.
- **`versionName`** is `git rev-parse --short HEAD`. Settings shows it, and it's the release tag.
- **Without git** they fall back to `1` and `"dev"`.
- **Build releases from a full clone:** a shallow clone undercounts the commits.

## Signing

- **The key:** release builds are signed when `keystore.properties` exists in the project root. It names the key with `storeFile` (absolute, or relative to the root), `storePassword`, `keyAlias` and `keyPassword`.
- **Without the file:** release builds come out unsigned and can be signed by hand with `apksigner`.
- **Keeping it out of git:** `.gitignore` covers `keystore.properties`, `*.jks` and `*.keystore`. The owner's private key never goes in the repo.
- **v1 and v2 signing are both on:** Android 6 only checks v1, and v2 is for Android 7 and later.

Check a build with `apksigner verify --print-certs app-release.apk`: it should show the owner's certificate and "Verified using v1 scheme: true".

## R8

Release builds use `isMinifyEnabled = true`, `isShrinkResources = true` and `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))`.

- **Why not the new block:** AGP 9.2's `optimization { enable = true }` fails without the undocumented `android.r8.gradual.support` flag, and the block only becomes stable in AGP 9.3. Switch to it after the AGP update.
- **Keep rules:** the SDK's are in `app/src/main/keepRules/rules.keep` ([hardware.md](hardware.md)). kotlinx.serialization (models, saved state, nav routes), Navigation and Retrofit ship their own rules, so none are added for them.
- **Testing:** check a release build on the PDA with a search, Details, a submit, a scan and a print. Those are the code paths R8 is most likely to break.

## Publishing a release

1. Commit, then build the release from a full clone: `./gradlew assembleRelease`, or the release variant in Android Studio.
2. Verify the signature, as above.
3. On <https://github.com/MLuleci/ButcherPDA>, create a release **tagged with the short hash** (e.g. `d842999`).
4. Attach the APK, named exactly `ButcherPDA-<versionCode>-<versionName>.apk` (e.g. `ButcherPDA-60-d842999.apk`). The app ignores any other name.

**The first switch from a debug build:** a debug build is signed with Android Studio's debug key, so a release build can't install over it. The first time, uninstall the debug app on the PDA, install the release, and enter Settings again. After that, each update installs over the previous one, as long as it's signed with the same key.

## Self-update (Settings → "Check for updates")

- **Checking** (`data/UpdateRepository.kt`):
  - It sends `GET https://api.github.com/repos/MLuleci/ButcherPDA/releases/latest` with `Accept: application/vnd.github+json`, and no token, since the repo is public.
  - `GithubRelease.newerThan(installed)` picks the highest-code asset matching `ButcherPDA-(\d+)-([0-9a-f]+)\.apk` that's above `BuildConfig.VERSION_CODE`.
  - A 404, meaning nothing released yet, counts as up to date. Other errors and bad bodies throw `UpdateException`.
- **Downloading:** the APK is streamed to `cacheDir/updates/ButcherPDA-<code>.apk`, replacing earlier downloads, with progress shown. A file that's shorter than the asset's `size` is deleted and refused. Leaving Settings cancels the download.
- **Checking the file** (`data/UpdateInstaller.check`, using the API 23 calls `getPackageArchiveInfo(path, GET_SIGNATURES)` and `getPackageInfo`):
  - It must be this package, at the expected `versionCode`, signed with the installed app's key.
  - Some Android versions don't read a downloaded archive's signatures. Then the check is skipped, and the system installer still refuses a mismatch.
  - A package-manager exception counts as an unreadable file.
  - A refused file is deleted, and a clear error is shown.
- **Installing:**
  - The app opens `ACTION_INSTALL_PACKAGE` with a `FileProvider` URI. The authority is `${applicationId}.updates`, with paths in `res/xml/update_paths.xml`, `cache-path updates/`.
  - On Android 6 the installer asks to allow "Unknown sources" itself if it's off. `REQUEST_INSTALL_PACKAGES` in the manifest covers Android 8+.
  - A firmware without an installer gets a toast instead of a crash.
  - If the operator cancels the installer, "Download and install" reopens it without downloading again (`UpdateState.Ready`).
- **Network:** the update client has its own OkHttp, without the ERP interceptor, sharing the bundled extra roots.
  - **`api.github.com`** chains to Sectigo E46, then USERTrust ECC (`res/raw/usertrust_ecc.pem`, SHA-256 `4F:F4:60:D5:…:AD:7A`).
  - **The download host** (`release-assets.githubusercontent.com`) chains to Let's Encrypt, then ISRG Root X1.
- **State:** the UI state is `UpdateState`: `Idle`, `Checking`, `UpToDate`, `Available`, `Downloading`, `Ready` or `Failed(UpdateError)`. The installer is opened through `SettingsUiEffect.Install`.
- **Tests:** `UpdateRepositoryTest` covers parsing, the 404, errors, the download and a short file.
