# Architecture

A single-module Compose app. Package `bg.butcher.pda`, source in `app/src/main/java/bg/butcher/pda/`.

## Layout

| Package | Contents |
|---|---|
| (root) | `MainApplication` (SDK init, container, label-mode collector), `MainActivity` (edge-to-edge, `AppTheme { Surface { MainApp() } }`) |
| `data/` | `AppContainer` / `DefaultAppContainer` (manual DI), `ErpRepository` + `NetworkErpRepository` + `FakeErpRepository`, `SettingsRepository` (DataStore, validators), `UpdateRepository`, `UpdateInstaller` |
| `network/` | `ErpService` (Retrofit), `ErpServiceInterceptor`, `ErpError`, `erpJson` |
| `model/` | `@Serializable` data: BOM, batch, stock entry, request/response wrappers, `BatchFilters`, `ExpiryStatus`, `GithubRelease` |
| `hardware/` | Scanner (`BarcodeScanner`, `ScanParser`, `ScanFeedback`), printer (`LabelPrinter`, `LabelRenderer`, `QsPdaLabelPrinter`), `QsPdaHardware` (SDK init) |
| `ui/` | `MainApp` (scaffold, NavHost, routes' wiring), `Routes` (type-safe routes, titles) |
| `ui/screens/` | Screens, their ViewModels, and `FormRules.kt` (pure rules) |
| `ui/components/` | Shared composables (batch dialog, ingredient table, BOM picker, inputs, buttons, `OnScan`) |
| `ui/state/` | `NetworkState<T, E>` (Idle/Loading/Success/Error) and `JobSlot` (one cancellable job) |
| `ui/theme/` | Theme (don't edit, see [README](README.md)), `StatusColors`, `Previews` |
| `util/` | `Decimal.kt` (all number parsing and formatting), `Dates.kt` |

Tests are JVM unit tests in `app/src/test/`. They use `unitTests.isReturnDefaultValues = true` so `android.util.Log` doesn't throw, MockWebServer (`mockwebserver3`) for network code, and `UnconfinedTestDispatcher` for effects. There are no real instrumented tests; `androidTest` is still the template.

## Dependencies and wiring

- **The container:** `MainApplication.container` is a `DefaultAppContainer`, which only `MainApplication` can set. ViewModel factories read it through `APPLICATION_KEY`. Its members:
  - `erpRepository`: `FakeErpRepository(latency = 1.seconds)` when `BuildConfig.USE_FAKE_ERP`, otherwise `NetworkErpRepository`.
  - `settingsRepository`
  - `labelPrinter` and `barcodeScanner`: the real ones when `QsPdaHardware.available`, otherwise the fakes.
  - `scanFeedback`, `updateRepository` and `updateInstaller`.
- **HTTP:** the certificate store and OkHttp clients are `lazy`, so fake builds don't read PEM files at start-up.
  - **Trusted roots:** `EXTRA_ROOTS` adds `res/raw/isrg_root_x1/x2/yr/ye.pem` and `usertrust_ecc.pem` on top of the platform store. Android 6 lacks the Let's Encrypt roots, and `api.github.com` chains to USERTrust ECC.
  - **Two clients:** the ERP client has `ErpServiceInterceptor`. The self-update client deliberately doesn't, because the interceptor would send its requests to the ERP server.
- **Libraries:**
  - **Network and data:** Retrofit 3 with Retrofit's own `converter-kotlinx-serialization`, OkHttp 5, DataStore Preferences, kotlinx-datetime with core library desugaring (`java.time` is API 26+).
  - **UI:** Navigation Compose 2.9 (later versions need minSdk 24).
  - **The SDK JAR** bundles zxing core 3.5.1, so don't add zxing separately, or the build fails with duplicate classes.
  - **Upgrading:** check an artifact's minSdk before bumping it (see the comment at the top of `gradle/libs.versions.toml`).

## Navigation (`ui/MainApp.kt`, `ui/Routes.kt`)

The routes are `@Serializable` objects:

```
Home
Settings
ManufactureGraph { Start → Form → Summary }
BatchesGraph     { Search → Details(batch: String) }
```

- **Scrolling:** the NavHost doesn't scroll; each screen does its own.
- **Titles:** `NavDestination?.title()` matches with `hasRoute`, and falls back to `app_name`.
- **Home:** its buttons navigate with `launchSingleTop`. `Settings` is imported as `SettingsRoute` in `HomeScreen.kt`, because the name clashes with the icon.
- **The top-bar back arrow** goes through `onBackPressedDispatcher`, so routes' `BackHandler`s apply to it too.

### Manufacture graph

- **ViewModel:** one `FormViewModel` is shared by Start, Form and Summary. `NavBackStackEntry.formViewModel(navController)` scopes it to the `ManufactureGraph` back-stack entry, so it's cleared when the graph is popped.
- **Start:** BOM picker (`BomSelect`), quantity, expiry days, and the Explode and "Is finished" checkboxes. Continue sits at the bottom, outside the scroll.
  - **Picking a BOM** waits for the window to have focus back from the sheet, then focuses the quantity and opens the keyboard.
  - **Enter** moves from quantity to expiry, but doesn't press Continue.
- **Form:** a read-only run header, the `IngredientTable`, "Add batch", and Submit.
  - **The batch dialog's state** (`BatchDialogState`) lives in the Form route, `rememberSaveable` with `BatchDialogStateSaver`, so scans can reach it.
  - **On entry,** if `canFetch` it reloads the BOM details (after process death), otherwise it pops back to Start.
- **Summary** shows the submitted entry, with Print, "Same BOM again" and Done.
  - **"Same BOM again"** calls `startAgain()`, then pops to Start.
  - **Done and Back** pop the whole graph.
- **Effects** (`CollectFormEffects` on all three routes):
  - `GotoForm`
  - `GotoSummary`, which uses `popUpTo<Form> { inclusive = true }`, so Back can't reach the submitted form.
  - `ShowToast`

### Batches graph

- **Search** (`SearchViewModel`): typing lists batches by batch number or product name, with a 300 ms debounce. An empty field lists the latest batches.
  - **Enter** opens an exact batch number. **A scan** always opens its batch, whatever the filters.
  - **Filters** are in a `ModalBottomSheet` (`BatchFilterSheet`): it edits a draft and applies it when the sheet closes, however it's closed.
  - **The ⚙ button** shows a dot when the filters aren't the defaults.
  - **Paging:** "Load more", 30 rows a page.
- **Details** (`DetailsViewModel(batch, repo, printer)`; only its factory reads the route, so the ViewModel is testable without Android): it loads the batch, then its Stock Entry for "ingredients used". The ingredients have their own error state and Retry.
  - **Not made here:** a batch whose item has no default BOM (`isMadeHere` false) is bought in, e.g. a supplier's batch on a Material Receipt. It shows a neutral "bought in" note, and "not made with this app" instead of ingredients, with no Stock Entry request. It still has a Print button, so raw materials can be labelled and then scanned on Form.
  - **Print** is available for every batch.
  - **A scan on Details** replaces the page (`popUpTo<Details> { inclusive = true }`), so Back always goes to Search.
- **Filters reset on each visit from Home,** because the graph is popped when leaving.

### Settings

`SettingsScreen` is stateless: it takes `uiState` and callbacks, and its route holds the `SettingsViewModel`.
- **Fields:** base URL (HTTPS only), API key (one field, `key:secret`, hidden as typed), paper size, paper type, label height.
- **Layout:** Save sits at the bottom outside the scroll, and a toast reports whether saving worked.
- **App version:** a section shows the installed version and "Check for updates" ([release-and-updates.md](release-and-updates.md)).

## State and saving

- **`FormViewModel`** saves inputs, not UI state, in `SavedStateHandle`, under the keys in `private object Saved`.
  - **Plain values:** `quantity`, `explode`, `expiry`, `expiryTyped`, `finished` and `requestId`.
  - **As JSON:** `selectedItem` (the whole `BomSearchResultItem`, including shelf life and the whole-number flag), `items` (`Map<itemCode, Map<batchNo, qty>>`) and `entry` (the submitted `ManufactureEntry`, so Summary survives process death).
- **Not saved on purpose:** `fetchState` (refetched), `searchState`, `printState`, and an in-flight submit.
- **`SearchViewModel`** saves `query`, and `filters` as JSON, under `private object SavedSearch`. Two top-level `Saved` objects in one package clashed at build time.
- **Details** saves nothing; its route argument reloads it.
- **Batch dialog:** only `quantityText` and `quantityTyped` are `rememberSaveable`, not keyed on the batch number, so a typed quantity survives a rescan. The lookup result is `remember(input)`, so a match can't carry over to a different batch number.
- **Summary** keeps its entry with `remember`, so "Same BOM again" can clear state while the page animates out.
- **Back during a submit** is blocked: there's a `BackHandler`, the top-bar arrow is disabled, and scans are off. A late result could otherwise land on a changed run.

## Scanning in the UI

- **Where scanning works:** `OnScan(enabled, onNoRead, onScan)` (`ui/components/OnScan.kt`) turns the trigger on with `repeatOnLifecycle(STARTED)`. Only Form, Search and Details use it, so on other screens the trigger does nothing.
- **Form** enables it only once the BOM has loaded and no submit is in flight. There:
  - A scan opens the batch dialog, or replaces the batch number in it.
  - While editing a batch, a scan is ignored, with a toast.
  - Each scan has a `scanId`, so scanning the same label again looks it up again.
  - A code that isn't a batch label shows "not a batch label", with no lookup.
- **Feedback:** `ScanFeedback` plays `beep1` when a scan is accepted, and `beep3` for a no-read or an invalid scan. Network errors are silent.

Hardware details are in [hardware.md](hardware.md).

## Conventions

- **Numbers** go through `util/Decimal.kt` only.
  - `parseDecimal` accepts `.`, with `,` as a fallback, and returns finite values only.
  - `formatDecimal(value, maxDecimals = 3)` rounds HALF_UP and strips trailing zeros. It special-cases zero, because Android 6's `stripTrailingZeros()` leaves `0.000`.
  - `roundForErp()` rounds to 3 decimal places before sending.
  - `validateQuantity(text, wholeNumber)` checks quantity input.
  - Never use `String.format("%.Nf")`.
- **Dates** (`util/Dates.kt`):
  - `today()` uses the device time zone, and `dateFromToday(n)` adds calendar days.
  - `shortDate` is `dd.MM.yyyy`.
  - The expiry is sent as a local `LocalDate` (`YYYY-MM-DD`), never a UTC instant.
- **Strings:** all of them are in `res/values/strings.xml` and `res/values-bg/strings.xml`, kept with the same keys. Use format arguments, not concatenation.
  - **Bulgarian terms:** `bom_label` = "СНМ", written in Cyrillic letters, not Latin C-H-M. `explode_label` = "Разгръщане".
  - **Icons** next to a label that already says the same thing get `contentDescription = null`.
- **Language:** `Accept-Language` is `bg` when the device is in Bulgarian, `en` otherwise, so server messages match the UI.
- **Effects:** each ViewModel has `Channel<…UiEffect>(Channel.BUFFERED)` exposed as `effects`. They're collected in the route (`LaunchedEffect(viewModel)` in `MainApp.kt`), never in the screen.
- **Screens** take state and callbacks; routes own the ViewModels.
- **Previews:**
  - Annotate with `@ThemePreviews`, which shows light and dark, and wrap in `PreviewTheme`. Both are in `ui/theme/Previews.kt`.
  - Make every preview `private`.
  - `BatchDialog` and `BatchFilterSheet` have none, since they open in their own windows.
- **Dynamic colour:** `AppTheme` defaults to `dynamicColor = true`, so on an Android 12+ device the app's colours follow the wallpaper. The PDAs (Android 6) have no dynamic colour, so they always show the app's palette. Previews pass `false` to match them.
- **Status colours:** `statusColors.warning` (amber) and `.success` (green) come from `ui/theme/StatusColors.kt` and stay the same with dynamic colour.
  - **In the ingredient table:** UNDER is warning, OK is success, MISSING is `onSurfaceVariant`, OVER is `error`.
  - **In the Details banner,** "expires soon" uses the warning container.
- **Touch targets** are sized for gloves: main buttons (`SubmitButton`) are at least 56 dp tall, row actions are 48 dp, and Home's buttons are 80 dp.
- **Settings validation:** `normaliseBaseUrl` (HTTPS only; it adds `https://` to a bare host) and `normaliseApiKey` (exactly one `:` with text on both sides) are in `data/SettingsRepository.kt`. The screen and the interceptor both use them.
- **Backups:** the settings DataStore (`datastore/`) is excluded from them in `backup_rules.xml` and `data_extraction_rules.xml`, because it holds the API key.
