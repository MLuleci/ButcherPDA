# ButcherPDA

An Android app for the handheld PDAs in a butcher's production room. Operators pick a recipe (BOM), scan the ingredient batches they used, and submit a Manufacture Stock Entry to an ERPNext server. The app then prints a label for the new batch. They can also search batches, open a batch's details, and reprint its label.

The PDA is a "QS" model with a built-in barcode scanner and thermal label printer, driven by a vendor SDK (`app/libs/qspdasdk.jar`). It runs **Android 6.0 (API 23)**, which is a hard requirement: `minSdk = 23`, and every API, library and certificate choice has to work there.

## Start here

| File | What's in it |
|---|---|
| [architecture.md](architecture.md) | Code layout, screens and navigation, state and saving, conventions |
| [business-rules.md](business-rules.md) | Validation, tolerance and coverage, batch lookup, request IDs, expiry, search filters |
| [server-api.md](server-api.md) | The ERPNext server and its custom `my_app`, every endpoint, error handling, Frappe quirks |
| [hardware.md](hardware.md) | Vendor SDK facts verified from bytecode, the scanner, the printer, packaging, device quirks |
| [release-and-updates.md](release-and-updates.md) | Versions, signing, R8, publishing a release, and the in-app self-update |
| [status.md](status.md) | What's done, what's untested on the device, known gaps and open questions |

## Working on this repo

These are the project owner's standing preferences.

- **Review before committing.** Make each change, summarise it, and leave it uncommitted until the owner has reviewed it. Commit (and push) only when asked.
- **No Claude attribution** in commit messages: no `Co-Authored-By` line.
- **Commit style:** a short imperative subject in plain English, then a paragraph on what changed and why, e.g. "Use the stable minify settings for release builds". One commit per reviewed step.
- **Don't touch the theme files:** `app/src/main/java/bg/butcher/pda/ui/theme/Color.kt`, `Theme.kt` and `Type.kt`, and `res/values*/themes.xml` and `colors.xml`. That includes tidy-ups such as removing the unused contrast schemes or changing the `dynamicColor` default. Ask before adding files next to them; `StatusColors.kt` and `Previews.kt` were added with approval.
- **Building may not work on this machine.** The owner builds and tests on another machine with the Android SDK and the real PDA. Read changes carefully for compile errors, say what hasn't been built or run, and give the owner device test steps.
- **Gradle memory stays at 2 GB** (`gradle.properties`). 4 GB strained the owner's machine alongside Android Studio.
- **Debugging on the device:** launch the app normally, then use "Attach Debugger to Android Process". Launching in Debug can leave the scan engine off ([hardware.md](hardware.md)).

## Build and test

```sh
./gradlew assembleDebug test       # debug APK + unit tests (JVM, no device)
./gradlew assembleRelease          # minified; signed if keystore.properties exists
./gradlew assembleDebug -PuseFakeErp=true   # in-app fake server, e.g. for the emulator
```

- **Debug builds use the real server** by default, so they need a valid base URL and API key in Settings.
- **The fake:** `useFakeErp=true`, in `gradle.properties` or on the command line, switches debug builds to `FakeErpRepository`. Release builds always use the network.
- **On the emulator** the SDK's native library doesn't load, so the app falls back to `FakeBarcodeScanner` and `FakeLabelPrinter` automatically.

## Repository history

The work was done in phases (0–7): data and network, navigation and state, Start and Form, the batch dialog, submit and Summary with the scanner, Search and Details with printing, then polish, release and self-update. The planning notes (`review/`) were removed once everything they decided was in the code or in these docs. `git log` has the details of each step.

The server side is a separate repo: <https://github.com/MLuleci/ButcherERP> (`my_app`). See [server-api.md](server-api.md).
