# Implementation order for the app

The server work ([04-server-changes.md](04-server-changes.md)) is done. This is a suggested order for the app work. It's built so that:

- **The app runs at the end of every step.** Each step is small enough for one commit (or a few), and you can try it on the emulator, which uses the fakes, or on the device.
- **Foundations come first.** Models, networking and number handling are what every later screen relies on. Fixing them first means you don't rewrite screens twice.
- **Hardware comes late.** The scanner and printer are behind interfaces, so the flow can be built and tested with fakes before the SDK is involved.

Section references point to [00-summary.md](00-summary.md) unless another file is named. "§1 #3" means item 3 in the summary's section 1 table.

---

## Before you start: server *(done)*

All the server work in [04-server-changes.md](04-server-changes.md) is done and tested, including the **PDA User** role (A7) and every `curl` check for Search and Details. On the device, use a PDA user that has **only** the PDA User role, such as `pda-test`. A new PDA user needs the role assigned by hand.

---

## Phase 0: Build and tooling (half a day)

Get the project building cleanly with tests running, so every later step can be checked.

### Step 0.1: Make the tests compile
- Fix `FakeDataSource` / `FakeErpService` / `FakeNetworkErpRepository` against the current interfaces (§1 #10).
- Add one trivial test, so `./gradlew test` runs green.

### Step 0.2: Tidy the build
- Remove duplicate and unused catalog entries, e.g. `coil` (01, finding 18).
- **Don't add the vendor JAR yet** (Phase 5). Until then, the emulator build stays free of native code.

**Done when:** `./gradlew assembleDebug test` passes.

---

## Phase 1: Data and network layer (1–2 days)

Make the app talk correctly to the server you've just changed. Nothing visible changes yet, but this is where most of the "would fail against the real server" bugs are.

### Step 1.1: Number helpers
- `parseDecimal`, `formatDecimal`, `roundForErp` and `validateQuantity(text, wholeNumber)` in one file (2.9, 2.12 point 6, 2.13 part 1).
- Unit tests for all four. These are the first real tests (2.13 part 7).

### Step 1.2: Models
- **`Float` → `Double`** for all quantities (01, finding 14).
- **New and changed fields:**
  - `BomSearchResultItem`: `shelfLife`, `wholeNumber: Int` with `isWholeNumber`, and an `isDefaultBom` getter.
  - `BomDetails` / `BomDetailsItem`: `tolerancePercent`, `wholeNumber`.
  - `ManufactureEntry`: `stockEntryId`, `itemCode`, `quantity`, `uom`, `wholeNumber`, and a non-null `expiryDate`.
  - `BatchDetails`: `stockUom`, the dates, `batchQuantity` and the reference fields (2.12 point 2).
- **Wrappers:** `MessageWrapper<T>` for `/api/method` calls, `DataWrapper<T>` for `/api/resource` calls (§1 #8).
- **Lenient decoding:** `Json { ignoreUnknownKeys = true }` (01, finding 7).
- **Decoding tests:** paste real responses from your server into them (01, finding 16).

### Step 1.3: `ErpService` and `NetworkErpRepository`
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

### Step 1.4: Interceptor and settings
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

### Step 1.5: Switch to the real repository
- `DefaultAppContainer` uses `NetworkErpRepository`. Keep the fake for debug builds or the emulator (01, finding 13), e.g. a `BuildConfig` flag or a Settings toggle.
- **Update `FakeErpRepository` to match the new models:** shelf life, whole-number ingredients in `Nos`, a tolerance, and real-looking dates. Also fix its quirks (01, finding 22).

**Done when:** on the device, against the dev server, a BOM search returns shelf life and the whole-number flag, and the fake still works on the emulator.

---

## Phase 2: Navigation and state structure (1 day)

Fix the crashes and set up the structure the new flow needs.

### Step 2.1: Navigation
- **Remove `verticalScroll` from the `NavHost`.** Each screen scrolls itself (§1 #6).
- **Nested `manufacture` graph** (Start, Form, Summary) with one shared `formViewModel(...)` helper (§1 #5, 2.8).
- **Hide the Search button** on Home until Phase 6 (§1 #2).
- **`launchSingleTop`** on navigations (02, finding 8).

### Step 2.2: `FormViewModel` state
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

### Step 2.3: Pure state functions and tests
- `canContinue`, `allowedRange` (with ±0.5 for whole-number units), `coverage`, `canSubmit`, `wholeNumberChoices` (starting at 1) and `unworkableIngredients` (2.2, 2.13 parts 4–5).
- Unit tests for them, and for the setters' clearing rules.

**Done when:** rotating the screen or backgrounding the app on any screen doesn't crash, and Back from Form, then a different BOM, gives an empty batch list.

---

## Phase 3: Start and Form screens (2 days)

### Step 3.1: Start screen
- **Add the moved fields:** expiry (with the shelf-life prefill and the "Default from product" hint) and "Is finished" (2.10).
- **Quantity:**
  - Whole-number validation and keyboard via the new `QuantityInput(…, wholeNumber)` (2.13 parts 2–3).
  - The BOM unit as the field's suffix.
- **Continue** requires a valid quantity, BOM and expiry. After the fetch, if a whole-number ingredient needs less than 0.5, stay on Start with "The quantity is too small for …" (2.13 part 4).
- **Show the selected BOM** clearly when coming back via "Same BOM again" (2.8).
- **Split `StartScreen.kt`** into smaller files while you're in there (03, finding 14).
- **Fix the selected-row colours** (03, finding 4).

### Step 3.2: Form screen and ingredient table
- **Form layout:** batches only. Show the run settings in a read-only header (2.10).
- **`IngredientTable`:**
  - Fix the `String.format` crash (§1 #1).
  - Show required vs chosen with the MISSING / UNDER / OK / OVER colours, or "use 2–3" for whole-number units (2.2, 2.13 part 5).
  - Fix duplicate keys (03, finding 8).
- **Submit button:** enabled by `canSubmit`.
- **Formatting:** use `formatDecimal` everywhere numbers are shown (2.9).

### Step 3.3: Strings
- **Fix the Bulgarian terms:**
  - `bom_label` = "СНМ", in Cyrillic letters.
  - `explode_label` = "Разгръщане".
  - Also fix `search_label`, `amount_label` and `settings_title` (03, finding 9).
- **Move hard-coded strings** into resources, and use format arguments instead of concatenation (03, finding 9).
- **Add the new messages:** whole number required, not in recipe, already added, not a batch label, scan ignored while editing, printer not ready, and the submit-unknown message.

**Done when:** with the fakes, you can pick a BOM, see the expiry filled in, continue, and see the ingredient table with correct totals and colours.

---

## Phase 4: Batch dialog (2 days)

The core of the app. It's still driven by manual entry here; scanning plugs in during Phase 5.

### Step 4.1: Dialog state in the Form route
- **`BatchDialogState`** (`Adding(BatchInput)` / `Editing(BatchInfo)`), held in a saveable way (2.1).
- **Opening it:** the + button opens `Adding("")`. The batch rows' edit action opens `Editing(batch)`.
- **The + button** gets a translated content description and a bigger touch target (03, finding 10).

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

### Step 4.3: `upsertBatch` / `removeBatch`
- Return errors instead of crashing. Reject duplicates and items that aren't in the recipe here too (2.3).

**Done when:** with the fake repository, you can add, edit and remove batches by typing. Duplicates, unknown batches, wrong items and fractional Nos quantities are all rejected under the right field, and Submit enables only when every ingredient is in range.

---

## Phase 5: Submit, Summary and the scanner (2 days)

### Step 5.1: Submit and Summary
- **Submit:** send with `requestId`. A 500 or network failure shows "Couldn't confirm the submission. Retry to check.", and Retry re-sends the same `requestId` (2.12 point 4).
- **Summary screen:** shows the entry. Navigating to it replaces Form, so Back can't reach the submitted form (2.8).
- **"Same BOM again"** calls `startAgain()` (new `requestId`, batches cleared) and pops back to Start.
- **Done / Back** leaves the graph.
- **Print button:** add it now with `FakeLabelPrinter`. It logs what it would print (2.11 part 4).

### Step 5.2: Vendor SDK setup
- **Build:**
  - Add `implementation(files("libs/qspdasdk.jar"))`.
  - `git mv jniLibs/armeabi jniLibs/armeabi-v7a` (2.11 part 1).
  - Add the keep rules to `rules.keep`.
- **`QsPdaHardware.init(app)`** in `MainApplication.onCreate`, with `applicationContext`, the 2500 ms delay and `setStarNum(30)`. Catch `LinkageError` (2.11 part 2).
- **Check the APK:** `unzip -l app-debug.apk | grep '\.so'` should show the library in every ABI folder.

### Step 5.3: Scanner
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

**Done when:** on the device, a full run works end to end:
1. Scan ingredient labels.
2. Submit.
3. See the Summary.
4. "Same BOM again" returns to Start with everything kept.

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

### Step 6.2: Navigation
- **A `batches` graph:** Search, then `Details/{batch}`.
- **Type-safe routes.** Either move to them now, or fix `MainScreen.valueOf(route)` for routes with arguments (2.14 part 1; 02, finding 17). Type-safe routes are recommended, and this is the first route that needs an argument.
- **Show the Search button** on Home again (it was hidden in step 2.1).

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

### Step 6.4: Details page
- **`DetailsViewModel(batch)`:** load the Batch, then the Stock Entry from `reference_name`. Each has its own loading and error state, with Retry for the ingredients alone.
- **Screen:**
  - The expiry banner, and an "isn't produced here" state when `defaultBom` is empty (no Print button).
  - Quantity (`batch_qty` + `stock_uom`), produced date, Stock Entry ID.
  - Ingredients used, as a plain list of batch numbers and quantities.
  - A "not produced with this app" state for other batches (no Print button).
- **Scanner:** `OnScan` on this route. A scan replaces the Details with `popUpTo(Details) { inclusive = true }`, so Back always goes to Search.

### Step 6.5: Label printer
- **`LabelData`:** `toLabel()` for `ManufactureEntry` (Summary) and `BatchDetails` (Details).
- **QR content:** `batchUrl(...)`, built from the Settings base URL (2.11 part 5).
- **Label bitmap:** render it at 384 px, with the QR from the bundled zxing `QRCodeWriter` and `dd.MM.yyyy` dates.
- **`QsPdaLabelPrinter`:** check `mComFd` and `isCanprint`, then call `printBitmap(1, bmp, true)` on the main thread (05, section 4).
- **Print buttons** on Details and Summary: disabled while printing, a toast on failure, re-printable.

**Done when:**
- On the device, scanning a label on Search opens its Details, with the expiry banner and ingredients.
- Scanning another label on Details shows that batch, and Back goes to Search.
- Search opens on the latest batches. Typing "saus" lists the sausage batches. Sorting by expiry or product name reorders the list. Limiting to 7 days and hiding expired batches narrow it, and the ⚙ dot shows they're active.
- Raw-material batches (e.g. BEEF.0001) don't appear in the list.
- A label printed from Details scans back to the same page.

---

## Phase 7: Polish and release (as time allows)

- **Glove use:** bigger touch targets and text, visible edit/delete actions instead of swipe-only (03, findings 10 and 12).
- **Previews:** for the new components, including whole-number and error states (03, finding 16).
- **Release build:**
  - Turn on R8 and check the SDK still works with the keep rules (05, section 7).
  - Set up signing (01, finding 17).
- **Leftovers:** the remaining tidy-ups (typos, unused imports, package moves, consistent error types) from 01, 02 and 03.

---

## How the phases depend on each other

```
0 Build ─▶ 1 Data/network ─▶ 2 Navigation/state ─▶ 3 Start + Form ─▶ 4 Batch dialog ─▶ 5 Submit, SDK, scanner ─▶ 6 Search, Details, printing ─▶ 7 Polish
```

- **Phases 0–4** need nothing from the device; the fakes cover everything.
- **The device test in step 5.3** is the earliest point where the hardware matters. You can do it earlier on its own if you like: it's a ten-minute throwaway receiver, and it settles the last open question.
- **Phase 6** can move before Phase 5 if you'd rather have Search and Details working first. It needs Phase 1, plus `OnScan` and `parseScan`, which you'd then build in Phase 6 instead.
