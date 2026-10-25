# Implementation order for the app

The server work ([04-server-changes.md](04-server-changes.md)) is done. This is a suggested order for the app work. It's built so that:

- **The app runs at the end of every step.** Each step is small enough for one commit (or a few), and you can try it on the emulator, which uses the fakes, or on the device.
- **Foundations come first.** Models, networking and number handling are what every later screen relies on. Fixing them first means you don't rewrite screens twice.
- **Hardware comes late.** The scanner and printer are behind interfaces, so the flow can be built and tested with fakes before the SDK is involved.

Section references point to [00-summary.md](00-summary.md) unless another file is named. "§1 #3" means item 3 in the summary's section 1 table.

**Progress:** Phases 0–5 are done. Phase 6 is built and pushed, but not yet confirmed on the device. Steps marked *(done)* are committed and pushed. Where the implementation made a decision the plan left open, or differs from it, there's an **Implementation notes** list under the step.

---

## Before you start: server *(done)*

All the server work in [04-server-changes.md](04-server-changes.md) is done and tested, including the **PDA User** role (A7) and every `curl` check for Search and Details. On the device, use a PDA user that has **only** the PDA User role, such as `pda-test`. A new PDA user needs the role assigned by hand.

---

## Phase 0: Build and tooling (half a day) *(done)*

Get the project building cleanly with tests running, so every later step can be checked.

