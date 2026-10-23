# 05 - Vendor scanner / label-printer SDK (static analysis)

Scope: [qspdasdk.jar](../app/libs/qspdasdk.jar), [libzyapi_common.so (arm64-v8a)](../app/src/main/jniLibs/arm64-v8a/libzyapi_common.so), [libzyapi_common.so (armeabi)](../app/src/main/jniLibs/armeabi/libzyapi_common.so), added in commit 61e9162.

Method: nothing was executed or loaded. I unzipped the JAR into `/tmp/qsdk_extract` and read it with `javap -p -s -c -v`, `strings`, `objdump -p/-T/-s` and `file`. No Gradle build, no `adb`, no device.

Labels used below:
- **Verified** = read directly from bytecode or the ELF files.
- **Inferred** = my reading of what the verified facts mean. Needs a device test.

---

## 1. Summary (read this first)

1. **The JAR is not on the compile classpath yet.** [app/build.gradle.kts](../app/build.gradle.kts) has no `files(...)` or `fileTree(...)` line (verified by grep). AGP does not pick up `libs/*.jar` by itself. You need `implementation(files("libs/qspdasdk.jar"))`. The `jniLibs` folders are picked up automatically. *(Still to do; listed as a build change in the summary, 2.11 part 1.)*

2. **The vendor docs have a typo.** The method is `PrintUtils.setStarNum(int)`, not `setStartNum`. `setStartNum` does not exist in the JAR, so the doc snippet will not compile.

3. **`ismart.intent.scandown` is the scan *trigger* in this SDK, not the result.**
   - Verified: the SDK registers a receiver for `ismart.intent.scandown` and `hbyapi.intent.key_scan_down`. That receiver ignores all extras and just calls `PrintUtils.openScan()`.
   - Verified: the scanned text is broadcast by the SDK as action **`com.qs.scancode`**, extra **`"data"`** (String).
   - So the vendor doc ("scanned text is in extra `data` of `ismart.intent.scandown`") does not match what this JAR does. Either the doc describes a different firmware path, or it is sloppy. Listen for both actions (see section 2) and confirm on the device. *Updated: the decision is to listen on `com.qs.scancode` (what the JAR sends) and confirm on the device. Add `ismart.intent.scandown` only if the test shows it also carries `"data"`, and ignore broadcasts with no `data` (summary, 2.11 part 3).*
   - Inferred: your `mintent` is `Intent("ismart.intent.scandown")`. Sending it fires the SDK receiver, which fires the scanner.

4. **The scanner is not independent of the SDK (on this code path).** The scan result arrives over the same serial port as the printer, read by a thread that `initPrintUtils` starts. If you never call `initPrintUtils`, the `com.qs.scancode` broadcast is never produced by the SDK. (A firmware-level scanner service might broadcast on its own; the JAR cannot tell us.)

5. **Threading trap: `PrintUtils` must be touched on a thread that has a Looper (use the main thread).**
   - Verified: the static initializer of `PrintUtils` does `new Handler()` several times, and every `printXxx` call does `new Handler().postDelayed(..., 200)`.
   - Called from a coroutine on `Dispatchers.IO`, the first touch of the class throws `ExceptionInInitializerError`. After that, every use gives `NoClassDefFoundError`.
   - The delayed print work (bitmap scaling, QR encoding, raster conversion) then runs on that Looper thread, usually the UI thread.

6. **Every API is `void`. There are no callbacks and no error codes.**
   - Print calls silently do nothing if `PrintUtils.isCanprint` is false or the serial port failed to open (`mComFd <= 0`).
   - Readiness has to be read from public static fields.

7. **`printText(..., isLabel=true)` feeds to the next label after each call.** To print one label with several fields, render the whole label into **one Bitmap** and call `printBitmap` once with `isLabel=true`. This also avoids the GBK text encoding (see section 3).

8. **Activity leak: yes.** `initPrintUtils(ctx)` stores `ctx` in `public static Context context`. Pass `applicationContext`. It works (see section 4). There is a release method, `closeCommonApi()`, but it NPEs if called twice.

