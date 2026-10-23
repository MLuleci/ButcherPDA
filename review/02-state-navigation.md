# Review 02: State, navigation, coroutines and data flow

Versions checked (from `gradle/libs.versions.toml`): lifecycle 2.11.0, navigation-compose 2.9.8, activity-compose 1.13.0, Compose BOM 2026.09.00, minSdk 23.
I could not run the app, so items that depend on library internals are marked where I am reasoning from the library's documented behaviour rather than an observed crash.

Severity tags: **[Bug]**, **[Risk]**, **[Improvement]**, **[Tidy-up]**. "Unfinished" means the WIP commit knowingly left it open.

---

## 1. [Bug] Form screen crashes (or breaks) if the ViewModel is first created on the Form route
[MainApp.kt:153-154](../app/src/main/java/bg/butcher/pda/ui/MainApp.kt#L153)

The Form route calls `viewModel(parentEntry)` with no factory. `FormViewModel` needs `(ErpRepository, SavedStateHandle)`, which the default factory cannot build, so it only works if the Start screen already created it. After process death while on Form, Android restores the whole back stack and composes only the top entry (Form). Form then asks for the ViewModel first and crashes with "Cannot create an instance of FormViewModel". The same happens if the order of screens ever changes.

Also `navController.getBackStackEntry(MainScreen.Start.name)` throws if Start is not on the stack.

Best fix: put the flow in a nested graph and create the ViewModel once per graph, always with the factory.

```kotlin
// before
val viewModel: FormViewModel = viewModel(parentEntry)

// after: nested graph, one helper
@Composable
private fun NavBackStackEntry.formViewModel(navController: NavController): FormViewModel {
    val parent = remember(this) { navController.getBackStackEntry(MainScreen.ManufactureGraph.name) }
    return viewModel(viewModelStoreOwner = parent, factory = FormViewModel.Factory)
}

navigation(startDestination = MainScreen.Start.name, route = MainScreen.ManufactureGraph.name) {
    composable(MainScreen.Start.name) { val vm = it.formViewModel(navController); ... }
    composable(MainScreen.Form.name)  { val vm = it.formViewModel(navController); ... }
    composable(MainScreen.Summary.name) { ... }
}
```

Bonus of the nested graph: the ViewModel lives until the whole flow is popped, which is the natural place to reset the form (see item 11).

---

## 2. [Bug] `SavedStateHandle` holds `FormUiState`, which is not saveable
[FormViewModel.kt:69](../app/src/main/java/bg/butcher/pda/ui/screens/FormViewModel.kt#L69), state definition [FormViewModel.kt:42-56](../app/src/main/java/bg/butcher/pda/ui/screens/FormViewModel.kt#L42)

`getMutableStateFlow("uiState", FormUiState())` stores a plain Kotlin data class. `SavedStateHandle` only writes Bundle-compatible types (primitives, String, Parcelable, Serializable, ...). The `BomSearchResultItem`, `BomDetails`, `NetworkState` and `FormUiState` classes are `@Serializable` (kotlinx) at most, which is not `java.io.Serializable`, and none are `Parcelable`.

What I expect (based on how the library works; please confirm on a device): the initial `getMutableStateFlow` call does not validate the value, so the screen opens fine. The failure comes when Android saves instance state, which happens when the app goes to the background (home button, PDA scanner app, screen off) and on rotation. The save throws `IllegalArgumentException` (unsupported type) and the app dies. On a PDA that is switched away from often, that would look like random crashes.

Second problem, even if it were saveable: `Loading` is saved too. After process death the screen would restore `fetchState = Loading` or `searchState = Loading` with no job running, so the user sees an endless spinner (`FormScreen` shows a spinner for `Loading` and nothing at all for `Idle`).

What to save: only what the user typed or chose, not server results or in-flight states. Server data can be re-fetched.

```kotlin
// before
private val _uiState = state.getMutableStateFlow("uiState", FormUiState())

// after: keep UI state in a normal MutableStateFlow, save only inputs
private val _uiState = MutableStateFlow(
    FormUiState(
        quantityText = state["quantity"] ?: "",
        explode = state["explode"] ?: false,
        expiryDaysText = state["expiry"] ?: "",
        isFinished = state["finished"] ?: true,
        // selected BOM: save its ids (strings), not the object
    )
)
// and in each setter: state["quantity"] = quantity
```

*Update: the developer says surviving process death is nice to have, but not worth a local database. The approach below needs none: `SavedStateHandle` survives Android killing the app in the background, but not a reboot or battery swap, and that's acceptable. See the summary, 2.6.*

The batches map (`Map<String, Map<String, Float>>`) is the most valuable thing to survive process death (the worker has typed or scanned a lot). Save it as a `Bundle`/JSON string, e.g. `Json.encodeToString(items)` into `state["items"]`, and decode on start. If the user was on Form after process death and `fetchState` is `Idle`, the Form route should call "fetch again" (or navigate back to Start) instead of showing an empty screen (see item 12).

---

## 3. [Bug] Changing inputs while a fetch is running lets the old result win
[FormViewModel.kt:147-161](../app/src/main/java/bg/butcher/pda/ui/screens/FormViewModel.kt#L147) and [FormViewModel.kt:93-123](../app/src/main/java/bg/butcher/pda/ui/screens/FormViewModel.kt#L93)

`setQuantity`, `setExplode` and `setSelectedBom` reset `fetchState` to `Idle` but do not cancel `bomFetchJob`. The Continue button is disabled while loading, but the quantity field and checkbox are not. So: tap Continue, change quantity from 10 to 100, the request for 10 finishes, writes `Success` and navigates to Form showing the quantity of 10 kg. The worker may not notice.

```kotlin
fun setQuantity(quantity: String) {
    bomFetchJob.cancel()
    _uiState.update { it.copy(quantityText = quantity, fetchState = NetworkState.Idle) }
}
```
(same in `setExplode` and `setSelectedBom`). Alternative: disable those inputs while `fetchState is Loading`.

---

## 4. [Bug] `verticalScroll` on the NavHost collapses `weight`/`fillMaxSize` and can crash lazy lists
[MainApp.kt:101-107](../app/src/main/java/bg/butcher/pda/ui/MainApp.kt#L101)

`verticalScroll` gives its content an infinite maximum height. Consequences for the screens inside:
- `fillMaxSize()` has no effect with an infinite height (it just wraps). The Home buttons will not be vertically centred, and `Centered { CircularProgressIndicator() }` / `ErrorMessage` will sit at the top instead of the middle.
- In `FormScreen` the `Column` gets infinite height, so `Modifier.weight(1f)` around `IngredientTable` gets no space (weights share "remaining space" and with infinite height the remaining space is treated as 0). The ingredient table would be squashed to nothing. [FormScreen.kt:60](../app/src/main/java/bg/butcher/pda/ui/screens/FormScreen.kt#L60)
- A `LazyColumn` placed directly in a scroll container with unbounded height throws "Vertically scrollable component was measured with an infinite maximum height constraints". The Start screen escapes it only because its list sits inside `heightIn(.., 500.dp)`. IngredientTable's `LazyColumn` only escapes because the weight above gives it a 0 height, which is the previous point.
- One shared `rememberScrollState()` also means the scroll position carries over from one screen to the next.

Fix: remove `verticalScroll` from the NavHost. Give each screen its own scrolling where it needs it (for example settings), and let the Form's table be the one scrolling list.

```kotlin
// before
modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(innerPadding)
// after
modifier = Modifier.fillMaxSize().padding(innerPadding)
```
On the order of modifiers: `.padding(innerPadding)` after `verticalScroll` puts the padding inside the scrolled content, so it is not the scroll viewport that is inset. Once the scroll is gone, `fillMaxSize().padding(innerPadding)` is the correct order.

---

## 5. [Risk] Submit is enabled with the Start page's rule (`canContinue`)
[FormScreen.kt:86](../app/src/main/java/bg/butcher/pda/ui/screens/FormScreen.kt#L86), [FormViewModel.kt:61](../app/src/main/java/bg/butcher/pda/ui/screens/FormViewModel.kt#L61)

`canContinue` is "quantity valid and a BOM selected", which is always true by the time Form is open. So Submit is always enabled, even with zero batches. Needs its own rule such as `canSubmit`, computed in the state class:

```kotlin
val canSubmit: Boolean
    get() = fetchState is NetworkState.Success
        && submitState !is NetworkState.Loading
        && expiryDays != null   // if expiry is required
        && bomItems.all { ing -> items[ing.itemCode].orEmpty().isNotEmpty() }
```
See open questions for the exact rule. If you compare quantities, avoid `Float` sums (0.1 + 0.2 != 0.3); use a small tolerance or `BigDecimal`.

*Updated: the rule is now decided. Every ingredient's total must be within ±tolerance % of the BOM amount (±0.5 for whole-number units), so the `isNotEmpty()` line above is superseded. Expiry is always required and is checked on Start, not on Form. Use `Double` and an `EPSILON`. See the summary, 2.2, 2.10 and 2.13.*

---

## 6. [Risk] Home navigates to `Search`, which has no destination (crash)
[HomeScreen.kt:57](../app/src/main/java/bg/butcher/pda/ui/screens/HomeScreen.kt#L57), [MainApp.kt:109-163](../app/src/main/java/bg/butcher/pda/ui/MainApp.kt#L109)

The "Find Batch" button navigates to `"Search"` but no `composable("Search")` exists. Navigation throws `IllegalArgumentException: Navigation destination ... cannot be found`. Unfinished feature, but until it is built either hide the button or register a placeholder composable so a tap cannot crash the app on the shop floor. Same for `Summary` and `Details`, which exist in the enum but have no route.

---

## 7. [Risk] `Result`/error mapping and the "submit" path are not finished (unfinished, with notes on what is needed)
[MainApp.kt:160](../app/src/main/java/bg/butcher/pda/ui/MainApp.kt#L160), [FormViewModel.kt:56](../app/src/main/java/bg/butcher/pda/ui/screens/FormViewModel.kt#L56)

`onSubmit = {}` and `submitState` is never written. To finish it well:
1. A `submit()` in the ViewModel: read the snapshot, return early if `canSubmit` is false or `submitState` is already `Loading` (stops double submits), set `Loading` synchronously, call `createManufactureEntry`, then `Success` + effect `GotoSummary`, or `Error` + toast.
2. Convert `expiryDays` to a date: `Clock.System.todayIn(TimeZone.currentSystemDefault()).plus(days, DateTimeUnit.DAY)` (kotlinx-datetime is already a dependency). Pass `null` if expiry is optional. *Updated: expiry is always required, so it is never `null`; keep the local-time `YYYY-MM-DD` date (summary, 2.10 and 2.12 point 5).*
3. Duplicate-record risk: if the request reaches the server but the reply is lost (weak Wi-Fi in a plant), the worker will press Submit again and create a second entry. Consider a client-generated request id that the server de-duplicates, or at least do not clear the form on failure and warn on "unknown" errors. *Updated: decided and done on the server (04, A4). The app sends a `request_id`, keeps it after a failed submit, and renews it for "Same BOM again" (summary, 2.12 point 4).*
4. Navigate to Summary with `popUpTo(Home)` (see item 11). *Updated: replace Form with Summary using `popUpTo(Form) { inclusive = true }`, so Start stays under it for "Same BOM again" (summary, 2.8).*
5. Outside my scope but worth a look: `ErpService.createManufactureEntry` sends `items: Map<String, Map<String, Float>>` and `LocalDate?` as `@Field`. With the kotlinx converter, `@Field` values are turned into strings with `toString()`, so the server would receive `{ITEM={B1=2.0}}`, not JSON. Likely needs `Json.encodeToString(items)` as a `String` field. *Updated: instead, the server now takes a JSON body and the app sends a typed `@Body` request (summary, 2.4; 04, A1).*

---

## 8. [Risk] Back-navigation and form reset after submit
[MainApp.kt:129](../app/src/main/java/bg/butcher/pda/ui/MainApp.kt#L129)

*Updated: the developer wants a "Same BOM again" shortcut on Summary. It returns to Start with every setting kept and only the batches cleared. See the summary, 2.8, for the nested graph, navigation and `startAgain()`.*

Start → Form → Summary stacks screens. Back from Summary returns to the filled Form with the old state, where Submit can be pressed again and make a duplicate. Navigate to Summary with `popUpTo(MainScreen.Home.name)` (or pop the whole nested graph) so the ViewModel is cleared and the next production run starts from an empty form. *Updated: not exactly. Replace Form with Summary (`popUpTo(Form) { inclusive = true }`); "Same BOM again" pops back to Start and keeps the ViewModel; "Done" and system Back pop the whole graph (summary, 2.8).* Similarly, back from Form to Start keeps the batches the user chose; see item 10.

Also use `launchSingleTop = true` on navigations (Home buttons, GotoNextPage) so a double tap on a slow PDA touch screen cannot push two copies of a screen:

```kotlin
navController.navigate(MainScreen.Form.name) { launchSingleTop = true }
```

---

## 9. [Risk] `require(...)` in `upsertBatch` / `removeBatch` crashes on bad input
[FormViewModel.kt:167-171](../app/src/main/java/bg/butcher/pda/ui/screens/FormViewModel.kt#L167), [FormViewModel.kt:180-181](../app/src/main/java/bg/butcher/pda/ui/screens/FormViewModel.kt#L181)

`require` throws `IllegalArgumentException`, which in a click handler kills the app. These values come from user input (batch quantity text) so they must be treated as normal bad input. The dialog already validates, so the ViewModel should simply ignore (or return `false`) rather than throw.

```kotlin
// before
require(quantity > 0f) { "..." }
// after
fun upsertBatch(itemCode: String, batchNumber: String, quantity: Float): Boolean {
    if (itemCode.isEmpty() || batchNumber.isEmpty() || !quantity.isFinite() || quantity <= 0f) return false
    _uiState.update { ... }
    return true
}
```
Also: `quantity` is a `Float`; NaN passes `> 0f` is false so it is caught, but `Infinity` would pass. Add `isFinite()`.

*Updated: quantities become `Double` (summary, 2.2). `upsertBatch` should also return an error for a batch whose item is not in the recipe, and refuse a duplicate batch number in add mode (summary, 2.3). Whole-number units need a whole quantity (2.13).*

---

## 10. [Bug] Chosen batches are not cleared when the BOM changes
[FormViewModel.kt:147-161](../app/src/main/java/bg/butcher/pda/ui/screens/FormViewModel.kt#L147), comment at [line 51](../app/src/main/java/bg/butcher/pda/ui/screens/FormViewModel.kt#L51)

*Updated: the developer confirmed batches should be cleared when the BOM, quantity or explode changes. They also thought this couldn't happen yet, because those are set on the first page before batches are entered.*

It can happen, though. The `FormViewModel` is scoped to the Start back-stack entry, so pressing Back from Form returns to Start with the same ViewModel and the same `items`. Picking BOM B after adding batches for BOM A leaves A's batches in `items`. The table iterates B's ingredients, so they're invisible, but they'd be submitted. The comment says `fetchState` is cleared on change; `items` should be too, but only when the value actually changes, so that re-selecting the same BOM doesn't wipe a list of scans:

```kotlin
fun setSelectedBom(bom: BomSearchResultItem) {
    _uiState.update {
        if (bom.name == it.selectedItem?.name) it
        else it.copy(selectedItem = bom, fetchState = NetworkState.Idle, items = emptyMap())
    }
}
// same pattern for setQuantity / setExplode
```

As a second guard, filter `items` to the loaded BOM's ingredient codes when building the submit request.

*Updated: choosing a different BOM also replaces the expiry days with that product's shelf-life default (`expiryTyped = false`), and the setters keep `items` untouched only for expiry and "is finished" (summary, 2.10).*

---

## 11. [Improvement] Move `GotoNextPage` out of the effects channel (or at least make it safer)
[FormViewModel.kt:73-74](../app/src/main/java/bg/butcher/pda/ui/screens/FormViewModel.kt#L73), [MainApp.kt:125-140](../app/src/main/java/bg/butcher/pda/ui/MainApp.kt#L125)

How it behaves today:
- `Channel.BUFFERED` + `receiveAsFlow()` is single-consumer and keeps events until someone collects, so events are not lost; they are delayed until the Start route is composed again. E.g. start a fetch, background the app, the fetch completes, the "go to Form" event is delivered when the screen comes back. That is acceptable.
- Collection is in `LaunchedEffect`, which keeps collecting while the app is in the background, so a Toast can be shown while the app is not visible. Prefer `repeatOnLifecycle(STARTED)` (lifecycle-runtime-compose has `LifecycleStartEffect`/`collectAsStateWithLifecycle`).
- Effects are only handled on the Start route. The Form route has no collector, so any effect sent while Form is open (the future submit success/error toast, or `GotoSummary`) would sit in the buffer and then pop up later on Start. When you build submit, add the same collector to Form (a shared helper composable `CollectFormEffects(viewModel, navController)`). *Updated: the scan-ignored toast is shown directly in the Form route, not through `effects` (summary, 2.1). The submit effects still need a collector on Form.*
- Navigation as a one-off event is easy to get wrong. A simpler design: keep `fetchState` in state, and let the route navigate when it sees `Success` that has not yet been "consumed". Either approach is fine; just keep one.

Double navigation (lead 5): `SubmitButton` is disabled while `fetchState is Loading` ([SubmitButton.kt:21](../app/src/main/java/bg/butcher/pda/ui/components/SubmitButton.kt#L21)), so a double tap is mostly prevented. But `Loading` is set one dispatch later because of the nested launch (item 13), and after `Success` the button is enabled again while the Start screen is still visible until navigation happens. Two quick taps could produce two requests (the second cancels the first, so likely one navigation) but `launchSingleTop` (item 8) removes the doubt.

---

## 12. [Risk] Form can show an empty screen
[FormScreen.kt:44-91](../app/src/main/java/bg/butcher/pda/ui/screens/FormScreen.kt#L44)

`when (state.fetchState)` has `else -> {}`. If Form is ever shown with `Idle` (after process death with the fix from item 2, or if the user changes an input on Start then returns via back and forward), the screen is blank with no explanation. Show a "Go back / reload" message or auto-navigate back to Start when `fetchState` is `Idle`. Likewise the `Error` branch shows `ErrorMessage()` without a retry callback; pass `onRetry` that re-runs `fetchBomDetails`.

---

## 13. [Improvement] `fetchBomDetails` has a needless nested `launch` and reads a stale snapshot
[FormViewModel.kt:93-123](../app/src/main/java/bg/butcher/pda/ui/screens/FormViewModel.kt#L93)

`viewModelScope.launch { ... bomFetchJob.replace { ... } }`: the outer launch only exists to call `_effects.send` (a suspend function) and then starts a second coroutine. That means `Loading` is set a moment after the click, which weakens the double-tap protection. The snapshot is read once and then `state.selectedItem.name` / `state.explode` are used inside the job; this is fine functionally (the values were copied at click time and are what the request should use), but `selectedItem` and `quantity` locals already exist, use them for clarity. Use `trySend` (the channel is buffered) to avoid needing the outer launch.

```kotlin
fun fetchBomDetails() {
    val s = _uiState.value
    val quantity = s.quantity
    val bom = s.selectedItem
    if (quantity == null || bom == null) { _effects.trySend(FormUiEffect.ShowToast(R.string.incomplete_message)); return }
    _uiState.update { it.copy(fetchState = NetworkState.Loading) }   // synchronous
    bomFetchJob.replace {
        erpRepository.getBomDetails(bom.name, quantity, s.explode)
            .onSuccess { ... }
            .onFailure { ... }
    }
}
```

---

## 14. [Improvement] Batch lookup is a `suspend` function called from the dialog
[FormViewModel.kt:126-145](../app/src/main/java/bg/butcher/pda/ui/screens/FormViewModel.kt#L126), [BatchDialog.kt:51-86](../app/src/main/java/bg/butcher/pda/ui/components/BatchDialog.kt#L51)

Trade-off: the current design keeps lookup state local to the dialog and the request is cancelled automatically when the text changes or the dialog closes (via `LaunchedEffect` restart). That is simple and good. The cost is that the ViewModel does not know about it, the lookup is not testable through state, and the status cannot survive rotation. Since the dialog is a transient thing, keeping it local is reasonable. If you move it into the ViewModel, hold a `batchLookup: NetworkState<BatchDetails, FetchBatchError>` in state and use a `JobSlot` with the debounce, like the BOM search.

Error mapping:
- 404 to `NOT_FOUND`, other HTTP codes to `REQUEST_FAILED`, everything else to `NETWORK_ERROR`. Reasonable. *Updated: the enum also needs `NOT_IN_RECIPE`, `ALREADY_ADDED` and `NOT_A_BATCH_LABEL` (summary, 2.3 and 2.11 part 5).* But `SerializationException` (server returned an unexpected shape) and the interceptor's `IOException("Invalid or missing API base URL")` also land in `NETWORK_ERROR`, which would tell the worker "network problem" when settings are wrong. Consider mapping `401/403` to an "check API key in Settings" error and `IOException` vs other exceptions separately.
- `FakeErpRepository` returns `Throwable("Unexpected error")` ([FakeErpRepository.kt:34](../app/src/main/java/bg/butcher/pda/data/FakeErpRepository.kt#L34)), so every simulated failure is `NETWORK_ERROR`, and you can never see `NOT_FOUND` in the fake. Have the fake produce `IOException` for network failures and, say, 20% `HttpException(404)` for lookups (or a fake that treats a specific batch number like "MISSING" as not found).

---

## 15. [Risk] `rememberSaveable` in `BatchDialog` holds unsaveable types
[BatchDialog.kt:51-53](../app/src/main/java/bg/butcher/pda/ui/components/BatchDialog.kt#L51), `BatchInfo` at [IngredientTable.kt:45](../app/src/main/java/bg/butcher/pda/ui/components/IngredientTable.kt#L45)

`BatchInfo` is a plain data class (not Parcelable), and `NetworkState`/`BatchDetails` are not Parcelable either. `rememberSaveable { mutableStateOf(x) }` uses the default saver, which refuses non-Bundle values and throws "MutableState containing ... cannot be saved using the current SaveableStateRegistry" when state is saved (rotation, backgrounding). Same crash path as item 2, hidden today because the dialog is not wired up (and `showDialog` is saved, so a restored `true` would open it). *Updated: the batch number is no longer kept inside the dialog. The Form route owns the dialog state (`Adding` / `Editing`), because a scan must be able to open the dialog and replace the number; the quantity stays inside the dialog and survives a rescan (summary, 2.1).*

Also `status` saved as `Loading` would restore as a stuck spinner, because the `LaunchedEffect` restarts from the text and sets `Idle` first, so that part is self-healing; the problem is only the crash.

Fix options:
```kotlin
// 1. Save only strings, rebuild the rest
var batchNumberText by rememberSaveable { mutableStateOf(initialValue?.batchNumber ?: "") }
var quantityText by rememberSaveable { mutableStateOf(initialValue?.quantity ?: "") }
var status by remember { mutableStateOf<NetworkState<BatchDetails, FetchBatchError>>(NetworkState.Idle) }

// 2. Or make BatchInfo saveable
@Parcelize data class BatchInfo(...) : Parcelable      // needs kotlin-parcelize plugin
// or use a custom Saver: listSaver(save = { listOf(it.batchNumber, ...) }, restore = { BatchInfo(...) })
```
Note `status` should be `remember`, not saved: a lookup result is cheap to redo and should not be restored. And `rememberSaveable(initialValue)` uses the object as a key, which with the default `BatchInfo` equality is fine.

To finish the dialog flow (unfinished): Form needs `var editing: BatchInfo?` state, the Add button opens `BatchDialog(fetchBatchDetails = viewModel::fetchBatchDetails, onSubmit = { viewModel.upsertBatch(it.itemCode, it.batchNumber, it.quantity.toFloat()) ... })`, and the dialog must tell the user when the scanned batch's item is not an ingredient of this BOM (the `itemCode` from the lookup should be checked against `bom.items`).

---

## 16. [Risk] SettingsViewModel: load can overwrite edits, validators disagree with the network layer, save has no feedback
[SettingsViewModel.kt:35-82](../app/src/main/java/bg/butcher/pda/ui/screens/SettingsViewModel.kt#L35)

- Race: `init` launches a load that replaces both fields when it finishes. If the user starts typing before DataStore replies (slow first read on an old PDA), their text is overwritten. Guard with `if (!it.isDirty)`.
- Flash of "invalid" on open: initial state is `value = ""`, `isValid = false`, so both fields show the red "invalid" message until the load finishes. Add an `isLoaded` flag and hide errors until then (and optionally disable the fields).
- Validation mismatch: the screen uses `URLUtil.isValidUrl` (accepts `ftp://`, `file://`, `content://`...), but the actual request path uses `toHttpUrlOrNull()` in [ErpServiceInterceptor.kt:15](../app/src/main/java/bg/butcher/pda/network/ErpServiceInterceptor.kt#L15), which throws "Invalid or missing API base URL" if it fails. Validate with the same parser and restrict to http/https: *(Updated: https only, with `https://` added if missing, and `usesCleartextTraffic="false"`; summary, 2.5.)*
  ```kotlin
  fun isValidBaseUrl(s: String) = s.trim().toHttpUrlOrNull()?.scheme in setOf("http", "https")
  ```
  Also `trim()` the URL and the API key before saving (a pasted trailing space/newline is common and silently breaks auth).
- `save()` sets `isDirty = false` before the write finishes and does not catch failures. A DataStore `IOException` in `viewModelScope.launch` would crash the app. Wrap in try/catch, set the flag after success, and show "Saved" feedback (toast or snackbar). It also reads `_uiState.value` inside the launch, not from the validated snapshot; capture `val s = _uiState.value` first.
- `PdaSettings(baseUrl = "http://localhost/")` default is never used because `SettingsRepository` maps missing values to `""`; either remove the default or use it.
- The API key is stored in plain text in DataStore. Fine for a closed PDA fleet, but note it if devices are shared.
- Related (unfinished): [AppContainer.kt:58](../app/src/main/java/bg/butcher/pda/data/AppContainer.kt#L58) is hard-wired to `FakeErpRepository(1.seconds)`, so Settings currently has no effect.

---

## 17. [Improvement] Type-safe navigation instead of string routes + `MainScreen.valueOf`
[MainApp.kt:88-90](../app/src/main/java/bg/butcher/pda/ui/MainApp.kt#L88)

`MainScreen.valueOf(route)` throws if a route ever has arguments (`"Details/{id}"`) or a nested graph route is current (`"ManufactureGraph"`), because the string won't match an enum name. It also assumes `route` is never null/unknown. Navigation 2.9.8 supports `@Serializable` route objects (since 2.8) and works on minSdk 23 as far as I can tell (your toml comment says 2.10+ moved to 24; stay on 2.9.x).

```kotlin
@Serializable object Home
@Serializable object Settings
@Serializable object Start
@Serializable object Form
@Serializable data class Details(val batchNumber: String)

composable<Details> { entry -> val args = entry.toRoute<Details>() ... }
val title = when {
    dest?.hasRoute<Form>() == true -> R.string.assembly_title
    ...
}
```
If you keep strings, at least make it safe: `MainScreen.entries.firstOrNull { it.name == route?.substringBefore('/') } ?: MainScreen.Home`.

---

## 18. [Tidy-up] Naming, packages, and error types
- Log tag `"AssemblyViewModel"` is stale ([FormViewModel.kt:86](../app/src/main/java/bg/butcher/pda/ui/screens/FormViewModel.kt#L86), [line 118](../app/src/main/java/bg/butcher/pda/ui/screens/FormViewModel.kt#L118)). Use a `private const val TAG = "FormViewModel"`.
- `JobSlot` and `NetworkState` are UI/state helpers, not networking. They sit in `bg.butcher.pda.network` next to Retrofit code (`ErpService`, interceptor). Consider `bg.butcher.pda.util` or `bg.butcher.pda.ui.state`. `JobSlot.isActive`, `launchIfIdle` and `join` look unused; remove them or keep them for the planned screens.
- Error types are inconsistent: `NetworkState<..., String?>` for search/fetch/submit (raw `e.message`, which the UI never displays and which is English/technical) vs the `FetchBatchError` enum for batch lookup. Use one small shared type, e.g. `enum class ErpError { NETWORK, AUTH, NOT_FOUND, SERVER, INVALID_INPUT, UNKNOWN }` with a mapping function `Throwable.toErpError()` (move the 404 logic from item 14 there) and map it to string resources in the UI. Then `NetworkState<T>` can carry that single error type and be saved/compared easily.
- `MainApplication.container` is `lateinit var` and `public`; make it `lateinit var container: AppContainer private set` or a `val` assigned in `onCreate` via `by lazy`. Minor.
- `FormUiState` mixes the Start page, Form page and fetch results. Splitting inputs (saveable) from results (not saveable) would also make item 2 easy.
- `Loading` state uses `NetworkState.Loading` for search results and an earlier `Error` is never cleared on query change except via `Idle` for blank query; fine, just note that stale `Success` results stay visible while the new query is loading? They do not: `Loading` replaces them, so there is a spinner flash per keystroke after the debounce. Acceptable.

---

## Things I checked and found fine
- `JobSlot.replace` cancels the old job and applies the debounce; `apiCall` rethrows `CancellationException`, so cancelled searches do not become errors.
- `searchForBom` with a blank query sets `Idle` and cancels; no stale result can overwrite it on the main dispatcher.
- Lifecycle: `viewModelScope` is cancelled when the back stack entry is popped, so leaving to Home drops in-flight requests (good).
- `FormUiState.quantity` correctly rejects NaN/Infinity/zero/negative.

---

## Open questions for the developer
1. ~~Submit rule?~~ *Answered: each ingredient's total must be within ±tolerance % of the BOM amount. The tolerance will later come from `BomDetails`; for now it's hard-coded at 5% (see the summary, 2.2).*
2. ~~Is "expiry days" required?~~ *Answered: always required, defaulting to the product's shelf life.* Original question: is "expiry days" required? Should a finished product require it and an unfinished (`isFinished = false`) one not? *Note: the developer decided that expiry and "is finished" move to the Start page, and Form holds batches only (see the summary, 2.10).*
3. ~~Clear batches on BOM/quantity/explode change?~~ *Answered: yes, clear (see item 10).*
4. ~~After submit?~~ *Answered: Summary, with a "Same BOM again" shortcut back to Start that keeps all settings and clears only the batches (see the summary, 2.8).*
5. ~~Should work in progress survive the app being killed?~~ *Answered: only if it doesn't need a database. SavedStateHandle with JSON-encoded batches (item 2) is enough. The summary (2.6) keeps this: inputs only, no database.*
6. ~~What does the server do on a duplicate submit (same BOM, batches, time)? Is there an idempotency key or a unique constraint?~~ *Answered: now there is. The server de-duplicates on `request_id` (04, A4).*
7. ~~Does the server accept the `items` field as `Map.toString()`, or JSON? (Retrofit's `@Field` with a `Map`.) And what does Frappe return for a missing batch (404, or 200 with an empty body, or 417)?~~ *Answered: JSON (the server now takes a JSON body, 04, A1), and a missing batch gives a clear "Batch not found" error (04, A5).*
8. Should unfinished screens (Search, Details, Summary) be hidden from Home until ready, or show a "coming soon" placeholder? *Partly answered: Summary and Details are planned (summary, 2.8 and 2.11); hide "Find Batch" until Search exists (summary, section 1, item 2).*
9. Is a PDA ever used in landscape or with a hardware keyboard/scanner that triggers configuration changes? (affects how much rotation/state-restore matters.)
10. Should Settings changes apply to a request already in progress, and should there be a "Test connection" button before saving?