### Step 0.1: Make the tests compile *(done)*
- Fix `FakeDataSource` / `FakeErpService` / `FakeNetworkErpRepository` against the current interfaces (§1 #10).
- Add one trivial test, so `./gradlew test` runs green.

### Step 0.2: Tidy the build *(done)*
- Remove duplicate and unused catalog entries, e.g. `coil` (01, finding 18).
- **Don't add the vendor JAR yet** (Phase 5). Until then, the emulator build stays free of native code.

**Implementation notes:**
- Also done from finding 18: the serialization plugin version now comes from the catalog, `ui-text` follows the Compose BOM, and the obsolete `composeOptions` block is gone.
- Not done (optional in finding 18): switching to Retrofit's own kotlinx converter, dropping `appcompat`/`material` by changing the theme, and raising the Gradle memory settings.

**Done when:** `./gradlew assembleDebug test` passes. *Confirmed.*

---

## Phase 1: Data and network layer (1–2 days) *(done)*

Make the app talk correctly to the server you've just changed. Nothing visible changes yet, but this is where most of the "would fail against the real server" bugs are.

### Step 1.1: Number helpers *(done)*
- `parseDecimal`, `formatDecimal`, `roundForErp` and `validateQuantity(text, wholeNumber)` in one file (2.9, 2.12 point 6, 2.13 part 1).
- Unit tests for all four. These are the first real tests (2.13 part 7).

**Implementation notes:**
- The file is `util/Decimal.kt`.
- `formatDecimal` returns `"0"` for zero itself: Android 6's `BigDecimal.stripTrailingZeros()` leaves zero as `0.000`, unlike the JVM the tests run on.

### Step 1.2: Models *(done)*
- **`Float` → `Double`** for all quantities (01, finding 14).
- **New and changed fields:**
  - `BomSearchResultItem`: `shelfLife`, `wholeNumber: Int` with `isWholeNumber`, and an `isDefaultBom` getter.
  - `BomDetails` / `BomDetailsItem`: `tolerancePercent`, `wholeNumber`.
  - `ManufactureEntry`: `stockEntryId`, `itemCode`, `quantity`, `uom`, `wholeNumber`, and a non-null `expiryDate`.
  - `BatchDetails`: `stockUom`, the dates, `batchQuantity` and the reference fields (2.12 point 2).
- **Wrappers:** `MessageWrapper<T>` for `/api/method` calls, `DataWrapper<T>` for `/api/resource` calls (§1 #8).
- **Lenient decoding:** `Json { ignoreUnknownKeys = true }` (01, finding 7).
- **Decoding tests:** paste real responses from your server into them (01, finding 16).

**Implementation notes:**
- `ManufactureEntry.name` is now `stockEntryId`, mapped with `@SerialName("name")`, since that's the key the server sends.
- One shared `erpJson` instance (`network/ErpJson.kt`) is used by Retrofit and the tests.
- The decoding tests use responses captured from the dev server. The `create_manufacture_entry` one was taken by repeating an existing `request_id`, so nothing new was created.

### Step 1.3: `ErpService` and `NetworkErpRepository` *(done)*
- **BOM search:**
  - Build the filters and `fields` with `buildJsonArray`, including both link fields (`item.shelf_life_in_days`, `uom.must_be_whole_number`). Drop `encoded = true` (§1 #9, 2.10, 2.13 part 3).
  - Filter on `docstatus = 1`.
  - Handle the 20-row page limit (01, finding 8).
- **Submit:** `create_manufacture_entry` with a JSON `@Body` (`ManufactureEntryRequest`, including `request_id`, quantities via `roundForErp`) (2.4).
- **Batch lookup:** `getBatch` returns `DataWrapper<BatchDetails>`.
- **Errors:** turn Frappe's `_server_messages` into a readable error (01, finding 6). Classify each failure as network / HTTP / server message, so the UI can tell "failed" from "unknown" on submit (2.12 point 4).
  - A 403 / `PermissionError` gets its own message ("This device isn't allowed to…").
  - A `SerializationException` becomes "Unexpected response from the server" (2.14 part 5).
- **Tests:** MockWebServer tests for each call (01, finding 16).

**Implementation notes:**
- **Page limit:** the BOM search asks for a fixed 50 rows (`BOM_SEARCH_LIMIT`) instead of paging, since a search only lists matching BOMs and there are few of them. The sort stays `item_name desc` (still an open question in 01).
- **LIKE escaping:** `\`, `%` and `_` in the query are escaped. Confirmed against the dev server.
- **Errors:** `ErpError` (`network/ErpError.kt`) is a sealed exception with `Network`, `UnexpectedResponse`, `Unauthorized` (401), `Forbidden` (403) and `Http` (anything else, with the server's message). Step 1.4 added `NotConfigured`. `isOutcomeUnknown` (network failure or 5xx) is for the submit message in Step 5.1.
- **Retrofit setup:** `createErpService(baseUrl, client)` builds the service for both the app and the tests.
- **Tests:** they use `mockwebserver3` at the OkHttp version, and the error bodies are real 401 / 403 / 404 / 417 responses from the dev server.

### Step 1.4: Interceptor and settings *(done)*
- **Interceptor:**
  - Add `Accept-Language: bg` (2.12 point 1).
  - Trim the key, and fail early if it's empty (01, finding 10). It stays one field, `api_key:api_secret`, validated for one `:` with text on both sides.
  - Use the shared `normaliseBaseUrl()` (2.5).
- **Settings screen:**
  - Accept only `https` URLs.
  - Fix the race between loading and typing (02, finding 16).
  - Hide the key as you type it.
- **Manifest and backup:**
  - Set `usesCleartextTraffic="false"` explicitly (2.5).
  - Exclude the settings DataStore from backups (01, finding 9).

**Implementation notes:**
- **Language:** `Accept-Language` is `bg` when the device is in Bulgarian and `en` otherwise.
- **Interceptor:** it takes a `Flow<PdaSettings>`, which makes it testable without DataStore. Invalid settings throw `InvalidSettingsException` before anything is sent, and the UI shows that as `ErpError.NotConfigured` ("…missing or invalid. Check them in Settings.").
- **Validators:** `normaliseBaseUrl` and `normaliseApiKey` live in `SettingsRepository.kt`. Settings saves the normalised values.
- **Backup:** `allowBackup` stays `true`. `datastore/` is excluded in both `backup_rules.xml` (API 23–30) and `data_extraction_rules.xml` (API 31+).
- **Default URL:** `PdaSettings.baseUrl` now defaults to `""` (01, finding 20).

### Step 1.5: Switch to the real repository *(done)*
- `DefaultAppContainer` uses `NetworkErpRepository`. Keep the fake for debug builds or the emulator (01, finding 13), e.g. a `BuildConfig` flag or a Settings toggle.
- **Update `FakeErpRepository` to match the new models:** shelf life, whole-number ingredients in `Nos`, a tolerance, and real-looking dates. Also fix its quirks (01, finding 22).

**Implementation notes:**
- **Switch:** a Gradle property, `useFakeErp`, sets `BuildConfig.USE_FAKE_ERP` in debug builds. Release builds always use the network. Set `useFakeErp=true` in `gradle.properties` (or pass `-PuseFakeErp=true`) for the emulator. **Debug builds use the real server by default**, so they need valid Settings.
- **The fake:** it now has a fixed catalogue (5 BOMs, including the server's meatball recipe, and 15 raw-material batches). It mirrors the server's main checks: 404 for an unknown batch, 417 for a fractional whole-number quantity or mismatched items, and the original entry for a repeated `request_id`. Search only matches BOM names, so arbitrary text gives "No results".
- **Start-up:** the certificate store and OkHttp client are `lazy`, so fake builds don't read the PEM files at start-up (01, finding 13).

**Done when:** on the device, against the dev server, a BOM search returns shelf life and the whole-number flag, and the fake still works on the emulator. *The build and tests are confirmed; the device check against the dev server hasn't been reported yet.*

---

## Phase 2: Navigation and state structure (1 day) *(done)*

Fix the crashes and set up the structure the new flow needs.

### Step 2.1: Navigation *(done)*
- **Remove `verticalScroll` from the `NavHost`.** Each screen scrolls itself (§1 #6).
- **Nested `manufacture` graph** (Start, Form, Summary) with one shared `formViewModel(...)` helper (§1 #5, 2.8).
- **Hide the Search button** on Home until Phase 6 (§1 #2).
- **`launchSingleTop`** on navigations (02, finding 8).

**Implementation notes:**
- **Graph contents:** the `manufacture` graph (`MANUFACTURE_GRAPH`) holds Start and Form only. Summary is added with its screen in Step 5.1, rather than as an empty placeholder.
- **Titles:** the top-bar title uses `MainScreen.entries.firstOrNull { … } ?: Home`, so a graph route can't crash `valueOf` (02, finding 17).
- **Scrolling:** Start and Settings scroll themselves, and Form's table is the one scrolling list.

### Step 2.2: `FormViewModel` state *(done)*
- **Saved state:**
  - Stop saving the whole `FormUiState`. Save only inputs as strings, plus the batches as JSON (§1 #3, 2.6).
  - On Form with no BOM loaded, re-fetch.
- **State fields:** add `expiryTyped`, `requestId`, `submitState` and `printState`.
- **Setters:**
  - Clear `items` (and the fetch) only when BOM, quantity or explode actually change (2.7).
  - Fill the expiry from shelf life when the BOM changes (2.10).
- **Fetch:**
  - Cancel an in-flight fetch when its inputs change (02, finding 3).
  - Skip the fetch when it's already `Success` (2.8).
- **Batch changes:** replace `require(...)` with returned errors (02, finding 9).
- **Factory:** `FormViewModel.Factory` stays as it is. The tolerance now comes from `BomDetails`, so no settings are needed.

**Implementation notes:**
- **Saved state:** the selected BOM is saved as JSON (the whole `BomSearchResultItem`, which keeps its shelf life and whole-number flag), alongside the inputs and the batches.
- **`requestId`:** it's a UUID, renewed whenever BOM, quantity or explode actually change, not only the BOM. An edited run can't look like a repeat of an earlier submission.
- **`printState`:** it's `NetworkState<Unit, Throwable>` until the printer's error type exists (Phase 6).
- **Batch errors:** `BatchChangeError` only has `INVALID_INPUT` and `NOT_FOUND` for now. Step 4.3 adds the duplicate and not-in-recipe checks.
- **Restore:** `reloadBomDetails()` re-fetches on Form after a restore and sends no effects. If there's nothing to reload, Form pops back to Start instead of showing a spinner forever. Its error state has a Retry button (02, finding 12).
- **Tests:** the setters' clearing rules are tested here, in `FormViewModelTest`, rather than in Step 2.3.

### Step 2.3: Pure state functions and tests *(done)*
- `canContinue`, `allowedRange` (with ±0.5 for whole-number units), `coverage`, `canSubmit`, `wholeNumberChoices` (starting at 1) and `unworkableIngredients` (2.2, 2.13 parts 4–5).
- Unit tests for them, and for the setters' clearing rules.

**Implementation notes:**
- **Location:** the rules are extensions on `FormUiState` in `ui/screens/FormRules.kt`.
- **Extra rule:** `canFetch` (a BOM, and a quantity valid for its whole-number flag) is what the BOM details request needs. `canContinue` is `canFetch` plus valid expiry days. `canSubmit` also needs valid expiry days.

**Done when:** rotating the screen or backgrounding the app on any screen doesn't crash, and Back from Form, then a different BOM, gives an empty batch list. *Backgrounding confirmed on the device. The empty batch list is covered by `FormViewModelTest`, but can't be seen in the app until batches can be added (Phase 4).*

---

## Phase 3: Start and Form screens (2 days) *(done)*

### Step 3.1: Start screen *(done)*
- **Add the moved fields:** expiry (with the shelf-life prefill and the "Default from product" hint) and "Is finished" (2.10).
- **Quantity:**
  - Whole-number validation and keyboard via the new `QuantityInput(…, wholeNumber)` (2.13 parts 2–3).
  - The BOM unit as the field's suffix.
- **Continue** requires a valid quantity, BOM and expiry. After the fetch, if a whole-number ingredient needs less than 0.5, stay on Start with "The quantity is too small for …" (2.13 part 4).
- **Show the selected BOM** clearly when coming back via "Same BOM again" (2.8).
- **Split `StartScreen.kt`** into smaller files while you're in there (03, finding 14).
- **Fix the selected-row colours** (03, finding 4).

**Implementation notes:**
- **Layout:** Continue stays at the bottom, outside the scrolling fields (2.10 point 5).
- **Too-small runs:** the "quantity is too small" message appears under the quantity field through a new `QuantityInput(extraError = …)`. The ViewModel doesn't navigate when `unworkableIngredients` isn't empty, whether the BOM details were just fetched or already loaded.
- **Expiry field:** its own fixes (03, finding 6) were done while moving it: whole days only, a number keyboard, the `days` string, and calendar-day arithmetic.
- **Files:** the BOM picker moved to `components/BomSelect.kt` (`BomSelect`, `BomSearch`, `BomResultRow`). The Cancel/Select pair is the shared `components/ConfirmCancelButtons.kt`.
- **Selected BOM:** shown in a bordered card with the name, BOM ID and unit.
- **Batch dialog:** still passes `wholeNumber = false` to `QuantityInput` until Step 4.2.

### Step 3.2: Form screen and ingredient table *(done)*
- **Form layout:** batches only. Show the run settings in a read-only header (2.10).
- **`IngredientTable`:**
  - Fix the `String.format` crash (§1 #1).
  - Show required vs chosen with the MISSING / UNDER / OK / OVER colours, or "use 2–3" for whole-number units (2.2, 2.13 part 5).
  - Fix duplicate keys (03, finding 8).
- **Submit button:** enabled by `canSubmit`.
- **Formatting:** use `formatDecimal` everywhere numbers are shown (2.9).

**Implementation notes:**
- **Table input:** `IngredientTable` takes a list of `IngredientLine`s, which `FormUiState.ingredientLines(bom)` builds from the rules. Batch rows take a numeric `BatchRow`, and `BatchInfo` stays for the dialog only.
- **Duplicate keys:** the server builds the ingredient list with `get_bom_items_as_dict` (keyed by item code), so an item can't appear twice. The real clash was the batch-row keys, which are now prefixed (`item|`, `none|`, `batch|<item>|<batch>`).
- **Colours:** amber and green are hard-coded for now (see Phase 7).
- **Not done yet:** the + button's touch target is Step 4.1, and the batch dialog isn't opened from Form until Phase 4.

### Step 3.3: Strings *(done)*
- **Fix the Bulgarian terms:**
  - `bom_label` = "СНМ", in Cyrillic letters.
  - `explode_label` = "Разгръщане".
  - Also fix `search_label`, `amount_label` and `settings_title` (03, finding 9).
- **Move hard-coded strings** into resources, and use format arguments instead of concatenation (03, finding 9).
- **Add the new messages:** whole number required, not in recipe, already added, not a batch label, scan ignored while editing, printer not ready, and the submit-unknown message.

**Implementation notes:**
- **Also fixed:** `invalid_batch_no` gets its full stop.
- **Format strings:** Form's BOM line uses `bom_line`, and quantities with units use `quantity_with_unit`.
- **Retry icon:** in `ErrorMessage` it has no description, because the button text already says Retry.
- **New messages:** they aren't used yet. Phases 4–6 use them.

**Done when:** with the fakes, you can pick a BOM, see the expiry filled in, continue, and see the ingredient table with correct totals and colours. *Not checked on the emulator yet.*

---

## Phase 4: Batch dialog (2 days) *(done)*

The core of the app. It's still driven by manual entry here; scanning plugs in during Phase 5.

### Step 4.1: Dialog state in the Form route *(done)*
- **`BatchDialogState`** (`Adding(BatchInput)` / `Editing(BatchInfo)`), held in a saveable way (2.1).
- **Opening it:** the + button opens `Adding("")`. The batch rows' edit action opens `Editing(batch)`.
- **The + button** gets a translated content description and a bigger touch target (03, finding 10).

**Implementation notes:**
- **File:** `components/BatchDialogState.kt` holds `BatchInput`, `BatchDialogState` and `BatchInfo` (moved from `IngredientTable.kt`).
- **Saving:** `BatchDialogStateSaver` stores the state as JSON, like `FormViewModel`'s saved inputs, so no Parcelize plugin is needed.
- **Owner:** the Form route in `MainApp.kt` holds the state and passes it to `FormScreen`, so the scanner can reach it in Step 5.3.
- **The + button:** a full-width `OutlinedButton`, at least 56dp tall, with the icon and the "Add batch" label. The icon has no description, since the label says it.
- **Row swipes:** swiping right edits and swiping left deletes, and the row slides back after each. The row has a surface background, and the action icons use `onPrimary` / `onError`. There's no delete confirmation yet, and the actions are still swipe-only (Phase 7).

### Step 4.2: Rework `BatchDialog`
- **Batch number:** comes from the caller (2.1).
- **Lookup:**
  - Debounce typing; no debounce for scans.
  - In edit mode, skip the lookup entirely (03, B2).
- **Checks, in order:**
  1. Already added. Checked before the lookup, and again with the server's name after it (2.3).
  2. Not found.
  3. Not in recipe (2.3).
- **After a successful lookup:**
  - Show the matched ingredient's name and unit. The unit comes from `BomDetailsItem.uom` (2.3).
  - Quantity: validate with the ingredient's whole-number flag (2.13 part 4). Track `quantityTyped`. Prefill the remaining amount, rounded for whole-number ingredients.
- **Focus:** the batch field gets focus when the dialog opens from +.
- **Layout:**
  - Padding, a smaller progress indicator and a title.
  - `dismissOnClickOutside = false`.
  - Taller buttons (03, B7).
- **Saved state:** no non-saveable types in `rememberSaveable` (§1 #4).

**Implementation notes:**
- **Lookup in the ViewModel:** the checks are in `FormViewModel.lookupBatch`, not the dialog, so they're unit-tested against the fake. It returns a `BatchMatch` (batch, matched ingredient, prefill) or a `BatchLookupError`: `InvalidInput`, `NotFound`, `AlreadyAdded`, `NotInRecipe(batch)` or `Failed(ErpError)`. It replaces `FetchBatchError`, and `Failed` shows the same messages as the rest of the app via `errorText`.
- **Case:** the server matches batch names case-insensitively (`beef.0001` returns `BEEF.0001`, checked on the dev server), so `FormUiState.hasBatch` ignores case.
- **Prefill:** `FormUiState.prefillQuantity` is what's still needed, rounded for whole-number units and null when nothing is.
- **Dialog state:** only the quantity text and `quantityTyped` are saved. The lookup result is `remember(input)`, so it resets as soon as the batch number changes and an earlier match can't be added.
- **Edit mode:** the batch field is read-only, and Save needs a valid quantity that differs from the original.
- **Also done:**
  - The supporting line under the batch field is always reserved, and shows "Ingredient: …" once matched.
  - Batch numbers are typed in capitals, with `ImeAction.Next`.
  - The buttons are Add / Save.
  - `ConfirmCancelButtons` buttons are at least 56dp tall, and its padding moved to `BomSelect`.

### Step 4.3: `upsertBatch` / `removeBatch` *(done)*
- Return errors instead of crashing. Reject duplicates and items that aren't in the recipe here too (2.3).
- *Partly done in Step 2.2:* both already return a `BatchChangeError?` instead of throwing. What's left is adding the duplicate (add mode only) and not-in-recipe checks.

**Implementation notes:**
- **Split:** `upsertBatch` is now `addBatch` and `updateBatch`. Both reject invalid input, items not in the loaded BOM (`NOT_IN_RECIPE`) and fractions for whole-number ingredients. `addBatch` also rejects batches already on the form (`ALREADY_ADDED`, ignoring case). `updateBatch` only changes batches already on the form (`NOT_FOUND` otherwise).
- **The dialog ignores these errors:** its lookup has already run the same checks, so they're only a backstop for future code paths.

**Done when:** with the fake repository, you can add, edit and remove batches by typing. Duplicates, unknown batches, wrong items and fractional Nos quantities are all rejected under the right field, and Submit enables only when every ingredient is in range. *Built and tested; the device run in Phase 5 goes through the dialog.*

**Fixed after review:**
- **Instant delete:** a deleted row's swipe state was saved under its key, so adding the same batch again brought it back dismissed and deleted it at once. The swipe state is now plain `remember`.

---

## Phase 5: Submit, Summary and the scanner (2 days) *(done)*

### Step 5.1: Submit and Summary *(done)*
- **Submit:** send with `requestId`. A 500 or network failure shows "Couldn't confirm the submission. Retry to check.", and Retry re-sends the same `requestId` (2.12 point 4).
- **Summary screen:** shows the entry. Navigating to it replaces Form, so Back can't reach the submitted form (2.8). Add its `composable` to the `manufacture` graph, which holds only Start and Form so far (Step 2.1).
- **Effects on Form:** only the Start route collects `FormViewModel.effects` today, so submit's success and failure effects need a collector on Form too (02, finding 11).
- **"Same BOM again"** calls `startAgain()` (new `requestId`, batches cleared) and pops back to Start.
- **Done / Back** leaves the graph.
- **Print button:** add it now with `FakeLabelPrinter`. It logs what it would print (2.11 part 4).

**Implementation notes:**
- **Saved state:** the submitted entry is saved with the inputs, so Summary survives process death. `canSubmit` is false once the run has been submitted.
- **Retry:** after an unknown outcome the Submit button reads Retry. Other failures show the server's message.
- **Back during a submit:** blocked on Form, and the top-bar arrow is greyed out. A late result could otherwise land on a changed run, or be lost when the graph is left. The arrow now goes through the back dispatcher, so it follows the routes' `BackHandler`s.
- **Summary:** keeps the entry it opened with, so "Same BOM again" can clear it while the page animates out. Back and Done pop the whole `manufacture` graph.
- **Effects:** one shared `CollectFormEffects` on Start, Form and Summary. `GotoNextPage` is now `GotoForm`, alongside the new `GotoSummary`.
- **Printing:** `hardware/LabelPrinter.kt` has `LabelData` (nullable dates, for Details), `ManufactureEntry.toLabel()`, the `LabelPrinter` interface and `FakeLabelPrinter`. A failed print shows "Printer not ready" as a toast.
- **Dates:** `dateFromToday` and `formatDate` moved to `util/Dates.kt`.
- **Tests:** `unitTests.isReturnDefaultValues = true`, since `Log` calls threw in local tests, and effects are collected on `UnconfinedTestDispatcher(testScheduler)` so they arrive before the assertions.

### Step 5.2: Vendor SDK setup *(done)*
- **Build:**
  - Add `implementation(files("libs/qspdasdk.jar"))`.
  - `git mv jniLibs/armeabi jniLibs/armeabi-v7a` (2.11 part 1).
  - Add the keep rules to `rules.keep`.
- **`QsPdaHardware.init(app)`** in `MainApplication.onCreate`, with `applicationContext`, the 2500 ms delay and `setStarNum(30)`. Catch `LinkageError` (2.11 part 2).
- **Check the APK:** `unzip -l app-debug.apk | grep '\.so'` should show the library in every ABI folder.

**Implementation notes:**
- **APK:** confirmed, with `libzyapi_common.so` in both `armeabi-v7a` and `arm64-v8a`.
- **Scans off at start-up:** `PrintUtils.isCanScan = false` before `initPrintUtils`. The SDK only writes it in its static initializer, so it stays off until a scanning screen turns it on.
- **Logging:** `Build.MODEL` and the serial port descriptor (`mComFd`) are logged at start-up. The SDK fails silently if the port doesn't open. On the test device the descriptor is 23.
- **Not used yet:** `QsPdaHardware.available` chooses the scanner (Step 5.3). The printer is still always `FakeLabelPrinter` (Step 6.5).
- **Device findings:**
  - The vendor demo says to uninstall the factory test apps first, since they compete for the serial port. Done on the test device.
  - Launching in Debug from Android Studio can leave the scan engine off until the next start. The module still answers `##55UP`, but its light stays off. It's probably the debugger slowing the SDK's timed start-up. Use "Attach Debugger to Android Process" on an app started normally instead.

### Step 5.3: Scanner *(done)*
- **Interfaces:**
  - `BarcodeScanner` with `scans: Flow<String>` and `setActive(Boolean)`.
  - `BroadcastBarcodeScanner`, listening for `com.qs.scancode` / `"data"`.
  - `FakeBarcodeScanner` for the emulator (2.1, 2.11 part 3).
- **First, a device test:** log every broadcast on both `com.qs.scancode` and `ismart.intent.scandown` while pressing the trigger. This answers the last open question. If only `ismart.intent.scandown` carries `data`, change the action.
- **`OnScan { }` helper:** sets `PrintUtils.isCanScan` through `setActive`. On Form, use it only once the BOM has loaded. Search and Details use it too (Phase 6).
- **`parseScan`** with its unit tests (2.11 part 5).
- **Scan handling:**
  - A scan opens the dialog, or replaces the batch number in it.
  - Ignore scans in edit mode, with a toast.
  - Clear focus and hide the keyboard after a scan (2.1).
- **`ScanFeedback`:** beep when a lookup is accepted, using `res/raw/scan_beep.ogg` (2.11 part 3).

**Implementation notes:**
- **Device test:** results arrive on `com.qs.scancode` / `"data"`, and the firmware broadcasts `ismart.intent.scandown` on each trigger press. The vendor demo (`MainActivity.java`) confirms both.
- **Events:** `BarcodeScanner.events` emits `ScanEvent.Code(text)` or `ScanEvent.NoRead`. When the engine reads nothing it replies `ESC ##55UP`, which the SDK drops, so `NoRead` is a timeout: a trigger press with no code within 3.5 s (the SDK powers the engine down at 3 s). Form shows it as a "Couldn't read the code" toast.
- **`setActive`:** counted rather than on/off, since two routes can both be started during a transition. `OnScan` uses `repeatOnLifecycle(STARTED)`, so scanning also pauses in the background.
- **`parseScan`:** also strips a leading ECI designator (a backslash and six digits, e.g. `\000026` for UTF-8). The scan engine adds it to QR codes that declare a character set.
- **Scan handling:**
  - Each scan has its own `scanId`, so scanning the same label again looks it up again.
  - A code that isn't a batch label shows "This code is not a batch label" under the field, without a lookup (`BatchLookupError.NotABatchLabel`).
  - Scans are off on Form while a submit is in flight.
- **Sounds:** `ScanFeedback` plays `res/raw/beep1.ogg` when a scanned batch is accepted and `beep3.ogg` with the no-read toast, through `SoundPool`.
- **Not done:** there's no way to trigger `FakeBarcodeScanner` on the emulator.
- **Printing for Step 6.5:** `com.qs.getmassage` sends `message` `0` when printed and `1` when out of paper, which gives a real print result.
- **Keypad, alongside this step:**
  - Enter moves from the batch number to the quantity, and in a valid quantity it adds or saves the batch.
  - On Start, Enter moves from quantity to expiry, but doesn't press Continue, since the checkboxes come after it.
  - Enter in the BOM search puts the keyboard away, and picking a BOM moves focus to the quantity.
  - The batch dialog waits for its window to have focus before opening the keyboard. Its focus and keyboard calls use the dialog's own window.
  - The keypad's Fn key (letters or digits) isn't visible to apps, so there's no mode indicator.

**Done when:** on the device, a full run works end to end:
1. Scan ingredient labels.
2. Submit.
3. See the Summary.
4. "Same BOM again" returns to Start with everything kept.

*Confirmed on the device.*

---

## Phase 6: Search, Details and printing (3 days)

The design is in 2.14 (summary). Search and Details only need Phase 1 (the network layer), plus the `OnScan` helper and `parseScan` from step 5.3. So this phase can come before Phase 5 if you'd rather have them first. In that case, build `OnScan`/`parseScan` here and use `FakeBarcodeScanner`.

### Step 6.1: Data layer for Search and Details
- **Repository:** add `ErpRepository.searchBatches(query, filters, start)` and `getStockEntry(name)`. The search uses `or_filters` on batch number and product name, the `item.default_bom is set` filter, the day limit and "hide expired", `order_by` from the enum, and paging with `limit_start` / `limit_page_length = 30` (2.14 part 2). Add the `StockEntryDetails` / `StockEntryRow` models (2.14 part 4).
- **The request is confirmed** with `curl` as `pda-test` (04, "For the Search and Details pages", item 4): the filters, `or_filters`, all three sorts and paging. Copy its `fields`, `filters` and `order_by` into `searchBatches` and its MockWebServer test. For the product-name sort, use the `item_name` alias, not `` `tabItem`.item_name ``.
- **No server change for "manufactured only":** filter on `item.default_bom is set` (2.14 part 2).
- **Details loads its batch with a one-row list request** (`name = <batch>`), so `default_bom` and `item_name` come through the links. The batch dialog keeps the single-document `getBatch`.
- **`FakeErpRepository`:** batches with a mix of expiry dates, and a fake Stock Entry with several ingredient batches.
- **Tests:** `expiryStatus` (each boundary), MockWebServer tests for both new calls, and decoding a real Stock Entry response, child rows included.

**Implementation notes:**
- **One batch request:** `getBatch` itself now uses the one-row list request (`fields` with the `item.*` links, `filters=[["name","=",<batch>]]`), for the batch dialog, Search and Details alike. The single-document `/api/resource/Batch/<id>` endpoint is gone. The list request matches names regardless of case, like the old one, and an empty result is a 404. A missing batch now carries the app's own "not found" text rather than the server's, which nothing shows.
- **`BatchDetails.defaultBom`:** comes through `item.default_bom` (null for raw materials). It's how Details tells a batch is "made here".
- **`BatchFilters` / `BatchSort`:** in `model/BatchFilters.kt`. `@Serializable`, so Search saves them as JSON. `withSort` also picks that sort's usual direction.
- **Stock Entry:** `StockEntryDetails.ingredients` is every row that isn't the finished item. `create_manufacture_entry` adds one row per batch, so an ingredient drawn from two batches shows as two rows.
- **Today's date:** `NetworkErpRepository` takes a `today` provider, so the tests can fix the date behind the day limit and "hide expired".
- **The fake:** five finished batches, one for each expiry state (expired, today, tomorrow, next week, none), each with a Stock Entry. A submit stores a Stock Entry too.
- **Tests:** the real Stock Entry is saved as `test/resources/stock_entry.json`. The unused `FakeDataSource`, `FakeErpService` and `FakeNetworkErpRepository` test fakes are removed.

### Step 6.2: Navigation
- **A `batches` graph:** Search, then `Details/{batch}`.
- **Type-safe routes.** Either move to them now, or fix `MainScreen.valueOf(route)` for routes with arguments (2.14 part 1; 02, finding 17). Type-safe routes are recommended, and this is the first route that needs an argument.
- **Show the Search button** on Home again (it was hidden in step 2.1).

**Implementation notes:**
- **Type-safe routes:** in `ui/Routes.kt`, `@Serializable` objects (`Home`, `Settings`, `ManufactureGraph`, `Start`, `Form`, `Summary`, `BatchesGraph`, `Search`) and `data class Details(val batch: String)`. `MainScreen` and `MANUFACTURE_GRAPH` are gone.
- **Titles:** `NavDestination?.title()` matches the destination against the route classes with `hasRoute`.
- **Home's buttons** take a route object. The `Settings` route is imported as `SettingsRoute` in `HomeScreen.kt`, since it clashes with the Settings icon.
- **Filters reset on each visit:** going back to Home pops `BatchesGraph`, so Search starts with the defaults without extra code.
- *The navigation was confirmed working on the device, with the placeholder Search and Details screens.*

### Step 6.3: Search page
- **`SearchViewModel`:**
  - The query text, the result list, and states for loading / no results / error.
  - `onScan(raw)` runs `parseScan`, then `getBatch`, then sends a navigate effect.
  - `onSubmit()` (Enter) does an exact lookup, and navigates if found.
  - Typing debounces, then calls `searchBatches`, matching on batch number or product name. Stock levels play no part.
  - `BatchFilters` (sort by production date, expiry date or product name; direction; "produced in the last N days" as whole days; "hide expired" off by default), kept in `SavedStateHandle`, reset on each visit from Home.
  - An empty field lists the latest batches straight away.
  - Paging: 30 rows, "Load more" with `limit_start`. Any change of text or filters starts at page one.
- **Screen:**
  - `SearchField` (with translated icon descriptions; 03, finding 9), with a ⚙ button (● when the filters aren't the defaults) and a one-line filter summary.
  - **Filter bottom sheet** (`ModalBottomSheet`): sort, direction, the day limit, "Hide expired", Reset. Changes apply when it closes.
  - Result rows showing product, batch, quantity, both dates and the ⚠ from `expiryStatus`. "Load more" at the bottom.
- **Scanner:** `OnScan` on this route.

**Implementation notes:**
- **Enter vs scan:** a scan of an unknown batch shows "Batch not found." under the field. Enter on typed text that isn't a batch number shows no error and keeps the list, which may be empty with "No results" (confirmed as the intended behaviour).
- **Filter sheet:** `components/BatchFilterSheet.kt` edits a draft, saved across rotation, and applies it when the sheet closes, however it's closed. The order buttons are a segmented pair whose labels follow the sort. A day limit that isn't a whole number keeps the previous one. The Bulgarian expiry labels are the shorter "Най-скоро / Най-късно".
- **Sheet and scans:** the sheet's open state lives in the route, which closes it before opening Details. A scan with the sheet open doesn't bring the sheet back on return.
- **Paging:** a new search cancels a "Load more" in flight, so an old page can't land on new results.
- **Rows:** `components/BatchResultRow.kt`, with dates as `dd.MM.yyyy` (`util/Dates.kt`'s `shortDate`). The ⚠ shows for expired, today and soon.
- **`SearchField`** gains `placeholder` and `onSearch` (Enter). The BOM search is unchanged.
- **Saved-state keys:** Search's are in `SavedSearch`, since a second top-level `Saved` in the package clashed with Form's at build time.

### Step 6.4: Details page
- **`DetailsViewModel(batch)`:** load the Batch, then the Stock Entry from `reference_name`. Each has its own loading and error state, with Retry for the ingredients alone.
- **Screen:**
  - The expiry banner, and an "isn't produced here" state when `defaultBom` is empty (no Print button).
  - Quantity (`batch_qty` + `stock_uom`), produced date, Stock Entry ID.
  - Ingredients used, as a plain list of batch numbers and quantities.
  - A "not produced with this app" state for other batches (no Print button).
- **Scanner:** `OnScan` on this route. A scan replaces the Details with `popUpTo(Details) { inclusive = true }`, so Back always goes to Search.

**Implementation notes:**
- **The ViewModel** takes the batch number directly, and only its factory reads the route (`SavedStateHandle.toRoute`), so it can be tested without Android.
- **No Stock Entry request** for a batch that isn't made here, or has no `reference_name`, so a supplier's label never fetches a Material Receipt as if it were production.
- **Scans:** a scan is looked up first and only replaces the page once found. An unknown batch or a code that isn't a batch label shows a toast and keeps the current batch.
- **Banner:** `tertiaryContainer` for "soon" until Phase 7's status colours. "Expires in N days" uses the `expires_in_days_banner` plurals (ден / дни).
- **`LabelledValue`:** Summary's private `SummaryLine` is now shared in `components/`.
- **Scan sounds, across every scanning screen:** `beep1` for an accepted scan and `beep3` for a time-out or an invalid scanned value (not a batch label, not found; on Form also already added or not in the recipe). Network and server errors are silent. Typing plus Enter on Search is never a scan, so it never beeps.

### Step 6.5: Label printer
- **`LabelData`:** `toLabel()` for `ManufactureEntry` (Summary) and `BatchDetails` (Details).
- **QR content:** `batchUrl(...)`, built from the Settings base URL (2.11 part 5).
- **Label bitmap:** render it at 384 px, with the QR from the bundled zxing `QRCodeWriter` and `dd.MM.yyyy` dates.
- **`QsPdaLabelPrinter`:** check `mComFd` and `isCanprint`, then call `printBitmap(1, bmp, true)` on the main thread (05, section 4).
- **Print buttons** on Details and Summary: disabled while printing, a toast on failure, re-printable.

**Implementation notes:**
- **Vendor documentation:** "408 Print & Scan Integrated Doc" confirms 384 px for 58 mm paper and 570 px for 80 mm, black and white only, and print results `0` (printed) and `1` (no paper). Its code figures are screenshots of the vendor demo (`MainActivity.java`). It asks for a 5–7 mm blank margin above the gap, or the printer feeds an extra blank label.
- **Label layout:** `hardware/LabelRenderer.kt` draws one black-and-white bitmap. The QR code is on the left. Beside it are the product name (up to two lines), batch number, quantity, `P:` / `E:` dates (`П:` / `Г:`) and Stock Entry ID. Only the dates have captions. The quantity and both dates always print whole; the name, batch number and Stock Entry ID may end in "…". The text starts at 28 px and shrinks to fit, down to 20 px. Anti-aliasing is off, since the printer can't print grey.
- **QR code:** the batch URL from `batchUrl`, level M, with no character set hint so it gets no ECI prefix. It takes what's left beside the dates, up to the label's height, and is capped at 180 px (about 22 mm) on 58 mm paper so the batch number stays readable.
- **SDK buffer bug:** `PrintUtils.draw2PxPoint` sends the picture in 24-row bands, but its buffer is too short for a partial last band on 570 px paper, so the end of the job was silently cut off (15 bytes at 80 × 40 mm). `labelBitmapHeight` rounds the bitmap down to whole bands. `LabelRendererTest` checks every allowed height on both papers against the SDK's formula.
- **Paper settings:** "Label paper" (80 mm, the default, or 58 mm) and "Label height" (30–150 mm, default 40) in Settings. Below 30 mm the text no longer fits beside the QR code. The width is drawn as is: the SDK only scales bitmaps down when they're wider than the printer.
- **Print result:** the printer checks `mComFd` and `isCanprint` first, then waits up to 4 s for `com.qs.getmassage`. `1` fails the print; `0`, or no broadcast at all, counts as sent. If the printer stops being ready within the SDK's 200 ms delay, it drops the job silently and the app still reports success.
- **Device printer:** `AppContainer` uses `QsPdaLabelPrinter` when `QsPdaHardware.available`, otherwise `FakeLabelPrinter`.
- **Not testable locally:** the bitmap itself; `LabelRendererTest` covers only the size calculations. Check on paper.
- **No QR code:** the QR is only drawn when the Settings base URL is valid. With it empty (e.g. a fake-ERP build), the label prints text only.
- **Paper type:** Settings has "Labels with gaps" (the default) or "Continuous roll". The printer has a gap sensor: in label mode it feeds to the next gap on paper load and after each print (`isLabel = true` sends `1D 0C`). On a plain roll there's no gap, so it runs out about 225 mm each time. For a continuous roll the app turns label mode off (`labelEnable(false)`, sent after the SDK's start-up reset and again whenever the setting changes), prints with `isLabel = false`, and draws the whole label height itself (to the nearest 24-row band). The last 12 mm (`TEAR_OFF_ROWS`) stay blank: the tear bar is past the print head, so the end of each print stays in the printer and starts the next piece. A roll label must be at least 35 mm, which leaves the same printable area as a 30 mm die-cut label.
- **SDK buffer:** `PrintUtils.draw2PxPoint` allocates `width × height / 8 + 1200` bytes but writes 24-row bands of `6 + 3 × width` bytes, dropping whatever doesn't fit. A partial last band at 570 px cuts the end of the job off, so every bitmap height is whole bands (`labelRows`).
- **Die-cut labels:** 80 mm labels are slightly too wide for the test device's paper bay. 58 mm is still to be tried.

**Done when:**
- On the device, scanning a label on Search opens its Details, with the expiry banner and ingredients.
- Scanning another label on Details shows that batch, and Back goes to Search.
- Search opens on the latest batches. Typing "saus" lists the sausage batches. Sorting by expiry or product name reorders the list. Limiting to 7 days and hiding expired batches narrow it, and the ⚙ dot shows they're active.
- Raw-material batches (e.g. BEEF.0001) don't appear in the list.
- A label printed from Details scans back to the same page.

*Built and pushed; the device check above hasn't been reported yet.*

---

## Phase 7: Polish and release (as time allows)

- **Glove use:** bigger touch targets and text, visible edit/delete actions instead of swipe-only (03, findings 10 and 12).
- **Previews:** for the new components, including whole-number and error states (03, finding 16).
- **Status colours in the theme:** the amber (UNDER) and green (OK) in `IngredientTable.kt`'s `coverageColor` are hard-coded, with a dark-theme variant each. Move them into the theme, e.g. a small `StatusColors` set provided next to the colour scheme, so other screens (the expiry banner on Details) can reuse them.
- **Release build:**
  - Turn on R8 and check the SDK still works with the keep rules (05, section 7).
  - Set up signing (01, finding 17).
- **Leftovers:** the remaining tidy-ups (typos, unused imports, package moves, consistent error types) from 01, 02 and 03.
- **Keyboard suggestions:** turn off suggestions and autocorrect outside the search fields. A `NoSuggestions` wrapper using `InterceptPlatformTextInput` is in `git stash` ("Turn off keyboard suggestions outside search fields"), but it's from before Phase 5's changes to those fields, so it needs reapplying by hand.
- **Emulator scanning:** there's no way to trigger `FakeBarcodeScanner` on the emulator yet (Step 5.3).

**Implementation notes:**
- **From the device:** the Home buttons are larger. The pages' main buttons (Continue, Submit, Done, Save) are at least 56 dp tall, like the outlined buttons beside them. Checkbox labels toggle their checkbox (`CheckboxRow`, shared with the filter sheet).
- **Ingredient table:** the swipes are gone. Each batch row has 48 dp Edit and Delete buttons, with the quantity and unit under the batch number. The column header is gone too, since the rows explain themselves, and `amount_label` with it. There's still no delete confirmation.
- **Previews:** `@ThemePreviews` shows each preview in the light and dark theme, and `PreviewTheme` uses the app's own colours (the PDA's Android 6 has no dynamic colour) on a surface. All previews are `private`. New ones: `IngredientTable` (every coverage colour, and a whole-number ingredient), `ErrorMessage`, `SearchField`, `ConfirmCancelButtons`, `CheckboxRow`, `LabelledValue` and `SettingsScreen`. `SettingsScreen` now takes its state and callbacks, and its route holds the ViewModel, like the other screens. `BatchDialog` and `BatchFilterSheet` have none: they're in their own windows, which previews don't show well.
- **Status colours:** `theme/StatusColors.kt` has `warning` (amber) and `success` (green) `ColorFamily`s for light and dark, provided by `AppTheme` and read as `statusColors`. They stay the same with dynamic colour, so their meaning doesn't change with the wallpaper. The ingredient table's UNDER and OK use them (same colours as before), and the Details banner for "expires soon" uses the warning container instead of the tertiary one.
- **Tidy-ups:** most of 01, 02 and 03's were already done in earlier phases. This round:
  - **Retrofit:** the official `converter-kotlinx-serialization` replaces Jake Wharton's archived one.
  - **Packages:** `NetworkState` and `JobSlot` move from `network` to `ui.state`, and `JobSlot` loses its unused `launchIfIdle` and `join`.
  - **Settings:** laid out like Start (20 dp padding, 10 dp spacing, Save at the bottom outside the scroll), with `modifier` parameters on its fields and a toast after saving, or if saving fails.
  - **Small fixes:** `MainApplication.container` has a private setter; `HomeScreen` and `ErrorMessage` take `modifier` as their first optional parameter; an unused import and the unused `valid_label` string are gone. Gradle stays at 2 GB: 4 GB strained the machine alongside Android Studio.
  - **Left as is:** the theme files (Compose and XML), and with them the `material` and `appcompat` libraries the XML theme needs. `printState` keeps `Throwable`, since a printer failure isn't an `ErpError`. The `"name (unit)"` and `" · "` joins have no words to translate.
- **Release build:**
  - **R8:** `isMinifyEnabled` and `isShrinkResources` on `release`, with the default `proguard-android-optimize.txt`. The newer `optimization { enable = true }` needs the undocumented `android.r8.gradual.support` flag before AGP 9.3, so it waits for the AGP update. The vendor SDK's keep rules are in `src/main/keepRules/rules.keep`. kotlinx.serialization (models, saved state, routes), Navigation and Retrofit ship their own rules, so none are added for them.
  - **Versions:** `versionCode` is the commit count (`git rev-list --count HEAD`), so each commit on master is newer than the last. `versionName` is the short commit hash. Without git they fall back to `1` / `"dev"`. Build releases from a full clone, since a shallow one undercounts.
  - **Signing:** `keystore.properties` (git-ignored, with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`) in the project root names the key. Without it, release builds are unsigned. v1 signing stays on, because Android 6 only checks v1. `.gitignore` also covers `*.jks` and `*.keystore`.

---

## How the phases depend on each other

```
0 Build ─▶ 1 Data/network ─▶ 2 Navigation/state ─▶ 3 Start + Form ─▶ 4 Batch dialog ─▶ 5 Submit, SDK, scanner ─▶ 6 Search, Details, printing ─▶ 7 Polish
```

- **Phases 0–4** need nothing from the device; the fakes cover everything.
- **The device test in step 5.3** is the earliest point where the hardware matters. You can do it earlier on its own if you like: it's a ten-minute throwaway receiver, and it settles the last open question.
- **Phase 6** can move before Phase 5 if you'd rather have Search and Details working first. It needs Phase 1, plus `OnScan` and `parseScan`, which you'd then build in Phase 6 instead.
