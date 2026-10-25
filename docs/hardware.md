# Hardware: scanner, label printer, vendor SDK

The PDA's scanner and printer share one serial port, driven by the vendor SDK `app/libs/qspdasdk.jar` (`com.qs.wiget.PrintUtils`, `android.zyapi.CommonApi`) and its native library `libzyapi_common.so`. Everything below was verified from the JAR's bytecode (`javap -p -c`) or on the device. The vendor docs are wrong in places.

## The SDK

**Initialising it (`hardware/QsPdaHardware.kt`, called from `MainApplication.onCreate`):**
1. Set `PrintUtils.isCanScan = false`, so the trigger does nothing until a scanning screen turns it on. The SDK only writes this field in its static initializer.
2. Call `PrintUtils.initPrintUtils(applicationContext)`. Pass the application context, not an Activity, since the SDK keeps it in a static field.
3. Log `Build.MODEL` and `PrintUtils.mComFd`. If the port didn't open, the SDK fails silently, leaving `mComFd <= 0`. The test device logs 23.
4. After `LABEL_SETUP_DELAY_MS = 2500`, send `labelEnable(<from settings>)` and `setStarNum(30)`. The SDK resets the printer (`ESC @`) 2000 ms after init, which would undo anything sent earlier.
5. Catch `LinkageError`. On the emulator `System.loadLibrary("zyapi_common")` fails, and `available` stays false, so the app uses `FakeLabelPrinter` and `FakeBarcodeScanner`.

**Threading:** touch `PrintUtils` on the main thread only.
- **First touch:** its static initializer creates `Handler`s, so touching it first from a thread without a Looper throws `ExceptionInInitializerError`, and then `NoClassDefFoundError` forever after.
- **Print calls** post their work 200 ms later on a `new Handler()`, and silently do nothing if `isCanprint` is false by then.

**Initialise once per process.** A second `initPrintUtils` call:
- leaks the first serial fd and reader thread;
- registers a second trigger receiver, so each trigger press scans twice;
- runs the printer reset again, which undoes label mode.

`closeCommonApi()` throws an NPE if it's called twice. The app never calls it and lets the process die.

**Gotchas:**
- **`setStarNum`:** the method is really called that. The vendor docs say `setStartNum`, which doesn't exist. It sends `1D 28 46 04 00 01 00 <n> 00`, with an unknown unit (probably dots). 30 is from the vendor sample.
- **Serial port by `Build.MODEL`** (115200 baud, 8N1):
  - if the model contains `408` or `415`: `/dev/ttyS1` when it contains `408P`, otherwise `/dev/ttyMT3`;
  - `5501`, `PTT-5501-S` or `LSLN-…`: `/dev/ttyS1`;
  - anything else: `/dev/ttyMT1`.

  The GPIO pins also depend on the model.
- **Print width:** 384 px, or 570 px when `Build.MODEL` contains `408`. A wider bitmap is squashed to that width (`zoomImg` scales only the width), not scaled evenly.
- **The device is a Qunsuo PDA-408.** Its model contains `408` (not `408P`), so the SDK uses `/dev/ttyMT3` and prints 570 px wide, which `PaperSize.MM_80` assumes. The exact `Build.MODEL` string is in the start-up log, and a different model in future could change both the port and the width.
- **Text encoding:** `printText` encodes in GBK for the printer's ROM font, which may have no Cyrillic. The app prints bitmaps instead.
- **zxing:** the JAR bundles an unrelocated zxing 3.5.1, so don't add zxing as a dependency.
- **Logging:** the SDK logs every serial chunk at `Log.e`.
- **Broadcasts:** scan and status broadcasts are global and implicit, so any app can read or forge them.
- **Other apps:** uninstall the factory test apps on a new device first, because they compete for the serial port.

## Scanner (`hardware/BarcodeScanner.kt`, `ScanParser.kt`, `ScanFeedback.kt`)

- **Broadcasts:**
  - **Result:** action `com.qs.scancode`, String extra `"data"`. The SDK's reader thread produces it, so it only works after `initPrintUtils`.
  - **Trigger:** the firmware broadcasts `ismart.intent.scandown` on each press. The SDK's own receiver then calls `openScan()` if `isCanScan` is true. The vendor docs wrongly say results come on `scandown`.
  - **The receiver** handles both actions, registered with `RECEIVER_EXPORTED`.
- **Events:** `BarcodeScanner.events` emits `ScanEvent.Code(text)` or `ScanEvent.NoRead`.
- **No-read:** when nothing is read, the engine replies `ESC ##55UP`, which the SDK drops, and the SDK powers the engine down 3 s after the trigger. So `NoRead` means a trigger press with no code within `NO_READ_TIMEOUT = 3.5 s`.
- **Dropped codes:** the SDK drops any code containing `##55`, `##56` or `55DN`, or exactly equal to `start`, `BE`, `11` or `M`.
- **`setActive(Boolean)`** counts calls rather than storing on/off, because two routes can both be started during a transition. `isCanScan` is true while the count is above 0. `OnScan` calls it under `repeatOnLifecycle(STARTED)`, so scanning also pauses in the background.
- **`parseScan`:**
  - It strips an ECI prefix (a backslash plus 6 digits, e.g. `\000026`). The engine adds one to QR codes that declare a character set.
  - It reduces `http(s)://<host>/app/batch/<batch>` to the batch number, without checking the host.
  - Other plain text is a batch number; anything else is `Unrecognised`.
- **Sounds:** `beep1` when a scan is accepted, `beep3` for a no-read or a rejected scan. `beep2` is unused.
- **The emulator** has no way to trigger `FakeBarcodeScanner`.
- **Debugging:** launching in Debug from Android Studio can leave the scan engine off until the next start. The module still answers `##55UP`, but its light stays off, probably because the debugger slows the SDK's timed start-up. Start the app normally, then attach the debugger.