9. **`jniLibs/armeabi` is probably fine to package, but it is the wrong folder name for safety.**
   - The 32-bit binary is ARMv5TE, which runs on ARMv7.
   - If any other dependency ships `lib/armeabi-v7a/*.so` (Compose's `androidx.graphics:graphics-path` very likely does), a 32-bit device will pick `armeabi-v7a` and never look in `armeabi`. The result is `UnsatisfiedLinkError` for `zyapi_common`.
   - **Fix: rename the folder to `armeabi-v7a`** (details in section 5). *Decided: do the rename with `git mv` (summary, 2.11 part 1). Open question 7 below is then only a courtesy check with the vendor.*

10. **Cyrillic / Bulgarian caveat.** `printText` encodes with GBK, and the scan decoder tries GBK first. GBK has a Cyrillic block but the printer font may not. Bitmap printing sidesteps this. For scan input, ASCII batch numbers are fine, and a UTF-8 QR with Cyrillic could be decoded wrongly (section 2).

---

## 2. Inventory

### Packages and classes (verified, `unzip -l`, 366 entries)

| Package | Content | Public API? |
|---|---|---|
| `android.zyapi` | `CommonApi` (JNI wrapper, loads `libzyapi_common.so`) | yes |
| `com.qs.wiget` | `PrintUtils` (the entry point), `BarcodeCreater`, `BitmapDeleteNoUseSpaceUtil`, plus 14 anonymous `PrintUtils$N`, `PrintUtils$ReadThread`, `PrintUtils$ScanBroadcastReceiver` | `PrintUtils`, `BarcodeCreater`, `BitmapDeleteNoUseSpaceUtil` public; the inner ones are package-private |
| `com.google.zxing.**` | A full **zxing core 3.5.1** copy (`META-INF/maven/com.google.zxing/core/pom.properties`, 326 entries, class major 52) | yes, but not vendor code |
| `com.qs5501demo.aidl` | Stray `BuildConfig`, `R`, `R$id`, ... from the vendor's demo app (9 inert classes) | yes, ignore |

Manifest: `Created-By: Fat Jar Eclipse Plug-In`. No `Main-Class`, no services, no resources beyond `META-INF/maven`.

### `com.qs.wiget.PrintUtils` public API (verified, `javap -p -s`)

Setup / lifecycle:
```java
public static void initPrintUtils(Context)          // stores ctx, init(), starts ReadThread
public static void init()                            // openGPIO(), initGPIO(), +2000ms init job, creates Handler h, registers scan receiver
public static void open()                            // sends power-on bytes, GPIO 74/75 for pin_L models
public static void openScan()                         // sends scan-trigger bytes, GPIO pulse
public static void openGPIO() / initGPIO()
public static void closeCommonApi()                   // the only "release" method
public static CommonApi getCommonApi()
public String getCurDevice() / static void setCurDevice(String)
```

Printing:
```java
public static void printText(int align, int size, String text, boolean isLabel)
public static void printBitmap(int align, Bitmap bmp, boolean isLabel)
public static void printBitmap1(int align, Bitmap bmp, boolean isLabel)   // near-identical to printBitmap
public static void printBarCode(int align, int width, int height, String data,
                                boolean isShowBarStr, int size, boolean isLabel)
public static void printQRCode(int align, int width, int height, String data,
                               boolean isShowQRStr, int size, boolean isLabel)
public static void labelEnable(boolean)
public static void toNextPaper()                      // sends 1D 0C
public static void setStarNum(int)                    // NOTE spelling
public static void send(byte[]) / send1(byte[])       // raw bytes to the UART
```

Helpers (all public static, return `Bitmap` unless noted): `word2bitmap(String,int,int,int)`, `textAsBitmap(String,int width,float size)`, `textAsBitmap1(String,float)`, `twoBtmap2One`, `twoBtmap2One1`, `adjustPhotoRotation`, `zoomImg(Bitmap,int,int)`, `createQRImage(String,int,int)`, `draw2PxPoint(Bitmap)` returns `byte[]`, `px2Byte`, `hexStringToBytes`, `byteToString`, `isStr2Num`, `toLH`, `sumMultiIntArray`, `intToBytes`, `deleteErr`.

Public static fields (verified): `pin`, `mComFd` (default -1), `context`, `isCanprint` (default false), `isCanSend` (true), `temHigh` (false, never written after init as far as I can see), `isCanScan` (default true), `decode` ("GBK", not used by the code I read, which uses the literal `"GBK"`), `sb1`, `arraysNum`.

### `android.zyapi.CommonApi` (verified)

```java
static { System.loadLibrary("zyapi_common"); }        // the ONLY loadLibrary in the JAR
public native int setGpioMode(int,int), setGpioDir(int,int), setGpioPullEnable(int,int),
                  setGpioPullSelect(int,int), setGpioOut(int,int), getGpioIn(int);
public native int openCom(String dev, int baud, int bits, char parity, int flags);
public native int openComEx(String,int,int,char,int,int);
public native int writeCom(int fd, byte[] buf, int len);
public native int readCom(int fd, byte[] buf, int len);
public native int readComEx(int fd, byte[] buf, int len, int a, int b);
public native void closeCom(int fd);
```

### `BarcodeCreater` / `BitmapDeleteNoUseSpaceUtil`

`BarcodeCreater.creatBarcode(Context,String,int,int,boolean,int,int)`, `creatBarcode1`, `creatCodeBitmap(String,int,int,int,Context)` (builds a `TextView` with the passed Context for the caption), `encode2dAsBitmap`, `encodeAsBitmap(String,BarcodeFormat,int,int)`, `mixtureBitmap`, `saveBitmap2file`, `guessAppropriateEncoding`. `BitmapDeleteNoUseSpaceUtil.deleteNoUseWhiteSpace(Bitmap)` crops white margins.

---

## 3. Scanner

### What the SDK does (verified)

`PrintUtils.init()` builds an `IntentFilter` with two actions and registers it on the stored context:

```
ismart.intent.scandown
hbyapi.intent.key_scan_down
```

`PrintUtils$ScanBroadcastReceiver.onReceive(ctx, intent)` is:
```java
if (PrintUtils.isCanScan) PrintUtils.openScan();   // extras are not read at all
```

`openScan()` writes a 7-byte command (`1B 23 23 35 35 44 4E`, ASCII `ESC # # 5 5 D N`) to the serial port. For `pin_L` models (the default) it also drives GPIO 74/75, and re-arms a 3 s timer. The scanner module answers over the serial port. `PrintUtils$ReadThread` reads it with `CommonApi.readCom`, hands chunks to a Handler, which assembles them, waits 200 ms for the end of the burst, decodes as GBK (retries UTF-8 if the GBK result contains U+FFFD), strips some junk characters, and then sends:

```java
Intent i = new Intent();
i.setAction("com.qs.scancode");
i.putExtra("data", text);          // String
context.sendBroadcast(i);          // global, implicit broadcast
```

### Strings related to intents (grep of all classes, verified)

| String | Where | Meaning |
|---|---|---|
| `ismart.intent.scandown` | `PrintUtils.init` | scan key pressed / trigger (receiver) |
| `hbyapi.intent.key_scan_down` | `PrintUtils.init` | same, other firmware family |
| `com.qs.scancode` + extra `data` | `PrintUtils$7` | **scan result** |
| `com.qs.getmassage` + int extra `message` | `PrintUtils$1` | printer status (see section 4) |

There is no keyboard-wedge / broadcast-output mode setting, no sound or vibrate setting, no scan start/stop action and no enable/disable action in the JAR. The only switch is the public static `PrintUtils.isCanScan` (disables the SDK reacting to the trigger broadcast). The arm64 `.so` exports `setVgp1On`, `setVibrOn`, `setVgp1Down`, `setVibrDwon`, but `CommonApi` declares no Java methods for them, so they cannot be called through this JAR.

### Recommendation for `BarcodeScanner` (inferred)

- Register dynamically only while the scan screens are visible, as planned. *(Updated: three routes now, Form, Search and Details; see the summary, 2.14.)* Filter for **both** `com.qs.scancode` and `ismart.intent.scandown`, read `intent.getStringExtra("data")`, and ignore null/blank values (a pure key-down broadcast will not carry `data`). *Updated: the agreed starting point is `com.qs.scancode` only; add the second action only if a device test shows it carries `data` (summary, 2.11 part 3). Only the Form and Search routes listen.*
- Use `ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)`. The broadcasts come from outside the app, so the "not exported" flag would drop them on Android 13+. On your Android 6 device the flag is ignored.
- If `initPrintUtils` runs at process start, the SDK's own receiver stays registered and keeps triggering scans on every screen. Set `PrintUtils.isCanScan = false` when you leave the scan screens, `true` when you enter them.
- Do not send `ismart.intent.scandown` from your own UI button *and* also treat that action as a scan result. Sending it is only the "start scan" gesture. *Updated: there is no on-screen scan button; the hardware trigger is the only way to scan. Use `FakeBarcodeScanner` for the emulator (summary, 2.11 part 3).*
- Caveats: the decoder drops some exact strings (`"start"`, `"BE"`, `"11"`, `"M"` and the vendor control strings `##55`, `##56`, `55DN`, as far as I can read the bytecode; inferred). A batch number that is exactly `11` or `M` could be swallowed. UTF-8 QR payloads (Cyrillic) may be mis-decoded as GBK without ever hitting the U+FFFD retry. ASCII batch numbers are safe. *Updated: the QR now holds an ERPNext URL (`https://<host>/app/batch/<batch_no>`), not the bare batch number. The summary (2.11 part 5) checked the decoder: `:`, `/`, `.`, `?` and `-` pass through, so the URL arrives intact, and `parseScan` reduces it to the batch number. The exact-match drops (`11`, `M`, `BE`, `start`) only apply to bare batch numbers.*

---

## 4. Printer

### Behaviour of the print calls (verified from bytecode)

Common pattern of `printText`, `printBitmap`, `printBitmap1`, `printBarCode`, `printQRCode`:

1. On the **calling thread**, `send(new byte[]{0x1D,0x61,...})` (blocking `writeCom`, tiny).
2. `new Handler().postDelayed(job, 200)`. This needs a Looper on the calling thread.
3. After 200 ms the job checks `if (PrintUtils.isCanprint)`. If false it does nothing and says nothing.
4. The job sets alignment (`ESC a n`, align 0 left, 1 center, 2 right), builds the payload, then wraps it like `1D 23 53 D1 7A F8 06 4D` + data + (`1D 0C` if `isLabel`) + `1D 23 45`, and `send()`s it in one write.

So the calls are asynchronous and **return immediately**. There is no completion callback, no return value, and no way to know when the paper has actually come out.

| Method | What it prints | Notes (verified) |
|---|---|---|
| `printText(align,size,text,isLabel)` | text as bytes, `text + "\n"` encoded **GBK** | `size` 1 = `GS ! 00`, 2 = `GS ! 11` (double). Printer-font glyphs, so Cyrillic depends on the printer ROM |
| `printBitmap(align,bmp,isLabel)` | any Bitmap, raster | width forced to **384 px** (**570 px** if `Build.MODEL` contains "408"); wider bitmaps are scaled down via `zoomImg` |
| `printBarCode(align,w,h,data,showStr,size,isLabel)` | 1D barcode built with zxing + a caption bitmap | uses `BarcodeCreater.creatBarcode(context,...)` |
| `printQRCode(align,w,h,data,showStr,size,isLabel)` | QR via `createQRImage` (zxing `QRCodeWriter`, UTF-8 hint, margins trimmed); falls back to `encode2dAsBitmap` | the caption is rendered with `textAsBitmap(data,384,size)` |
| `labelEnable(true/false)` | sends `1F 1B 1F 80 04 05 06 44` (on) / `... 66` (off) | switches the printer firmware between label (gap / black-mark) mode and continuous receipt mode. Which of gap vs black mark is not in the JAR |
| `setStarNum(n)` | sends `1D 28 46 04 00 01 00 <n> 00` | looks like Epson `GS ( F` "adjust print start position". Inferred: `n` shifts where printing starts on the label (vendor sample uses 30). Unit unknown, probably dots |
| `toNextPaper()` | sends `1D 0C` | feed to next label / gap |

`isLabel=true` appends `1D 0C` after the payload, i.e. **one feed per call**. If you call `printText(..., true)` seven times you get seven labels.

### Status / errors (verified bytes, inferred meaning)

The serial replies are matched against hex strings:

| Reply contains | Effect |
|---|---|
| `14 00 0C 0F` | `isCanprint = false`; Handler sends broadcast `com.qs.getmassage` with int `message=1`. Inferred: paper out |
| `14 00 00 0F` | `isCanprint = true` (ready) |
| `42 45 D1 7A F8` | Handler sends `com.qs.getmassage` with `message=0`. Inferred: another printer condition (possibly over-temperature) |
| anything else not containing `11` | `isCanprint = true` |

`temHigh` exists but I did not find code that sets it. Public fields to poll: `PrintUtils.isCanprint` (ready), `PrintUtils.mComFd > 0` (serial port opened). A `BroadcastReceiver` for `com.qs.getmassage` (int extra `message`) gives paper-out events.

The init job posted 2000 ms after `initPrintUtils` sends `ESC @` (`1B 40`, reset printer) plus a few setup commands. That reset is why the vendor says to wait 2500 ms before `labelEnable(true)` and `setStarNum(30)`. Calling them earlier gets overwritten by the reset (inferred).

### Label design recommendation

Render the whole label into one 384 px wide `Bitmap` yourself (QR + text lines: batch number, item name, quantity with unit, production and expiry dates, stock entry ID; Cyrillic-capable Android fonts), then call `printBitmap(1, bmp, true)` once. Advantages: one feed, one status check, no GBK/font problem, you control layout. The QR can come from the zxing copy that is already in the JAR (`com.google.zxing.qrcode.QRCodeWriter`) or from `PrintUtils.createQRImage(batch, w, h)`.

### Sketch of `QsPdaLabelPrinter` (inferred, not compiled)

*Updated: the summary (2.11 part 4) defines the app-side types as `LabelData` and `LabelPrinter` (`isAvailable`, `suspend fun print(label): Result<Unit>`), and puts the one-time setup in `QsPdaHardware.init(app)` called from `MainApplication.onCreate`. The sketch below uses `Label` / `PrintResult` and a `start()` method; treat them as placeholders for those. Calling `PrintUtils.isCanScan = false` at start is kept; the scanner turns it on per screen.*

```kotlin
class QsPdaLabelPrinter(app: Application) : LabelPrinter {
    private val appCtx = app.applicationContext
    private val main = Handler(Looper.getMainLooper())

    // Call once from MainApplication.onCreate() (main thread).
    fun start() {
        try {
            PrintUtils.isCanScan = false              // BarcodeScanner turns it on per screen
            PrintUtils.initPrintUtils(appCtx)          // applicationContext, not the Activity
            main.postDelayed({
                PrintUtils.labelEnable(true)
                PrintUtils.setStarNum(30)              // vendor spelling is setStarNum
            }, 2500)
        } catch (t: LinkageError) { /* UnsatisfiedLinkError / NoClassDefFoundError on emulator */ }
    }

    override suspend fun print(label: Label): PrintResult {
        if (PrintUtils.mComFd <= 0) return PrintResult.NotAvailable
        if (!PrintUtils.isCanprint) return PrintResult.NotReady   // paper out or still starting
        val bmp = withContext(Dispatchers.Default) { renderLabelBitmap(label, widthPx = 384) }
        withContext(Dispatchers.Main) {                // PrintUtils needs a Looper thread
            PrintUtils.printBitmap(/* align = center */ 1, bmp, /* isLabel = */ true)
        }
        return PrintResult.Sent                        // no completion signal exists
    }
}
```

If you want to use the SDK QR instead: `PrintUtils.printQRCode(1, 200, 200, label.batchNo, false, 1, false)` *(Updated: the QR data would be the batch URL from `batchUrl(baseUrl, batchNumber)`, not the bare number; summary, 2.11 part 5. The summary prefers the single-bitmap route.)* followed by `printText(1, 1, ..., false)` per line and a final `toNextPaper()`. That is untested and each call is its own print job with header/footer bytes, so check on paper before relying on it.

---

## 5. Lifecycle / Context

All verified from `initPrintUtils`, `init`, `closeCommonApi` bytecode.

- `initPrintUtils(ctx)`: `PrintUtils.context = ctx;` `init();` start a new `ReadThread` (non-daemon, loops while the private `isOpen` flag is true).
- **Leak:** `context` is `public static`. Passing an `Activity` pins it for the life of the process. Pass `applicationContext`.
- Does it need an Activity? No. The context is used for `registerReceiver`, `unregisterReceiver`, `sendBroadcast`, and `new TextView(context)` in `BarcodeCreater.creatCodeBitmap` (caption for 1D barcodes). The application context works for all of them. No dialogs, no `startActivity`.
- Thread: `init()` and the class initializer create `Handler`s. Call on the main thread. `MainApplication.onCreate()` is the natural place and removes the "double init per Activity" problem. It also means the 2 s init job is not repeated on every Activity re-creation.
- **Double init** (calling `initPrintUtils` twice without `closeCommonApi`): a second `CommonApi` and serial port fd are opened (the first fd is leaked), a second `ReadThread` starts, a second scan receiver is registered and the first one is dropped from the static field (still registered, can no longer be unregistered, so each trigger runs `openScan` twice), and the 2 s init job (`ESC @`) runs again, which resets label mode. Avoid.
- **Release:** `closeCommonApi()` (public static). It sets several GPIOs low (powering down the printer/scanner module), unregisters the receiver, sets `isOpen=false`, closes the fd, sets `mCommonApi = null`. It is guarded only by `mComFd > 0`, and `mComFd` is not reset, so a **second call throws NPE**. If `initPrintUtils` was called with an Activity and the Activity is destroyed, call it once in `onDestroy` inside `runCatching`, but with the Application-level init there is no reason to call it. Let the process end.
- The `ReadThread` loop has no sleep. If `readCom` returns immediately with 0 it will spin (inferred; depends on the native read timeout, which I did not trace).

---

## 6. Native libs and packaging

### `file` / ELF facts (verified)

| File | `file` | Notes |
|---|---|---|
| `arm64-v8a/libzyapi_common.so` | ELF 64-bit LSB shared object, ARM aarch64, stripped | built with GCC 4.9 |
| `armeabi/libzyapi_common.so` | ELF 32-bit LSB shared object, ARM, EABI5, stripped | built with GCC 4.6; `.ARM.attributes` CPU name `5TE` = **ARMv5TE**, no NEON/VFP requirement visible |

Both: `NEEDED liblog.so libstdc++.so libm.so libc.so libdl.so`, `SONAME libzyapi_common.so`. All are system libraries, nothing to bundle.

### Load path and JNI symbols (verified)

- Only `android.zyapi.CommonApi.<clinit>` calls `System.loadLibrary("zyapi_common")`. The first access to `CommonApi` triggers it. In practice that is `PrintUtils.initPrintUtils()` -> `init()` -> `openGPIO()` -> `new CommonApi()`. On an emulator / non-ARM device that throws `UnsatisfiedLinkError` (on x86 images, "dlopen failed: library ... not found") out of `initPrintUtils`; wrap with `catch (e: LinkageError)`. Any later touch of `CommonApi` then throws `NoClassDefFoundError`.
- Exports: `JNI_OnLoad`, `JNI_OnUnload`, and `Java_android_zyapi_CommonApi_{setGpioMode, setGpioDir, setGpioPullEnable, setGpioPullSelect, setGpioOut, getGpioIn, openCom, openComEx, closeCom, readCom, readComEx, writeCom}` which match the 12 `native` methods of `CommonApi`. Extra exports with no Java declaration: `GetLibVersion` (both ABIs), `setVgp1On`, `setVibrOn`, `setVgp1Down`, `setVibrDwon` (arm64 only). Symbols are bound by name, not `RegisterNatives` (no `FindClass` / `RegisterNatives` string).
- Imports (arm64): `open`, `close`, `read`, `write`, `ioctl`, `select`, `tcgetattr`, `tcsetattr`, `tcflush`, `cfsetispeed`, `cfsetospeed`, `isatty`, `__android_log_print`.

### Hardware path (verified strings)

- Native: `/dev/mtgpio` (MediaTek GPIO driver, opened by `openGpioDev`). Log lines: `zyapi_common so JNI_OnLoad ~~!!`, `Cant't open com port:%s`, `com[fd:%d] setting success.`
- Java picks the serial device from `Build.MODEL` (115200 baud, 8 data bits, parity 'N'):
  - model contains `408` or `415`: `/dev/ttyS1` if it contains `408P`, otherwise `/dev/ttyMT3`
  - model equals `5501` or `PTT-5501-S`, or contains `LSLN-`: `/dev/ttyS1`
  - everything else: `/dev/ttyMT1`
- GPIO pins used: 84 (`pin_L`, default), 68 (`pin_H`), 157 (`pin_P`), plus 74, 75, 58 in some paths. Pin choice again depends on model strings (`5501H`, `EH`, `0221`, `HY`, `H2`, `i9000S`, `KN-2231`, `NF5501`, `P2`, `CY03`, `PDA-SG2`, `408`, `415`).
- Both the printer and scanner are on the serial port and GPIO lines above. The app needs read/write on those device nodes. As a normal (non-system) app that works only if the vendor firmware made them world-accessible. If `openCom` fails, `mComFd` stays <= 0 and everything silently no-ops.
- Permissions: no `android.permission` string anywhere in the JAR or `.so` files. Nothing to add to the manifest for the SDK itself. (The existing manifest has only `INTERNET`.)

### Does AGP still package `jniLibs/armeabi`?

I could not run a build, so this part is knowledge plus the repo config (AGP `9.2.1` in [libs.versions.toml](../gradle/libs.versions.toml)), not something I observed.

- The NDK stopped *building* for `armeabi` (r17), but AGP's packaging of prebuilt files under `src/main/jniLibs/<abi>/` copies any folder name; it does not validate the name. I believe `lib/armeabi/libzyapi_common.so` ends up in the APK. **Verify** after the next build with: `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep '\.so'`.
- Android's runtime rule: the system looks at the device's ABI list (a 32-bit ARMv7 phone reports `armeabi-v7a,armeabi`; an arm64 phone reports `arm64-v8a,armeabi-v7a,armeabi`) and picks the **first ABI that has any native library directory in the APK**. All libs are then loaded from that one directory.
  - 32-bit ARMv7 device, APK has only `lib/armeabi/` and `lib/arm64-v8a/`: picks `armeabi` and loads our lib. Works.
  - 32-bit ARMv7 device, APK **also** has `lib/armeabi-v7a/` (from any dependency): picks `armeabi-v7a`, never looks at `armeabi`, so `zyapi_common` is missing. **Breaks.** Compose (`androidx.graphics:graphics-path` via `ui-graphics`, and possibly Coil/others) ships `armeabi-v7a` natives (inferred; check the APK listing).
  - 64-bit device: uses `arm64-v8a`, fine either way.
- **Recommended layout:** move the 32-bit file to `app/src/main/jniLibs/armeabi-v7a/libzyapi_common.so` and delete the `armeabi` folder. ARMv5TE code runs on ARMv7. Keep `arm64-v8a` as is. Optionally add `ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }` in `defaultConfig` to leave out x86 natives from dependencies (emulator then needs an ARM image or the SDK path must be skipped).

### 16 KB page size (verified with `objdump -p`)

- arm64: both `LOAD` segments have `align 2**16` (64 KB), file offset and vaddr are congruent. **Meets the 16 KB requirement.**
- armeabi (32-bit): `LOAD align 2**12` (4 KB). The 16 KB rule applies to 64-bit ABIs only (arm64-v8a, x86_64), so this is not an issue.
- It matters for Android 15+ devices on 16 KB kernels and for Play Store uploads. Your target PDAs run Android 6, so it does not affect them.

---

## 7. Build and R8

### Dex compatibility (verified)

- Vendor classes: major version **50** (Java 6). zxing classes: major **52** (Java 8). D8 handles both with minSdk 23. I found no references to `java.util.function`, streams or `Optional` in the vendor classes.
- `META-INF/maven/**` resources are excluded by default AGP packaging rules. No merge conflict expected.
- No reflection, no `Class.forName`, no `SystemProperties`, no `Runtime.exec`/`su`, no hidden-API use found (grep over all class files). Uses `Build.MODEL`, `TextView`, `StaticLayout`, `TextPaint`, all public APIs.
- **zxing clash:** the JAR contains an unrelocated copy of `com.google.zxing.*` 3.5.1. The app currently has no zxing dependency (checked [libs.versions.toml](../gradle/libs.versions.toml)). If you ever add `com.google.zxing:core` or `zxing-android-embedded`, D8 fails with "Duplicate class com.google.zxing...". Either use only the copy in the JAR, or pick a build that excludes it.
- The package `android.zyapi` starts with `android.`; D8 accepts it. On vendor firmware the framework may already ship `android.zyapi.CommonApi` in the boot classpath, in which case the framework copy wins (parent-first class loading). That is harmless as the API is the same (inferred).

### Dependency line to add

```kotlin
dependencies {
    implementation(files("libs/qspdasdk.jar"))
}
```

### Keep rules for [rules.keep](../app/src/main/keepRules/rules.keep)

Note: release currently has `optimization { enable = false }` in [build.gradle.kts](../app/build.gradle.kts), so R8 is not shrinking or renaming anything today and these rules have no effect yet. Add them now so turning optimization on later does not break the JNI lookup.

```proguard
# Vendor PDA SDK (qspdasdk.jar)
# JNI binds by name: Java_android_zyapi_CommonApi_<method>. Class and native method names must survive.
-keep class android.zyapi.CommonApi {
    native <methods>;
    <init>();
}
-keepclasseswithmembernames class * {
    native <methods>;
}

# PrintUtils keeps state in public static fields and anonymous inner Handler/Runnable classes
# that call each other through synthetic access$ methods. Keep the whole package, it is small.
-keep class com.qs.wiget.** { *; }

# zxing copy bundled inside the JAR: used internally by PrintUtils and BarcodeCreater.
-dontwarn com.google.zxing.**
-dontwarn com.qs5501demo.aidl.**
```

`-keep class com.qs.wiget.**` is broader than strictly required. The minimum for correctness is the `CommonApi` rule (the other classes have no reflection and no JNI). Keeping `com.qs.wiget` costs a few KB and protects against R8 inlining the static state fields.

---

## 8. Risks

- **Logging of scanned data (verified):** `PrintUtils$ReadThread` calls `Log.e("str1", "str1=" + <hex/text of every serial chunk>)` and `PrintUtils$1` calls `Log.e("", "1read success:" + <data>)`. Every scan and every printer status reply lands in logcat at error level. Low risk for batch numbers, but note it.
- **Global broadcasts (verified):** scan results go out by `sendBroadcast` on `com.qs.scancode`. Any app can read them, and any app can forge them. If scan input ever drives something sensitive, validate the batch number against the ERP rather than trusting the broadcast. Printer status uses `com.qs.getmassage` the same way.
- **Static mutable state everywhere (verified):** `isCanprint`, `isCanScan`, `mComFd`, `context`, shared `StringBuffer sb1`. The SDK is single-instance and not thread-safe. Use it from one thread.
- **No network access, no hard-coded URLs, no `su`/root, no obfuscation (verified):** the only `http://` hit is zxing's `URIParsedResult` string constants and the Maven `pom.xml`. Names and local-variable debug info are present, so the code is easy to read.
- **Device-specific code (verified):** `Build.MODEL` string matching decides serial port and GPIO pins. On a model not in its list it falls back to `/dev/ttyMT1` and pin 84, which may be wrong for your PDA. Log `Build.MODEL` on the target device and compare with the lists above.
- **Silent failure (verified):** port-open failure, paper-out and "not ready" all produce no exception. Surface `mComFd` and `isCanprint` in your UI.
- **Non-daemon reader thread** is started per `initPrintUtils` call and only ends when `isOpen` becomes false (init job failure or `closeCommonApi`).
- **Provenance:** third-party binary of unknown origin with no checksum or signature in the repo. I did not find anything malicious in what I read (no network, no exec, no file writes beyond `BarcodeCreater.saveBitmap2file`, which only runs if called). I did not disassemble the native code beyond symbols, imports and strings.

---

## 9. Open questions for the vendor

1. *(Still open; summary, section 4, question 1.)* Which action carries the scan result on your firmware: `ismart.intent.scandown` + `data` (as in your docs) or `com.qs.scancode` + `data` (as the JAR sends)? Are both possible, depending on a system setting? Does the system scanner service work without `PrintUtils.initPrintUtils`?
2. Is there a documented way to enable/disable the hardware scan trigger, or to choose keyboard-wedge vs broadcast output, other than the `PrintUtils.isCanScan` static?
3. What is the correct spelling and meaning of `setStarNum` (docs say `setStartNum`)? What unit is the value (dots, mm) and is 30 right for all label sizes? Which label sensing does `labelEnable(true)` select, gap or black mark?
4. What do `com.qs.getmassage` `message=0` and `message=1` mean exactly? Is there an over-temperature signal? Why is `temHigh` never set?
5. Is it safe to call `initPrintUtils` once from `Application.onCreate` with the application context, and never call `closeCommonApi`? Is `closeCommonApi` supposed to be callable twice? *(The app does this by decision, summary 2.11 part 2; the question is only a confirmation.)*
6. Which `Build.MODEL` values does our device report, and are they in your model list? What are the permissions on `/dev/ttyMT1`, `/dev/ttyS1` and `/dev/mtgpio` for a non-system app?
7. Is the 32-bit library intended to be `armeabi` (ARMv5TE) only, and is placing it in `armeabi-v7a` supported?
8. Is there a newer SDK build that uses `Handler(Looper.getMainLooper())` or a callback, can be called from background threads, and does not log scanned data at error level?
9. Can you confirm the maximum bitmap width per model (384 px, 570 px on "408") and the dot density for label printing?