## Printer (`hardware/QsPdaLabelPrinter.kt`, `LabelRenderer.kt`, `LabelPrinter.kt`)

**One bitmap per label.** `isLabel = true` adds "feed to the next gap" (`1D 0C`) to every print call, so printing line by line would make one label per line. The app draws the whole label on `Dispatchers.Default`, then calls `printBitmap(1, bitmap, isLabel)` once on the main thread.
- **Contents:** QR code on the left, then product name (up to 2 lines), batch number, quantity with unit, `P:`/`E:` dates (`dd.MM.yyyy`, captions translated) and the Stock Entry ID.
- **Fitting:**
  - The quantity and both dates always print whole.
  - The text shrinks from 28 to 20 px to fit.
  - The name, batch number and Stock Entry ID may be cut short.
- **Black and white only:** there's no anti-aliasing, since the printer can't print grey.

**The SDK buffer bug: every bitmap height must be a multiple of 24 rows.**
- **What the SDK does:** `draw2PxPoint` allocates `width × height / 8 + 1200` bytes, but writes `3 + bands × (6 + 3 × width)` bytes, where a partial last band counts as a whole one.
- **The failure:** writes past the end are dropped silently. At 570 px, a partial last band cuts off the end of the job.
- **The fix:** `BAND_ROWS = 24`, and `labelRows()` always returns whole bands.
- **The test:** `LabelRendererTest.rows_areWholeBandsThatFitTheSdkBuffer` checks the formula for every allowed height on both paper sizes.

**Geometry:**
- **Scale:** `DOTS_PER_MM = 8`.
- **Paper sizes:** `PaperSize.MM_80` is 570 px wide with an uncapped QR code. `MM_58` is 384 px, with the QR code capped at 180 px so the text beside it stays readable.
- **QR code:** at least `MIN_QR_SIZE = 18 mm`, so it reads at arm's length.
- **Label height:** a setting with a default of 40 mm. Its range is `labelHeightRange(paperType)`: 30–150 mm for labels, 35–150 mm for a roll.

**Paper type (`PaperType`, in Settings):**
- **`LABELS`** (die-cut, the default):
  - Label mode is on. The printer's gap sensor feeds to the next gap on paper load and after each print.
  - The bitmap stops `BOTTOM_MARGIN = 6 mm` short of the gap, rounded down to whole bands: `(mm × 8 − 48) / 24 × 24`. Printing closer to the gap makes the printer feed an extra blank label.
- **`CONTINUOUS`** (plain roll):
  - **The problem:** in label mode the printer looks for a gap that isn't there, and runs out about 225 mm of paper each time.
  - **Label mode:** the app sends `labelEnable(false)` after the reset, and whenever the setting changes. `MainApplication` collects the setting with `distinctUntilChanged`.
  - **Printing:** with `isLabel = false`.
  - **Height:** the bitmap is the full label height, rounded to the nearest band.
  - **Tear-off:** the last `TEAR_OFF_ROWS = 12 mm` stay blank, because the tear bar is past the print head and the end of each print stays in the printer until the next one.
- **Die-cut label width:** 80 mm labels are slightly too wide for the test device's paper bay, and 58 mm labels haven't been tried. Continuous roll is what's in use.

**QR content:**
- **The URL:** the batch's ERPNext page, `batchUrl(baseUrl, batch)` = `https://<host>/app/batch/<batch>`. Scanning it back gives the batch number through `parseScan`.
- **Settings:** error correction M, margin 1, and **no character-set hint**, because a UTF-8 hint makes the scanner add an ECI prefix.
- **No base URL:** without a valid one in Settings, the label prints text only. That was the "missing QR" bug.

**Print result:**
- **Before printing:** if `mComFd <= 0` or `!isCanprint`, it fails with `PrinterNotReadyException` ("Printer not ready" toast).
- **After printing,** it waits up to 4 s for `com.qs.getmassage` (int extra `message`):
  - `1` means out of paper, and fails the print;
  - `0` means printed;
  - no broadcast at all also counts as success.
- **A gap:** if the printer stops being ready during the SDK's 200 ms delay, the job is dropped silently and still counts as success.

## Packaging

- **Native libraries:** `app/src/main/jniLibs/armeabi-v7a/libzyapi_common.so` (ARMv5TE code, which runs on v7) and `arm64-v8a/libzyapi_common.so` (16 KB page-aligned).
  - **The 32-bit one was moved from `armeabi`:** Android loads all native libs from one ABI folder. If any dependency ships `armeabi-v7a` libs, a 32-bit device never looks in `armeabi`.
  - **Check after packaging changes:** `unzip -l app-debug.apk | grep '\.so'` should list both folders.
- **The JAR:** `implementation(files("libs/qspdasdk.jar"))`.
- **Keep rules** (`app/src/main/keepRules/rules.keep`, applied to release builds):
  - **`android.zyapi.CommonApi` and every class with native methods:** kept, because JNI binds by name (`Java_android_zyapi_CommonApi_*`, no `RegisterNatives`).
  - **`com.qs.wiget.**`:** kept whole, because its state is in static fields used by anonymous `Handler`/`Runnable` classes. This also stops R8 inlining `isCanScan`.
  - **`-dontwarn`** for `com.google.zxing.**` and `com.qs5501demo.aidl.**`: stray demo classes inside the JAR.
- **Permissions:** the SDK needs none. On this firmware the serial and GPIO device nodes are world-accessible.

## Still unknown

- **Label sensing:** whether `labelEnable(true)` selects gap or black-mark sensing.
- **`setStarNum`:** the unit of its value.
- **The keypad:** its Fn key (letters or digits) isn't visible to apps, so there's no mode indicator.
