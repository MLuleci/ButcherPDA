# ButcherPDA code review: summary

Three reviewers each went through one area of the code. Their full reports are:

- [01-data-network-build.md](01-data-network-build.md): Retrofit/OkHttp, models, repositories, settings storage, Gradle, tests
- [02-state-navigation.md](02-state-navigation.md): ViewModels, saved state, coroutines, navigation
- [03-ui-compose.md](03-ui-compose.md): Compose screens and components (with a separate BatchDialog section), strings and theme
- [04-server-changes.md](04-server-changes.md): **every change to the ButcherERP server** (`my_app/api.py` and ERPNext setup), in order, to do before the app work
- [05-scanner-sdk.md](05-scanner-sdk.md): static analysis of the vendor scanner/printer SDK (`qspdasdk.jar`, `libzyapi_common.so`)
- [06-implementation-order.md](06-implementation-order.md): **suggested order for the app work**, in phases, with a "done when" check for each

No source files were changed. Nothing was run on a device or against the real server. The server-side claims were checked against the public `my_app/api.py`.

This file lists the most important items and turns your answers to my questions into concrete suggestions.

---

## Decisions so far

Answers you've given, so the suggestions below can rely on them:

| Topic | Decision |
|---|---|
| Batch entry | Two ways in: type the batch number and quantity in the dialog by hand, or scan. A scan opens the dialog pre-filled; later scans replace the batch number. |
| Ingredient | Not chosen by the user. It is inferred from the batch's item after the lookup. |
| Wrong item | Blocked when the batch's item isn't in the recipe. |
| Submit rule | Every ingredient's chosen total must be within ±tolerance % of the BOM amount; under and over are both allowed within it. The tolerance comes from the server's `BomDetails` (`tolerance_percent`), with 5% as the app's default. Whole-number units use ±0.5 instead (2.13). |
| Whole-number units | A run is too small if a whole-number ingredient needs less than 0.5; the app stops on Start (2.13). Quantities in units like Nos must be whole numbers: the Start quantity (BOM unit), batch quantities (ingredient unit) and the ingredient total. The app checks this before sending. For Start the flag comes from the BOM search (`uom.must_be_whole_number`, returned as 0/1); for ingredients, from `get_bom_details` (2.13). |
| Same batch twice | Rejected, with an error under the batch number field so the user can change it. |
| Scanner | Vendor SDK `qspdasdk.jar`. Per the JAR, the result arrives on `com.qs.scancode` (extra `"data"`), and `ismart.intent.scandown` is the trigger; confirm on the device. Set up once in `Application.onCreate`, with a 2500 ms delay. Only heeded on the Form, Search and Details routes. Hardware trigger only, with no on-screen button. Vendor beep on an accepted batch. |
| Label printing | A "Print label" button on the Details and Summary routes. The label has the batch number as QR code and as text, item name, quantity, production and expiry dates, and stock entry ID. The printer itself comes later. |
| Label quantity | From Details: the batch's **current** amount (`batch_qty`), with the BOM's unit. From Summary: the amount just produced. |
| QR content | The batch's ERPNext URL (`…/app/batch/<batch_no>`), built from the Settings base URL, which is also the browser address. The PDA reduces any scanned URL to the batch number. |
| Details route | Only for batches this app manufactured: look up by batch number, then print. Shows the label fields, expiry status and the ingredients used (2.14). |
| Search route | One field: a scan or Enter opens an exact batch; typing lists batches by number or product name (case-insensitive, stock levels ignored). Advanced filters in a bottom sheet. 30 rows per page, with "Load more". A scan on Details replaces the batch shown (2.14). |
| Expiry warning | "Expires soon" means within 1 day. |
| Ingredients on Details | A plain list of batch numbers and quantities, not tappable. |
| PDA permissions | A dedicated "PDA User" role, the only role on each PDA user: Read on Batch, Item, BOM, UOM, Warehouse; Create/Write/Submit on Stock Entry and Serial and Batch Bundle, limited to Manufacture entries by a hook. **Done** (04, A7). |
| Editing a batch | Quantity only. Scans are ignored while editing, with a toast. |
| Rescan in the add dialog | Replaces the batch number but keeps a quantity the operator typed. |
| Keyboard after scan | Clear focus from all inputs and hide the keyboard. |
| BOM/quantity/explode change | Chosen batches should be cleared. |
| Process death | Nice to have, but not worth a local database. |
| Server | HTTPS. You control the server API and can change it. |
| After submit | Summary screen. "Same BOM again" returns to Start with every setting still filled in and only the batches cleared. |
| Page layout | Start: BOM, quantity, explode, expiry days, is finished. Form: batches only. |
| Expiry | Always required. Defaults to the product's shelf life, pre-filled when a BOM is picked; the operator can override it. |
| API key | One per device. |
| Batch unit | In the batch dialog, use the matched ingredient's `BomDetailsItem.uom`. On Details, use the Batch's `stock_uom`, which ERPNext fills itself and which equals the BOM unit for manufactured items (2.12). |
| Decimal separator | Assume `.`. |
| Bulgarian terms | BOM: "СНМ" (short for "Спецификация на материалите"). Explode: "Разгръщане". |

---

## 1. Fix first (crashes or broken features)

| # | What | Where | Why |
|---|------|-------|-----|
| 1 | `"%.1f".format(batch.quantity, …)` where `quantity` is a `String` | [IngredientTable.kt:159](../app/src/main/java/bg/butcher/pda/ui/components/IngredientTable.kt#L159) | Throws `IllegalFormatConversionException` as soon as the first batch row is shown. |
| 2 | Home's "Find Batch" button navigates to `Search`, which has no `composable(...)` | [HomeScreen.kt:37](../app/src/main/java/bg/butcher/pda/ui/screens/HomeScreen.kt#L37), [MainApp.kt](../app/src/main/java/bg/butcher/pda/ui/MainApp.kt) | `navigate()` to an unknown route throws. Hide the button until the screen exists. |
| 3 | The whole `FormUiState` (non-Parcelable data classes) is put in `SavedStateHandle` | [FormViewModel.kt:69](../app/src/main/java/bg/butcher/pda/ui/screens/FormViewModel.kt#L69) | Likely crashes when the app goes to the background or rotates. Save only user inputs; see 2.6. |
| 4 | `rememberSaveable` holding `BatchInfo` / `NetworkState` | [BatchDialog.kt:50-52](../app/src/main/java/bg/butcher/pda/ui/components/BatchDialog.kt#L50-L52) | Same problem, inside the dialog. |
| 5 | The Form route gets the ViewModel with `viewModel(parentEntry)` and no factory | [MainApp.kt](../app/src/main/java/bg/butcher/pda/ui/MainApp.kt) | Crashes if the app is restored directly onto Form. Use a nested nav graph with one shared helper. |
| 6 | `verticalScroll` on the `NavHost` | [MainApp.kt:101](../app/src/main/java/bg/butcher/pda/ui/MainApp.kt#L101) | Gives screens unlimited height: `weight(1f)` and centring stop working, and a `LazyColumn` inside can crash. Let each screen scroll itself. |
| 7 | `items` is sent as Kotlin `toString()` (`{SKU.001={B1=1.0}}`) | [ErpService.kt:36](../app/src/main/java/bg/butcher/pda/network/ErpService.kt#L36) | Every submit would fail. The server now also accepts a JSON body (04, A1); switch to `@Body` (2.4). |
| 8 | `createManufactureEntry` and `getBatch` responses aren't unwrapped (`{"message": …}` / `{"data": …}`) | [ErpService.kt](../app/src/main/java/bg/butcher/pda/network/ErpService.kt) | Deserialisation fails against the real server. Read the Batch's `stock_uom` (2.12), and note that `/api/resource/Batch/{id}` ignores `fields` and returns the whole document (01, finding 3). |
| 9 | `@QueryMap(encoded = true)` with a hand-built filter JSON | [NetworkErpRepository.kt:23-31](../app/src/main/java/bg/butcher/pda/data/NetworkErpRepository.kt#L23-L31) | `%be` in `"%beef%"` is decoded as a byte by the server, and a `"` in the query breaks the JSON. |
| 10 | The unit-test fake doesn't compile (`BatchDetails` is missing `uom`) and there are no tests | [FakeDataSource.kt:56](../app/src/test/java/bg/butcher/pda/fake/FakeDataSource.kt#L56) | Start with `NetworkErpRepository` + MockWebServer, then `FormViewModel`. |

Also high on the list:
- Changing BOM, quantity or explode doesn't cancel an in-flight fetch, so an old result can still open the Form. It also doesn't clear batches already chosen (2.7).
- `require(...)` in `upsertBatch`/`removeBatch` crashes on bad input.
- After submitting, pressing Back returns to a filled-in form that can be submitted again. OkHttp can also silently retry the POST, so the server may receive the same submission twice.
- The API key ends up in backups (`allowBackup=true` with the template backup rules).
- Server error messages (`_server_messages`) are never shown to the user.

---

## 2. Suggestions based on your answers and notes

### 2.1 Two ways in: manual entry and scanning

The flow you described:

- **Manual:** the + button opens an empty `BatchDialog`. The operator types the batch number and the quantity.
- **Scan:** a hardware scan **opens the dialog with the batch number already filled in**. Any further scans while the dialog is open **replace** the batch number.
- **Editing** an existing batch changes the quantity only. Scans are ignored, with a toast to say so.
- **After a scan,** focus is cleared and the keyboard hidden. A quantity the operator already typed is kept when a new scan replaces the batch number.
- In both cases the dialog works out the ingredient from the batch, using the network lookup (see 2.3).

**Hoist the batch number out of the dialog.** Today `batchNumberText` lives inside `BatchDialog`, so nothing outside can change it. A scan has to be able to open the dialog *and* overwrite the field, so the Form route should own the dialog's state. Adding and editing behave differently, so make them two cases:

```kotlin
// Where the input came from: scans skip the typing debounce.
data class BatchInput(val text: String, val fromScan: Boolean = false)

sealed interface BatchDialogState {
    data class Adding(val input: BatchInput) : BatchDialogState   // batch number editable, lookup runs
    data class Editing(val batch: BatchInfo) : BatchDialogState   // quantity only, no lookup, scans ignored
}

// FormScreen (or FormViewModel) owns this. null = dialog closed.
var dialog by rememberSaveable(stateSaver = BatchDialogStateSaver) { mutableStateOf<BatchDialogState?>(null) }

fun onScan(code: String) {
    when (dialog) {
        is BatchDialogState.Editing ->
            Toast.makeText(context, R.string.scan_ignored_while_editing, Toast.LENGTH_SHORT).show()
        else ->
            dialog = BatchDialogState.Adding(BatchInput(code, fromScan = true))   // opens, or replaces the text
    }
}

when (val d = dialog) {
    is BatchDialogState.Adding -> BatchDialog(
        batchNumber = d.input,
        onBatchNumberChange = { dialog = BatchDialogState.Adding(BatchInput(it)) },   // typing
        onDismiss = { dialog = null },
        ...
    )
    is BatchDialogState.Editing -> BatchDialog(
        initialValue = d.batch,          // batch number shown read-only
        onDismiss = { dialog = null },
        ...
    )
    null -> {}
}
```

```xml
<string name="scan_ignored_while_editing">Scan ignored. Close the edit to add a new batch.</string>
```

The + button becomes `dialog = BatchDialogState.Adding(BatchInput(""))`, and tapping a batch row's edit action becomes `dialog = BatchDialogState.Editing(batch)`.

`BatchDialogStateSaver` is a small custom `Saver`. Alternatively, make both cases `@Parcelize`, which needs the `kotlin-parcelize` plugin, and then no saver is needed. Either way it's needed so `rememberSaveable` doesn't hit the crash described in section 1, item 4.

The toast is shown directly in the Form route rather than through `FormViewModel.effects`. Today only the Start route collects those effects (02, finding 11), so a toast sent that way would not appear until the user goes back to Start.

**Edit mode is quantity-only.** This matches 03, B2:
- The batch number field is `readOnly`.
- No lookup runs, so the operator can fix a quantity even while offline.
- The Save button only needs a valid, changed quantity that keeps within the duplicate and tolerance rules. Re-validating the batch isn't needed.

```kotlin
// Inside BatchDialog: one lookup path for both sources, skipped entirely in edit mode
LaunchedEffect(batchNumber) {
    if (isEditing) return@LaunchedEffect
    status = NetworkState.Idle
    val number = batchNumber.text.trim()
    if (number.isEmpty()) return@LaunchedEffect
    if (!batchNumber.fromScan) delay(300.milliseconds)   // debounce typing only
    status = NetworkState.Loading
    status = fetchBatchDetails(number)
}
```

Because the effect is keyed on the whole `BatchInput`, a new scan restarts the lookup straight away. One catch: scanning the *same* code twice (for example to retry after a network error) creates an equal `BatchInput`, so the effect would not restart. If you want that retry, add a scan counter field so each scan is a distinct value.

**A new scan keeps the quantity.** When a scan replaces the batch number in the add dialog, the quantity the operator typed stays. This works without extra effort as long as two rules hold:

- **The quantity lives inside `BatchDialog` and isn't keyed on the batch number.** Going from `Adding(a)` to `Adding(b)` keeps the same `when` branch, so the dialog stays on screen and its remembered state survives. Today's code uses `rememberSaveable(initialValue)`. That's fine, because `initialValue` doesn't change on a scan. Just don't add the batch number as a key.
- **Typed and prefilled quantities are tracked separately.** Section 2.3 suggests filling in the quantity still needed once the lookup succeeds. If batch A's prefill were kept after scanning batch B (perhaps a different ingredient), B would get A's amount. So remember whether the operator typed the value:

```kotlin
var quantityText by rememberSaveable { mutableStateOf(initialValue?.quantity.orEmpty()) }
var quantityTyped by rememberSaveable { mutableStateOf(initialValue != null) }

QuantityInput(
    value = quantityText,
    onValueChange = { quantityText = it; quantityTyped = true },
    ...
)

// after a successful lookup:
if (!quantityTyped) quantityText = formatInput(remainingFor(details.item))
```

If the operator typed a value, it stays. If the app filled it in, it's recalculated for the new batch.

**Unfocus and hide the keyboard after a scan.** Whenever a scan arrives, whether it opens the dialog or replaces the batch number, clear focus from all fields and hide the on-screen keyboard. `Dialog` has its own window, so this has to happen *inside* `BatchDialog`, using the dialog's own focus manager:

```kotlin
// Inside BatchDialog
val focusManager = LocalFocusManager.current
val keyboard = LocalSoftwareKeyboardController.current

LaunchedEffect(batchNumber) {
    if (batchNumber.fromScan) {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }
    ...   // lookup as above (no debounce for scans)
}
```

Focus when the dialog first opens:
- **From +:** focus the batch number field, so the operator can type straight away.
- **From a scan:** focus nothing; it's already done.

```kotlin
val batchFocus = remember { FocusRequester() }
LaunchedEffect(Unit) {
    if (!isEditing && !batchNumber.fromScan) batchFocus.requestFocus()
}
```

If the operator then taps the quantity field, the keyboard opens as usual. The next scan closes it again.

**Keep the scanner behind an interface.** The rest of the app doesn't need to know about the vendor SDK, and you can test without the hardware. Section 2.11 covers the vendor SDK (`qspdasdk.jar`): the result arrives in a broadcast under the extra key `"data"`.

```kotlin
interface BarcodeScanner {
    val scans: Flow<String>
}

// Real one: the vendor SDK broadcasts each scan; extra = "data", action still to be found (2.11)
class BroadcastBarcodeScanner(private val context: Context, private val action: String, private val extra: String) : BarcodeScanner {
    override val scans: Flow<String> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                i.getStringExtra(extra)?.trim()?.takeIf { it.isNotEmpty() }?.let { trySend(it) }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }
}

// For the emulator / development: emit codes from a debug button.
class FakeBarcodeScanner : BarcodeScanner {
    private val _scans = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val scans = _scans
    fun scan(code: String) { _scans.tryEmit(code) }
}
```

**Listen only on Form, Search and Details** (Details added in 2.14). Put the scanner in `AppContainer` next to the repositories, and give the routes that care a small helper. A route that doesn't call it never sees scans:

```kotlin
// ui/components/OnScan.kt
@Composable
fun OnScan(enabled: Boolean = true, onScan: (String) -> Unit) {
    val scanner = (LocalContext.current.applicationContext as MainApplication).container.barcodeScanner
    val currentOnScan by rememberUpdatedState(onScan)   // always call the latest lambda without restarting
    LaunchedEffect(scanner, enabled) {
        if (enabled) scanner.scans.collect { currentOnScan(it) }
    }
}
```

```kotlin
// MainApp.kt, Form destination: only once the BOM has loaded, so there are ingredients to check against
OnScan(enabled = state.fetchState is NetworkState.Success) { code -> onScan(code) }

// Search destination: look the batch up and go straight to Details (2.14)
OnScan { code -> searchViewModel.onScan(code) }

// Details destination: replace the batch shown (2.14)
OnScan { code -> detailsViewModel.onScan(code) }
```

`LaunchedEffect` stops collecting when the route leaves the screen. For the broadcast version, `callbackFlow` also unregisters the receiver at that point, so a scan on Home, Start or Settings goes nowhere.

### 2.2 Submit rule: every ingredient within ±tolerance of the BOM amount

Your rule, per ingredient: the chosen total must be within **±tolerance %** of the required amount. Under and over are treated the same way. With 5%, a 5.00 kg requirement accepts anything from 4.75 to 5.25 kg.

**Where the tolerance comes from.** It will eventually be a field on the server's `get_bom_details` response. For now, hard-code 5%. Adding it to `BomDetails` as an **optional field with a default** means the app works today, and picks up the server value automatically once the server sends it. You won't need an app release when you change the server:

```kotlin
// model/BomDetails.kt
const val DEFAULT_TOLERANCE_PERCENT = 5.0

@Serializable
data class BomDetails(
    ...
    val items: List<BomDetailsItem>,
    // Not sent by the server yet; the default applies until it is.
    @SerialName("tolerance_percent")
    val tolerancePercent: Double = DEFAULT_TOLERANCE_PERCENT,
)
```

kotlinx.serialization uses the default when the key is missing, so this needs no other change. Neither `FakeErpRepository` nor the tests need updating either. One thing to watch: if the server ever sends `"tolerance_percent": null`, decoding fails. Either have the server always send a number, or make the field `Double?` and use `?: DEFAULT_TOLERANCE_PERCENT` where it's read.

With this approach `FormViewModel` doesn't need the settings at all, so ignore the earlier `SettingsRepository` / `overagePercent` idea.

**The rule, as plain functions of state:**

```kotlin
// FormUiState
enum class Coverage { MISSING, UNDER, OK, OVER }

val bom: BomDetails? get() = (fetchState as? NetworkState.Success)?.data

fun chosenQuantity(itemCode: String): Double = items[itemCode].orEmpty().values.sum()

fun allowedRange(ingredient: BomDetailsItem): ClosedFloatingPointRange<Double> {
    // Whole-number units use ±0.5 instead of the percentage; see 2.13, part 5
    val margin = ingredient.quantity * (bom?.tolerancePercent ?: DEFAULT_TOLERANCE_PERCENT) / 100
    return (ingredient.quantity - margin)..(ingredient.quantity + margin)
}

fun coverage(ingredient: BomDetailsItem): Coverage {
    val chosen = chosenQuantity(ingredient.itemCode)
    val range = allowedRange(ingredient)
    return when {
        chosen == 0.0 -> Coverage.MISSING
        chosen < range.start - EPSILON -> Coverage.UNDER
        chosen > range.endInclusive + EPSILON -> Coverage.OVER
        else -> Coverage.OK
    }
}

val canSubmit: Boolean
    get() = bom?.items?.all { coverage(it) == Coverage.OK } == true &&
        submitState !is NetworkState.Loading

private const val EPSILON = 0.0005   // half a gram in kg; absorbs rounding in sums
```

Some notes on the numbers:

- **`EPSILON`:** a tolerance range already absorbs most rounding, but at the exact edge (e.g. a sum of 4.7499999 against a 4.75 minimum) the extra half gram stops a correct entry from being rejected.
- **`Double`:** switching quantities from `Float` to `Double` (01, finding 14) is still worth doing.
- **Prefill:** the quantity prefill from 2.3 ("what's still needed") should use the required amount, not the edge of the range. The operator then aims for the recipe, and the tolerance only absorbs real-world differences.

**Table.** `IngredientTable` should show each ingredient's state, so the operator can see *why* Submit is disabled. For a 5.00 kg line at ±5%:

| State | Display |
|---|---|
| MISSING | `0 / 5.00 kg` in grey |
| UNDER | `4.60 / 5.00 kg · at least 4.75` in amber |
| OK | `4.90 / 5.00 kg` with a ✓ |
| OVER | `5.40 / 5.00 kg · at most 5.25` in red |

### 2.3 Infer the ingredient from the batch, and block batches not in the recipe

As you described, the operator doesn't pick the ingredient. The batch lookup returns `item` (the item code), and that decides which ingredient row the batch goes under. What's missing today is a check that this item is **in the recipe at all**. Without it, a scanned pork batch on a beef recipe would be accepted. `IngredientTable` only draws rows for BOM ingredients, so that batch would then be stored and sent but never shown.

Pass the recipe's item codes into the dialog and reject anything else:

```kotlin
@Composable
fun BatchDialog(
    batchNumber: BatchInput,
    onBatchNumberChange: (String) -> Unit,
    ingredients: List<BomDetailsItem>,     // from the loaded BOM
    ...
)

// after the lookup:
val result = fetchBatchDetails(number)
status = if (result is NetworkState.Success && ingredients.none { it.itemCode == result.data.item }) {
    NetworkState.Error(FetchBatchError.NOT_IN_RECIPE)
} else {
    result
}
```

```xml
<string name="batch_not_in_recipe">Batch %1$s is %2$s, which is not in this recipe.</string>
```

To make this message possible, add a `NOT_IN_RECIPE` value to `FetchBatchError`. The `Error` state then needs to carry the fetched `BatchDetails` too, so the message can name the item.

Two things make this flow clearer for the operator:

- **Show the inferred ingredient in the dialog** once the lookup succeeds, e.g. "Beef trim (SKU.012) · kg" under the batch field. Right now the fetched `itemName` is thrown away, so the operator can't see what the scan matched until the dialog closes.
- **Prefill the quantity with what's still needed** for that ingredient (required minus already chosen), but only if the operator hasn't typed one (see 2.1 on rescans). The common case then becomes: scan, check, confirm.

**Units: use the BOM line's unit.** The dialog already receives the BOM's ingredients for the "not in recipe" check. Once the looked-up batch's `item` matches an ingredient, take the unit from that `BomDetailsItem.uom` instead of `BatchDetails.uom`:

```kotlin
// after a successful lookup
val ingredient = ingredients.firstOrNull { it.itemCode == result.data.item }
status = when {
    ingredient == null -> NetworkState.Error(FetchBatchError.NOT_IN_RECIPE)
    else -> NetworkState.Success(result.data)
}
val unit = ingredient?.uom.orEmpty()   // shown next to the quantity field
```

This is also the *correct* unit, not just the simpler one. The quantity the operator types is added to that ingredient's total, which is compared with the BOM line's required amount (2.2). The server also writes each ingredient row in the BOM line's unit, `item.uom` with its conversion factor (`api.py` lines 168-170). If the dialog showed a different unit, the operator could type "500" meaning grams, while it was counted as kilograms.

`BatchDetails.uom` is then no longer needed by the dialog. The Details route reads the Batch's `stock_uom` instead (2.12, point 2).

Do the same check in `FormViewModel.upsertBatch`, returning an error instead of the current `require(...)` crash. That way a future code path can't bypass it. The server should check it too (see 2.4).

**Reject a batch that's already been added.** Do this check in the dialog **before** the network lookup. It's instant and needs no server. Show the result as the batch field's error text, which keeps the field editable, and a new scan replaces the text and re-checks:

```kotlin
// BatchDialog gets the batch numbers already on the form
existingBatchNumbers: Set<String>,   // state.items.values.flatMap { it.keys }.toSet()

LaunchedEffect(batchNumber) {
    val number = batchNumber.text.trim()
    ...
    if (number in existingBatchNumbers) {
        status = NetworkState.Error(FetchBatchError.ALREADY_ADDED)
        return@LaunchedEffect
    }
    ...
    val result = fetchBatchDetails(number)
    // Check again against the server's own name, in case it differs in case/spacing from what was typed
    if (result is NetworkState.Success && result.data.name in existingBatchNumbers) {
        status = NetworkState.Error(FetchBatchError.ALREADY_ADDED)
        return@LaunchedEffect
    }
}
```

```xml
<string name="batch_already_added">This batch has already been added.</string>
```

A flat set of batch numbers works because a Frappe Batch's name is its unique ID across all items. Edit mode never reaches this check, because it skips the lookup (2.1), so the batch being edited can't flag itself.

`upsertBatch` should also refuse duplicates in add mode. Today it silently overwrites the earlier quantity.

### 2.4 You control the server: send a JSON body

*All server-side changes from this summary, plus a few more found while reading `api.py`, are collected in [04-server-changes.md](04-server-changes.md). The snippets below show the app side.*

Rather than encoding JSON into a form field, change `create_manufacture_entry` to accept a JSON body and send a typed request from the app. Frappe passes JSON body keys to whitelisted methods as arguments, so the Python side mostly just loses the `json.loads`.

```kotlin
@Serializable
data class ManufactureEntryRequest(
    val bom: String,
    val qty: Double,
    val items: Map<String, Map<String, Double>>,   // item_code -> batch_no -> qty
    @SerialName("expiry_date") val expiryDate: LocalDate,      // always required
    val explode: Boolean,
    @SerialName("is_finished") val isFinished: Boolean,
)

@POST("/api/method/my_app.my_app.api.create_manufacture_entry")
suspend fun createManufactureEntry(@Body request: ManufactureEntryRequest): MessageWrapper<ManufactureEntry>
```

```python
# api.py — items now arrives already parsed
@frappe.whitelist()
def create_manufacture_entry(bom, qty, items, expiry_date=None, explode=False, is_finished=True, request_id=None):  # expiry checked in code (04, implementation notes)
    items = frappe.parse_json(items)  # still works if a string is passed, so it stays backwards compatible
```

While you're changing the server, two more ideas:
- Accept a client-generated **request id** (a UUID created when the form opens) and ignore repeats. This deals with double taps, Back-and-resubmit, and OkHttp retries all at once.
- Add `tolerance_percent` to the `get_bom_details` response, for example from a custom field on the BOM with a site-wide default. The app already reads it if present (2.2). It's also worth checking the tolerance in `create_manufacture_entry`, so an old app version can't submit totals outside the range.
- Check on the server too that every batch's item is in the BOM. The client-side check in 2.3 can be bypassed, for example by an old version of the app.

Also use the same `MessageWrapper<T>` for `get_bom_details`, and set up `Json { ignoreUnknownKeys = true }` so a new server field doesn't break old PDAs.

### 2.5 HTTPS only, including on Android 6

Since the server is HTTPS, there are three steps:

1. **Turn cleartext off explicitly.** Removing `android:usesCleartextTraffic="true"` is *not* enough on your devices. The "blocked by default" behaviour depends on `targetSdk ≥ 28`, which Android 6 doesn't know about, so on Android 6 the default stays "allowed". Set it to `false`:

   ```xml
   <application
       android:usesCleartextTraffic="false"
       ... >
   ```

   OkHttp checks this flag, and refuses `http://` URLs with an `UnknownServiceException` ("CLEARTEXT communication … not permitted").

2. **Validate the scheme in Settings.** This is the guard users actually see. Only accept `https://` URLs. If someone types just a host name, adding `https://` for them is friendly:

   ```kotlin
   fun normaliseBaseUrl(input: String): HttpUrl? {
       val text = input.trim().let { if ("://" in it) it else "https://$it" }
       return text.toHttpUrlOrNull()?.takeIf { it.isHttps }
   }
   ```

   Use this same function in `SettingsViewModel` and `ErpServiceInterceptor`. Today they use different validators (`URLUtil.isValidUrl` vs `toHttpUrlOrNull`), so they can disagree (02, finding 16).

3. **Keep the bundled Let's Encrypt roots.** Android 6 still needs them. The placeholder `http://localhost/` in Retrofit is fine, since the interceptor replaces it before any connection is made.

Report 01, finding 12 has been updated to match.

### 2.6 Keeping the form across process death, without a database

Two different events can lose the form:

- **Android kills the app in the background** (low memory, or the PDA asleep for a long time). Android keeps the saved state (`SavedStateHandle`, `rememberSaveable`) and gives it back when the user returns. This is cheap to support.
- **Battery swap, reboot, force-stop, or swiping the app away.** Saved state is gone. Surviving this would need a file or a database, so by your answer it's out of scope.

So the plan is to fix the current crash and get the first case almost for free. Save only small inputs, as strings, and re-fetch everything else:

```kotlin
class FormViewModel(
    private val erpRepository: ErpRepository,
    private val settingsRepository: SettingsRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    // Plain state flow: server results and Loading states are NOT saved
    private val _uiState = MutableStateFlow(
        FormUiState(
            quantityText = saved["quantity"] ?: "",
            explode = saved["explode"] ?: false,
            expiryDaysText = saved["expiry"] ?: "",
            isFinished = saved["finished"] ?: true,
            items = saved.get<String>("items")?.let { Json.decodeFromString(it) } ?: emptyMap(),
        )
    )

    fun setQuantity(quantity: String) {
        saved["quantity"] = quantity
        _uiState.update { ... }
    }

    private fun saveItems(items: Map<String, Map<String, Double>>) {
        saved["items"] = Json.encodeToString(items)
    }
}
```

Save the selected BOM's ID (and item name, for display) as strings as well. When the Form route comes back with no BOM loaded (`fetchState` is `Idle`), call `fetchBomDetails()` again instead of showing an empty screen. If you'd rather not bother, the even simpler option is to drop `SavedStateHandle` entirely: when the Form route finds no BOM loaded, it navigates back to Home. Either way, there's no more crash.

### 2.7 Correction: chosen batches *can* survive a BOM change today

You said this can't happen because the BOM, quantity and explode are set on the first page. But Back from Form returns to Start **with the same ViewModel**, because the ViewModel is scoped to the Start back-stack entry. So this sequence is possible:

1. Pick BOM A on Start, then Continue.
2. Scan batches on Form.
3. Press Back and pick BOM B on Start, then Continue.
4. Form now shows B's ingredients, while `items` still holds A's batches.

A's batches don't appear in the table, but they would be sent on submit.

The fix is small. Clear `items` in the three setters, and save them (2.6):

```kotlin
fun setSelectedBom(bom: BomSearchResultItem) {
    _uiState.update { it.copy(selectedItem = bom, fetchState = NetworkState.Idle, items = emptyMap()) }
}
// same for setQuantity / setExplode
```

Only clear when the value actually changes (`if (bom.name != it.selectedItem?.name)`). Otherwise, re-selecting the same BOM by accident would throw away a list of scanned batches. You could also confirm before clearing ("Changing the recipe will remove 4 scanned batches"), but that's optional.

### 2.8 After submit: Summary, with "Same BOM again" back to Start

The flow after a successful submit:

1. **Summary** shows what the server created: the new batch number, item name, production and expiry date, and whether it's finished.
2. Two buttons:
   - **Same BOM again:** go back to **Start** with everything still filled in (BOM, quantity, explode, expiry days, is finished). Only the chosen batches are cleared. The operator adjusts what they need and presses Continue.
   - **Done:** go Home. The form state is thrown away.
3. **Back** on Summary acts like Done. It must not return to the filled-in Form, which could be submitted a second time (02, finding 8).

**Shared ViewModel.** Group Start, Form and Summary in a nested navigation graph, and scope `FormViewModel` to the graph instead of the Start entry. All three screens then share one ViewModel, and it is cleared when the graph is left. This also fixes section 1, item 5:

```kotlin
navigation(route = "manufacture", startDestination = MainScreen.Start.name) {
    composable(MainScreen.Start.name) { entry -> val vm = entry.formViewModel(navController); ... }
    composable(MainScreen.Form.name) { entry -> val vm = entry.formViewModel(navController); ... }
    composable(MainScreen.Summary.name) { entry -> val vm = entry.formViewModel(navController); ... }
}

@Composable
private fun NavBackStackEntry.formViewModel(navController: NavController): FormViewModel {
    val graphEntry = remember(this) { navController.getBackStackEntry("manufacture") }
    return viewModel(graphEntry, factory = FormViewModel.Factory)
}
```

**Navigation.** The back stack is Home → Start → Summary, because Form was replaced when the submit succeeded:

```kotlin
// Submit succeeded: replace Form with Summary, so Back can't reach the submitted form
navController.navigate(MainScreen.Summary.name) {
    popUpTo(MainScreen.Form.name) { inclusive = true }
}

// Summary: "Same BOM again" -> back to the Start page that's already on the stack
viewModel.startAgain()
navController.popBackStack(MainScreen.Start.name, inclusive = false)

// Summary: "Done", and also the system Back button
val done = { navController.popBackStack("manufacture", inclusive = true) }   // clears the ViewModel
BackHandler(onBack = done)
```

Popping back to the existing Start entry, instead of navigating to a new one, keeps the same ViewModel and so all the values. Back from Start then goes Home as usual.

**ViewModel.** `startAgain()` clears only the per-run data:

```kotlin
fun startAgain() {
    _uiState.update {
        it.copy(
            items = emptyMap(),
            submitState = NetworkState.Idle,
            requestId = newRequestId(),   // the next submission must not look like a repeat of the last one (2.4)
            // kept: selectedItem, quantityText, explode, expiryDaysText, isFinished, fetchState
        )
    }
    saveItems(emptyMap())                 // keep the saved copy in step (2.6)
}
```

This works with the rules already suggested:

- **Changing quantity or explode on Start** clears `fetchState` and `items` (2.7). `items` is already empty here, so nothing is lost, and Continue fetches the BOM again for the new quantity.
- **Changing nothing and pressing Continue** can skip the network call. If `fetchState` is still `Success`, go straight to Form:

  ```kotlin
  fun fetchBomDetails() {
      if (_uiState.value.fetchState is NetworkState.Success) {
          _effects.trySend(FormUiEffect.GotoNextPage)   // BOM already loaded for these inputs
          return
      }
      ...
  }
  ```

  This is safe because every input that affects the BOM details clears `fetchState` when it changes.
- **The Start page shows the selected BOM.** If the search field's text and the selected BOM are separate states (`searchQuery` vs `selectedItem`), check that the page clearly shows the selected BOM when the operator comes back, and not just an empty search box with stale results.

**Where expiry days and "Is finished" live:** they move to Start, so Form holds only batches. See 2.10.

### 2.9 Decimal separator: why it matters, and assuming `.`

You asked why it matters. In Bulgarian the decimal separator is a comma, and **some parts of Android follow the device language while others don't**. If they disagree, a value the app shows can't be typed back in:

- **Parsing ignores the language.** `toFloatOrNull()` / `toDoubleOrNull()` only accept `.`. Typing `2,5` gives "Enter a valid number."
- **Formatting follows the language.** `"%.1f".format(x)` (as in [IngredientTable.kt:96](../app/src/main/java/bg/butcher/pda/ui/components/IngredientTable.kt#L96)) uses the device language, so on a Bulgarian PDA it prints `5,0`. The table would show `4,8 / 5,0 Kgs`, and the operator would then type `4.8`.
- **The keyboard depends on the device.** Some keyboards for Bulgarian show only `,` on the number pad. On Android 6 the stock decimal keyboard usually shows `.`, but PDA vendors often ship their own keyboard.
- **The quantity prefill from 2.3 makes this worse.** If the app formats "0.20 still needed" using the device language, it puts `0,20` into the field, and its own parser then rejects it.

Assuming `.` is fine, as long as the whole app sticks to it. Three small rules:

```kotlin
// util/Decimal.kt: the only place numbers are turned into text and back

/** Accepts "2.5" and, as a safety net, "2,5". */
fun parseDecimal(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/** Always "." and no exponent: "1.25", never "1,25" or "1.0E-4". */
fun formatDecimal(value: Double, maxDecimals: Int = 3): String =
    BigDecimal.valueOf(value).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
```

1. **Parse** every quantity with `parseDecimal`: Start's quantity, the dialog quantity, and `FormUiState.quantity`. Accepting a comma costs nothing, and it covers a vendor keyboard you haven't seen yet.
2. **Format** every number shown or prefilled with `formatDecimal`, never with `"%.1f".format`. That also fixes `0.04` showing as `0.0` (03, finding 7).
3. **Send** numbers to the server as numbers (they already are, in the JSON body from 2.4), never as formatted text.

If the operators later prefer seeing commas, only `formatDecimal` needs to change.

### 2.10 Page layout: Start holds every run setting, Form holds only batches

The BOM details fetch splits the flow into "before" and "after". So there are only two sensible layouts:

| Layout | Start page | Form page |
|---|---|---|
| A | BOM only | quantity, explode, expiry, finished, batches |
| **B (chosen)** | BOM, quantity, explode, expiry days, is finished | batches only |

**Why B is the right choice:**

- **"Same BOM again" fits it.** Every setting the operator might change between runs is on the page they return to. Form is only about the batches for this run.
- **Layout A wouldn't really work.** `explode` changes *which* ingredients the server returns, not just their amounts. Quantity scales them. Both must be known **before** the fetch, so in layout A, changing either on Form would need a re-fetch from Form, and the batches already chosen would have to be cleared (2.7). Under B, the fetch inputs and the page that triggers the fetch are the same.
- **Expiry and "is finished" don't affect the fetch.** They're only sent on submit, so they could live on either page. Putting them on Start keeps Form simple, and makes Form the only page the scanner listens on (2.1).

**What changes:**

1. **Move the two fields.** `ExpiryInput` and the "Is finished" checkbox go from [FormScreen.kt:56-87](../app/src/main/java/bg/butcher/pda/ui/screens/FormScreen.kt#L56) to the Start screen. Their callbacks (`setDaysUntilExpiry`, `setIsFinished`) move from the Form route to the Start route in [MainApp.kt](../app/src/main/java/bg/butcher/pda/ui/MainApp.kt). `FormUiState` already holds both values, so the ViewModel doesn't change.
2. **Don't clear the fetch for them.** `setDaysUntilExpiry` and `setIsFinished` must *not* reset `fetchState` or `items`, because they don't affect the BOM details. The current code is already right on this. Keep it that way, so that after "Same BOM again" a change of expiry alone doesn't force a needless re-fetch (2.8).
3. **Start's Continue rule.** Expiry is always required, so `canContinue` must also require valid expiry days (a whole number > 0). Otherwise the operator only finds out on Form that it's invalid, and has to go back. The submit request then always has an expiry date, so `expiryDate` can become non-null in `ManufactureEntryRequest` (2.4).

   ```kotlin
   val canContinue: Boolean
       get() = quantity != null && selectedItem != null && expiryDays != null
   ```
4. **Show a summary on Form.** Since the run settings are no longer editable on Form, show them read-only in the header, for example: "СНМ: Sausage (BOM.012) · 20 kg · Expires in 5 days · Finished". The operator can then check them while scanning, and press Back to change them.
5. **Start becomes longer.** The page now holds the BOM search, quantity, explode, expiry and the finished checkbox. With the `verticalScroll` removed from the NavHost (section 1, item 6), Start should scroll itself (`Modifier.verticalScroll` on its own `Column`). Keep Continue visible at the bottom, outside the scrolling area.

**Expiry default from the BOM's shelf life.** `BomDetails` has `shelfLife` (`shelf_life_in_days`), but nothing reads it, and the expiry field starts empty. You want it as the default. Under this layout, though, the expiry field is on Start, while `shelfLife` only arrives with the BOM details *after* Continue. The value has to be available as soon as a BOM is selected.

**Return it from the existing BOM search.** No new endpoint is needed. `NetworkErpRepository.searchBom` already chooses the returned columns through the `fields` query parameter, so add the shelf life to that list. One detail: `get_bom_details` on the server reads `shelf_life_in_days` from the **Item** (via `get_fg(bom_doc.item)`), not from the BOM. So in the BOM list call it has to be fetched through the `item` link field, using Frappe's `link_field.target_field` notation. Frappe adds the join automatically. *Confirmed against your server with `curl`: it returns the expected value.*

```kotlin
// NetworkErpRepository.searchBom (using the buildJsonArray approach from 01, finding 15)
"fields" to buildJsonArray {
    add("name"); add("item"); add("item_name"); add("uom"); add("is_default")
    add("item.shelf_life_in_days as shelf_life_in_days")   // from the linked Item
}.toString(),
```

Keep the `as` alias: it pins the key name to `shelf_life_in_days`, which is what `@SerialName` expects. Your test sent the parameter URL-encoded, which is what Retrofit does once `encoded = true` is removed from `@QueryMap` (section 1, item 9). With `encoded = true` still in place, the spaces in `as` would be encoded by OkHttp anyway, but the `%` wildcards in the search filter still need the fix.

The model gets one more field:

```kotlin
@Serializable
data class BomSearchResultItem(
    val name: String,
    val item: String,
    @SerialName("item_name") val itemName: String,
    val uom: String,
    @SerialName("is_default") val isDefault: Int,
    @SerialName("shelf_life_in_days") val shelfLife: Int? = null,   // null or 0 if the item has none
)
```

The default value means `FakeErpRepository` and older responses keep working. Have the fake return a random shelf life too, so the prefill can be tried without a server.

**Prefill rule.** This uses the same "typed vs prefilled" idea as the quantity in 2.1:

```kotlin
// FormUiState
val expiryTyped: Boolean = false

// FormViewModel
fun setSelectedBom(bom: BomSearchResultItem) {
    _uiState.update {
        if (bom.name == it.selectedItem?.name) return@update it
        it.copy(
            selectedItem = bom,
            fetchState = NetworkState.Idle,
            items = emptyMap(),
            // A new product gets its own default, even if the old one was typed over
            expiryDaysText = bom.shelfLife?.takeIf { it > 0 }?.toString().orEmpty(),
            expiryTyped = false,
        )
    }
}

fun setDaysUntilExpiry(days: String) {
    _uiState.update { it.copy(expiryDaysText = days, expiryTyped = true) }
}
```

- **Picking a different BOM** replaces the expiry with that product's shelf life, because a value typed for the previous product no longer applies.
- **"Same BOM again"** keeps whatever is there, typed or not, since `startAgain()` doesn't touch it (2.8).
- **No shelf life:** if the item has none (`null` or `0`, which is how ERPNext stores "not set" and how `get_bom_details` reports it), the field stays empty, and the operator must type a value before Continue.

A small hint under the field helps the operator tell the two apart, e.g. "Default from product: 5 days", shown when the value has been changed.

### 2.11 The vendor scanner SDK (`qspdasdk.jar`) and label printing

An agent analysed the JAR and both `.so` files statically, without running them. The full report is [05-scanner-sdk.md](05-scanner-sdk.md). I checked the key points in the bytecode myself. Several things differ from the vendor's documentation:

| Topic | Vendor docs | What the JAR actually does (verified) |
|---|---|---|
| Scan **result** | Broadcast `ismart.intent.scandown`, text in `"data"` | Broadcast **`com.qs.scancode`**, text in `"data"`, sent by `PrintUtils$7` |
| `ismart.intent.scandown` | (the result) | The **trigger**: the SDK's own receiver listens for it (and `hbyapi.intent.key_scan_down`), and starts a scan |
| Start position | `setStartNum(30)` | **`setStarNum(30)`**. `setStartNum` doesn't exist, so the doc snippet won't compile |
| Context | `initPrintUtils(this)` in the Activity | Stored in `public static Context context`, so an Activity leaks. `applicationContext` works |
| Scanner vs SDK | (not said) | The scanner talks over the same serial port as the printer, and a reader thread started by `initPrintUtils` produces `com.qs.scancode`. **No `initPrintUtils`, no scans** |
| Threading | (not said) | `PrintUtils` creates `new Handler()` in its static initializer and in every print call. Use it **only from the main thread**. From `Dispatchers.IO` it fails with `ExceptionInInitializerError` |

Setup delay: 2500 ms (from you). The agent found the reason: the SDK resets the printer 2000 ms after `initPrintUtils`, which would undo `labelEnable` / `setStarNum` if they ran earlier.

#### 1. Build setup

The repo now has `app/libs/qspdasdk.jar`, `jniLibs/arm64-v8a/` and `jniLibs/armeabi/`. Two changes are needed:

```kotlin
// app/build.gradle.kts: AGP does not pick up libs/*.jar automatically
dependencies {
    implementation(files("libs/qspdasdk.jar"))
    ...
}
```

```sh
git mv app/src/main/jniLibs/armeabi app/src/main/jniLibs/armeabi-v7a
```

**Why rename the folder:**
- **ABI.** The 32-bit library is built for ARMv5TE, the old `armeabi` ABI (verified with `file`). ARMv5 code runs fine on ARMv7.
- **How Android picks native libs.** At install time, Android picks **one** ABI folder from the APK and loads every native library from it. If any dependency ships `lib/armeabi-v7a/` (Compose very likely does; check the APK), a 32-bit device chooses that folder and never looks in `armeabi`, so the SDK fails with `UnsatisfiedLinkError`.
- **Check it** after a build: `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep '\.so'`. Every ABI folder in the APK should contain `libzyapi_common.so`.

**Notes:**
- **The emulator** has no x86 build of the library. `CommonApi`'s static initializer calls `System.loadLibrary("zyapi_common")`, so `initPrintUtils` throws there. Catch `LinkageError` (which covers both `UnsatisfiedLinkError` and `NoClassDefFoundError`) and fall back to the fakes.
- **Bundled zxing.** The JAR includes **zxing 3.5.1** under its original package name. Don't add a separate zxing dependency, or the build fails with duplicate classes. To make a QR image, use the bundled `com.google.zxing.qrcode.QRCodeWriter`.
- **Keep rules** for [rules.keep](../app/src/main/keepRules/rules.keep). They have no effect yet, because R8 is off for release, but they stop the native calls breaking when it's turned on:

  ```proguard
  # Vendor PDA SDK (qspdasdk.jar): JNI binds by name to android.zyapi.CommonApi
  -keep class android.zyapi.CommonApi { native <methods>; <init>(); }
  -keepclasseswithmembernames class * { native <methods>; }
  -keep class com.qs.wiget.** { *; }
  -dontwarn com.google.zxing.**
  -dontwarn com.qs5501demo.aidl.**
  ```

#### 2. Setting up the SDK once, in `Application.onCreate`

Because the SDK keeps a static context and leaks a serial port and a thread if set up twice, start it **once per process** in `MainApplication.onCreate`, with the application context. Not in `MainActivity.onCreate`, which runs again on every rotation. `closeCommonApi()` exists, but throws if called twice, and there's no need for it: let the process end.

```kotlin
// hardware/QsPdaHardware.kt
object QsPdaHardware {
    var available = false
        private set

    /** Call once, from MainApplication.onCreate (main thread). */
    fun init(app: Application) {
        try {
            PrintUtils.isCanScan = false                 // only the scan screens turn it on (part 3)
            PrintUtils.initPrintUtils(app.applicationContext)
            Handler(Looper.getMainLooper()).postDelayed({
                PrintUtils.labelEnable(true)             // order is important (vendor)
                PrintUtils.setStarNum(30)                // sic: setStarNum, not setStartNum
            }, 2_500)                                    // after the SDK's own printer reset at 2000 ms
            available = true
        } catch (e: LinkageError) {                      // emulator, or a device without the vendor library
            Log.w("QsPdaHardware", "Scanner/printer SDK not available", e)
        }
    }
}

// MainApplication.onCreate
QsPdaHardware.init(this)
container = DefaultAppContainer(this)   // pick real or fake scanner/printer from QsPdaHardware.available
```

The SDK picks the serial port and GPIO pins by matching `Build.MODEL` against a list, and falls back to `/dev/ttyMT1` if the model isn't listed. Log `Build.MODEL` once on the real device, and check it against the list in 05, section 6.

#### 3. Receiving scans

**Listen for `com.qs.scancode`**, not `ismart.intent.scandown`:

```kotlin
BroadcastBarcodeScanner(
    context = applicationContext,
    action = "com.qs.scancode",   // result broadcast sent by the SDK (verified in the JAR)
    extra = "data",
)
```

- **Confirm on the device.** Your docs say the result comes on `ismart.intent.scandown`. On a firmware where a system scanner service does that itself, `com.qs.scancode` would stay silent. Ten minutes with a test receiver for both actions settles it. If both carry `"data"`, listen to both, and ignore broadcasts where `data` is missing or blank (a pure trigger broadcast has none).
- **The SDK's trigger receiver is always registered** once `initPrintUtils` has run. So pressing the hardware button would fire the scanner on every screen. Use the static switch to limit it to Form and Search:

  ```kotlin
  // in OnScan (2.1), when the screen starts and stops listening
  DisposableEffect(enabled) {
      PrintUtils.isCanScan = enabled
      onDispose { PrintUtils.isCanScan = false }
  }
  ```

  Put this in the real `BarcodeScanner` as `setActive(Boolean)`, so the fake doesn't touch `PrintUtils`.
- **Read the text with `intent.getStringExtra("data")`** and register the receiver from code with `RECEIVER_EXPORTED`, as in 2.1. The broadcast comes from the SDK's `sendBroadcast`, which is global.
- **Known decoder quirks:**
  - The SDK silently drops a few exact strings, including `11`, `M`, `BE` and `start`. If your batch numbering could ever produce exactly one of these, change the series.
  - It decodes as GBK first, so a QR code containing Cyrillic may come out garbled. ASCII batch numbers are fine.
- **Logging.** The SDK logs every scan at error level (`Log.e`). That's harmless for batch numbers, but expect noisy logcat.
- **Beep on an accepted batch.** Put the vendor's `.ogg` in `res/raw/scan_beep.ogg`. Play it when the lookup **succeeds**, not when the scan arrives. Leave out the sample's toast.

  ```kotlin
  class ScanFeedback(context: Context) {
      private val sounds = SoundPool.Builder().setMaxStreams(1).build()
      private val beep = sounds.load(context, R.raw.scan_beep, 1)
      fun accepted() { sounds.play(beep, 1f, 1f, 1, 0, 1f) }
  }
  ```

  `SoundPool` plays short clips with less delay than the sample's single `MediaPlayer`, which misses beeps when scans come quickly. Create one in `AppContainer`. Adding `rejected()` later is one line.

There's no on-screen scan button, since the device has a hardware trigger. On the emulator, use `FakeBarcodeScanner` from a debug-only menu or a test.

#### 4. Printing labels from Details and Summary

Put the printer behind its own interface now, even though the printing itself comes later. Then the buttons, their states and their tests can be built today.

**What goes on the label** (your list):

| On the label | From `create_manufacture_entry` today? |
|---|---|
| Batch number as a **QR code** (the ERPNext batch URL, part 5) | ✅ `batch_no`, plus the base URL |
| Batch number as text | ✅ `batch_no` |
| Item name | ⚠️ The key `item_name` exists, but holds the item **code** (`fg_doc.name`), not the name (01, finding 23) |
| Quantity (and its unit) | ❌ Not returned. The server uses it for `fg_completed_qty`, but it isn't in the response |
| Production date | ✅ `production_date` |
| Expiry date | ✅ `expiry_date` |
| Stock entry ID as text | ✅ `name` (the Stock Entry's ID) |

So the server response needs two small changes, which you can make while you're changing the endpoint for the JSON body (2.4):

```python
return {
    "name": se.name,
    "batch_no": fg_batch.name,
    "item_code": fg_doc.name,          # keep the code, under an honest name
    "item_name": fg_doc.item_name,     # the display name, for the label
    "qty": flt(qty),
    "uom": fg_doc.stock_uom,           # same as the BOM's uom for finished goods (api.py line 192)
    "production_date": str(today()),
    "expiry_date": str(expiry_date),   # always set now (2.10)
    "is_finished": is_finished,
}
```

```kotlin
@Serializable
data class ManufactureEntry(
    @SerialName("name") val stockEntryId: String,
    @SerialName("batch_no") val batchNumber: String,
    @SerialName("item_code") val itemCode: String,
    @SerialName("item_name") val itemName: String,
    @SerialName("qty") val quantity: Double,
    val uom: String,
    @SerialName("production_date") val productionDate: LocalDate,
    @SerialName("expiry_date") val expiryDate: LocalDate,
    @SerialName("is_finished") val isFinished: Boolean,
)
```

Renaming `name` to `stockEntryId` in Kotlin makes the label code clearer. The JSON key stays `name`.

**Label data and printer interface:**

```kotlin
data class LabelData(
    val batchNumber: String,     // printed as text; the QR code holds batchUrl(...) (part 5)
    val itemName: String,
    val quantity: String,        // already formatted, e.g. "20.5 kg" (formatDecimal, 2.9)
    val productionDate: LocalDate,
    val expiryDate: LocalDate,
    val stockEntryId: String?,   // from Details: the Batch's reference_name, if set
)

fun ManufactureEntry.toLabel() = LabelData(
    batchNumber = batchNumber,
    itemName = itemName,
    quantity = "${formatDecimal(quantity)} $uom",
    productionDate = productionDate,
    expiryDate = expiryDate,
    stockEntryId = stockEntryId,
)

interface LabelPrinter {
    val isAvailable: Boolean
    suspend fun print(label: LabelData): Result<Unit>
}

class FakeLabelPrinter : LabelPrinter {
    override val isAvailable = true
    override suspend fun print(label: LabelData): Result<Unit> {
        Log.i("FakeLabelPrinter", "Would print $label")
        delay(500)
        return Result.success(Unit)
    }
}
```

The real `QsPdaLabelPrinter` comes later. What the JAR tells us about it (05, section 4):

- **Render the whole label into one bitmap**, 384 px wide, and print it with a single `PrintUtils.printBitmap(1, bitmap, true)`. Draw the QR code with the bundled zxing `QRCodeWriter`, and the text lines with a normal Android `Canvas` and font. Two reasons:
  - `printText(..., isLabel = true)` feeds a new label **after each call**, so one call per line would print seven labels.
  - `printText` sends GBK text to the printer's built-in font, which may not have Cyrillic letters. A bitmap avoids both problems.
- **Call `PrintUtils` on the main thread** (`withContext(Dispatchers.Main)`). Draw the bitmap on `Dispatchers.Default` first.
- **No result comes back.** The print calls return immediately and fail silently. Before printing, check `PrintUtils.mComFd > 0` (port open) and `PrintUtils.isCanprint` (ready, has paper), and report "Printer not ready" yourself. The SDK also broadcasts `com.qs.getmassage` (int extra `message`, `1` probably meaning out of paper).
- **Dates** on the label: format them as the operators expect (e.g. `dd.MM.yyyy`, the usual format in Bulgaria), not as ISO `2026-10-23`.

05, section 4, has a sketch of `QsPdaLabelPrinter`.

**The button**, the same on both routes:

```kotlin
// ui/components/PrintLabelButton.kt
@Composable
fun PrintLabelButton(state: NetworkState<Unit, Unit>, onClick: () -> Unit, enabled: Boolean = true) {
    SubmitButton(
        label = stringResource(R.string.print_label),
        onClick = onClick,
        enabled = enabled && state !is NetworkState.Loading,
        isLoading = state is NetworkState.Loading,
    )
}
```

- The ViewModel keeps a `printState`. Show a toast when a print fails ("Printer error, check the paper"), and keep the button enabled so the operator can retry.
- **Don't block double prints.** A second copy of a label is a legitimate need (a torn label, two crates). But disable the button *while* printing, so a double tap doesn't print twice by accident.
- If `labelPrinter.isAvailable` is false (`QsPdaHardware.available` is false, or the serial port didn't open), disable the button with a hint.

**Data for the label on each route:**

- **Summary:** `entry.toLabel()` provides everything, once the server returns the item name and quantity.
- **Details:** this route shows a looked-up batch, not a fresh stock entry, so the data comes from the Batch. `/api/resource/Batch/{id}` returns the whole document (01, finding 3), and the standard Batch fields include `manufacturing_date`, `expiry_date`, `batch_qty` (current stock in the batch) and `reference_name` (the document that created the batch, i.e. your stock entry, if `reference_doctype` is "Stock Entry"). Read them into `BatchDetails`:

  ```kotlin
  @Serializable
  data class BatchDetails(
      val name: String,
      val item: String,
      @SerialName("item_name") val itemName: String,
      val uom: String,
      @SerialName("manufacturing_date") val manufacturingDate: LocalDate? = null,
      @SerialName("expiry_date") val expiryDate: LocalDate? = null,
      @SerialName("batch_qty") val batchQuantity: Double? = null,
      @SerialName("reference_doctype") val referenceDoctype: String? = null,
      @SerialName("reference_name") val referenceName: String? = null,
  )
  ```

  *Updated (2.12): ERPNext fills `manufacturing_date`, `reference_name` and `stock_uom` on new batches itself. Read `stock_uom` instead of a custom `uom`; the class in 2.12 replaces this one.*

  **Quantity and unit on a re-printed label** (your decision): the batch's **current** amount, `batch_qty`, with the **BOM's unit**. For a finished good these fit together. ERPNext requires a finished good's BOM unit to equal its stock unit (the comment at `api.py` line 192), and `batch_qty` is counted in the stock unit. ERPNext fills the Batch's `stock_uom` itself, so the app prints `batch_qty` with `stockUom`:

  ```kotlin
  fun BatchDetails.toLabel(baseUrl: HttpUrl) = LabelData(
      batchNumber = name,
      itemName = itemName,
      quantity = "${formatDecimal(batchQuantity ?: 0.0)} ${stockUom.orEmpty()}",
      productionDate = manufacturingDate,
      expiryDate = expiryDate,
      stockEntryId = referenceName.takeIf { referenceDoctype == "Stock Entry" },   // the entry that created it
  )
  ```

  Details is only used for batches this app manufactured, so the unit always comes from a finished good's BOM, and there's nothing else to handle. One thing to be aware of:
  - **All warehouses.** `batch_qty` is the total across every warehouse. That's usually what you want on a label.

  `LabelData.productionDate` and `expiryDate` must become nullable (`LocalDate?`) for Details, since older batches may not have them. Leave the line off the label when a date is missing.

#### 5. QR code: an ERPNext link the scanner reduces to the batch number

The QR code holds the batch's **ERPNext page URL**, so it works for two kinds of reader:

- **A person with a phone** scans it and lands on the batch in ERPNext (after logging in).
- **The PDA** scans it and keeps only the batch number.

```
https://erp.example.bg/app/batch/BATCH-00123
```

`/app/batch/<name>` is the standard ERPNext desk address for a Batch.

**Building the URL** (app side, when drawing the label):

```kotlin
// From the base URL in Settings, which is already validated as https (2.5)
fun batchUrl(baseUrl: HttpUrl, batchNumber: String): String =
    baseUrl.newBuilder()
        .encodedPath("/")
        .addPathSegments("app/batch")
        .addPathSegment(batchNumber)   // encodes anything unusual in the name, e.g. "/" or spaces
        .build()
        .toString()
```

*Confirmed: the base URL in Settings is the same address people open in a browser.* So the app can build the link itself, and no server change is needed for it.

**Reading a scan:** one pure function, used by every scan path (the batch dialog on Form, and the Search and Details routes), so the rest of the app only ever sees batch numbers:

```kotlin
// hardware/ScanParser.kt
sealed interface ScannedCode {
    data class Batch(val number: String) : ScannedCode
    data class Unrecognised(val raw: String) : ScannedCode
}

fun parseScan(raw: String): ScannedCode {
    val text = raw.trim()
    if (text.isEmpty()) return ScannedCode.Unrecognised(raw)

    // Not a URL: a plain batch number (old labels, supplier labels, barcodes)
    if (!text.startsWith("http://", ignoreCase = true) && !text.startsWith("https://", ignoreCase = true)) {
        return ScannedCode.Batch(text)
    }

    // A URL: accept only .../app/batch/<name>
    val url = text.toHttpUrlOrNull() ?: return ScannedCode.Unrecognised(raw)
    val segments = url.pathSegments.filter { it.isNotEmpty() }
    return if (segments.size == 3 && segments[0] == "app" && segments[1] == "batch") {
        ScannedCode.Batch(segments[2])   // pathSegments are already URL-decoded
    } else {
        ScannedCode.Unrecognised(raw)
    }
}
```

How it fits the earlier decisions:

- **A URL that isn't a batch link** gives `Unrecognised`, for example a supplier's website QR or an ERPNext link to a different document. The dialog shows "This code is not a batch label" under the batch field, just like the other batch errors (2.3), and no lookup is made. Add `FetchBatchError.NOT_A_BATCH_LABEL`. Looking up the whole URL as a batch number would only produce a confusing "not found".
- **The host is not checked on purpose.** A label printed while Settings pointed at a different address (a test server, or an old host name) still yields the right batch number, and the ERP lookup that follows decides whether the batch exists. If you'd rather reject labels from other servers, compare `url.host` with the Settings host.
- **Plain text stays accepted.** Ingredient batches received from suppliers, and labels printed before this change, keep working.
- **Typing in the dialog doesn't go through `parseScan`.** Operators type batch numbers, not URLs.
- **The scan flow in 2.1 calls `parseScan` first:**

  ```kotlin
  OnScan(enabled = ...) { raw ->
      when (val code = parseScan(raw)) {
          is ScannedCode.Batch -> onScan(code.number)
          is ScannedCode.Unrecognised -> onUnrecognisedScan(code.raw)   // error under the batch field
      }
  }
  ```

- **Tests.** `parseScan` has no Android dependencies, so it's an easy first unit test. Cases to cover:
  - a plain number
  - a number with spaces around it
  - an `https` batch URL
  - a URL with a trailing `/`
  - a URL with an encoded name (`BATCH%2F001`)
  - a URL with a query string
  - a non-batch ERPNext URL (`/app/item/...`)
  - a non-ERPNext URL
  - an empty scan

**The SDK passes URLs through.** I checked the decoder in `PrintUtils$7`. It drops a scan only if it *contains* `##55`, `##56` or `55DN`, or *equals* `start`, `BE`, `11` or `M`, and it removes a few control characters. `:`, `/`, `.`, `?` and `-` are untouched, so a URL arrives intact.

**QR size.** A URL of around 45 characters fits in a small QR code (version 3 or 4, about 30 modules across). Drawn about 200 px wide on the 384 px label, each module is about 6 px, which is easy for both the PDA and a phone to read. Use error correction level M, and leave a quiet zone of 4 modules around it. `QRCodeWriter` adds that by default, with the `MARGIN` hint.

### 2.12 What the server work changes in the app

You've implemented [04-server-changes.md](04-server-changes.md) and noted some ERPNext behaviour along the way (recorded at the end of 04). Most of it stays on the server. These points change the app:

**1. Send `Accept-Language: bg` on every request.** Frappe chooses the language *before* it checks the API key, so the PDA user's language setting is ignored, and errors arrive in English. Add the header in [ErpServiceInterceptor.kt](../app/src/main/java/bg/butcher/pda/network/ErpServiceInterceptor.kt), next to `Authorization`. Take it from the app's own language, so English stays possible if someone runs the app in English:

```kotlin
val language = Locale.getDefault().language.takeIf { it == "bg" } ?: "en"   // languages the app ships

val request = original.newBuilder()
    .url(url)
    .header("Authorization", "token ${settings.apiKey}")
    .header("Accept-Language", language)
    .build()
```

`Locale.getDefault()` follows the device language on Android 6. If you later add an in-app language switch, use that instead. This is what makes the Bulgarian error messages (01, finding 6) actually reach operators.

**2. Batch unit: read `stock_uom`, not a custom `uom`.** You skipped B4 because ERPNext fills a new Batch's `stock_uom`, manufacturing date and Stock Entry link by itself. You also found that a BOM's unit is always the item's stock unit (ERPNext resets it on every save). So for the batches this app manufactures, `stock_uom` *is* the BOM unit, and it's the field that's reliably filled. On the Details route and its label, read that:

```kotlin
@Serializable
data class BatchDetails(
    val name: String,
    val item: String,
    @SerialName("item_name") val itemName: String,
    @SerialName("stock_uom") val stockUom: String? = null,
    @SerialName("manufacturing_date") val manufacturingDate: LocalDate? = null,
    @SerialName("expiry_date") val expiryDate: LocalDate? = null,
    @SerialName("batch_qty") val batchQuantity: Double? = null,
    @SerialName("reference_doctype") val referenceDoctype: String? = null,
    @SerialName("reference_name") val referenceName: String? = null,
    @SerialName("default_bom") val defaultBom: String? = null,   // item.default_bom alias: set = an item we make (2.14 part 2)
)
```

The batch dialog is unaffected: it already takes the unit from the matched `BomDetailsItem.uom` (2.3). `FakeErpRepository` and `FakeDataSource` need the same rename. The second point means the batch-unit decision in the table above is simplified.

**3. Whole-number units.** *Done on the server:* `must_be_whole_number` is returned, and a ±0.5 range is used for whole-number ingredients. For the app side, see 2.13.

**4. A simultaneous duplicate may fail with a 500, and that's safe.** When two identical submits arrive together, the second fails with a `QueryDeadlockError` (HTTP 500) rather than being answered from the first. As you found, no duplicate is created, and **retrying with the same `request_id` returns the original entry**. So in the app:

- **A failed submit keeps its `requestId`.** Only a new form, a BOM change or "Same BOM again" makes a new one (2.8). The Retry button then simply re-sends the same request. The response is either the entry that was already created, or a new one if the first attempt really failed.
- **Treat a 500 on submit as "unknown", not "failed".** Show "Couldn't confirm the submission. Retry to check." instead of a plain error. A retry is always safe, thanks to the `request_id`.
- **OkHttp's automatic retry** (01, finding 11) is now harmless for this endpoint. You can leave `retryOnConnectionFailure` on.

**5. Send the expiry date as `YYYY-MM-DD` in local time.** The desk page needed this fix (a full timestamp was rejected, and `toISOString()` gave the UTC day). The app is already right: `dateFromToday` ([ExpiryInput.kt:25](../app/src/main/java/bg/butcher/pda/ui/components/ExpiryInput.kt#L25)) uses `TimeZone.currentSystemDefault()`, and `LocalDate` serialises as `2026-10-23`. Keep it that way. Don't switch to `Instant` or UTC when the expiry logic moves into the ViewModel (2.10).

**6. Round quantities before sending.** The desk page found sums like `0.30000000000000004`. The app adds up batch quantities too (2.2), and it sends what operators type. Round every quantity it sends to 3 decimal places, and display them with `formatDecimal` (2.9):

```kotlin
fun Double.roundForErp(): Double = BigDecimal.valueOf(this).setScale(3, RoundingMode.HALF_UP).toDouble()
```

**7. Tolerance from the server (C1).** Done on the server side, as you've described. A Percent field can't be empty, so `0` means "use the default". The app's `tolerancePercent` default (2.2) is only used if the key is missing. **If the server sends `0`, the app uses 0%**, so make sure `get_bom_details` sends the resolved value (the BOM's own, or the default), never the raw `0`. The `get_tolerance_percent` helper in C1 does this, with `0` now treated as "not set" as you described.

### 2.13 Validating quantities by unit (whole-number units)

The server now returns `must_be_whole_number` and enforces three rules. The app should check the same rules **before** the request, so the operator sees the problem under the field instead of an error after Continue or Submit:

| Field | Flag it uses | Server rule (`api.py`) | App check |
|---|---|---|---|
| **Start: quantity** (finished good) | BOM's `uom` | `validate_fg_qty`: whole number if the BOM unit is | Whole number if the BOM's flag is set |
| **Batch dialog: quantity** | Matched ingredient's `uom` | > 0, and whole number if the ingredient unit is | Same, once the lookup has matched an ingredient |
| **Form: ingredient total** | Ingredient's `uom` | Within **±0.5** of required for whole-number units, otherwise ±tolerance % | Same, in `coverage()` (2.2) |

#### 1. One validator, with the unit's flag

Replace `validateQuantity(text)` in [QuantityInput.kt](../app/src/main/java/bg/butcher/pda/ui/components/QuantityInput.kt) with a version that knows about whole-number units, using `parseDecimal` from 2.9:

```kotlin
// util/Decimal.kt, next to parseDecimal / formatDecimal
@StringRes
fun validateQuantity(text: String, wholeNumber: Boolean): Int? {
    if (text.isBlank()) return R.string.value_required
    val value = parseDecimal(text) ?: return R.string.invalid_number
    if (value <= 0.0) return R.string.must_be_positive
    if (wholeNumber && value % 1.0 != 0.0) return R.string.whole_number_required
    return null
}
```

```xml
<!-- values/strings.xml -->
<string name="whole_number_required">Enter a whole number.</string>
<!-- values-bg/strings.xml -->
<string name="whole_number_required">Въведете цяло число.</string>
```

`"25.0"` passes, which matches the server: `flt("25.0") == cint("25.0")`. To keep the request tidy, send it as `25`.

`FormUiState.quantity` (Start) and the dialog's Select/Save check should both call this function, so the field's error and the button's enabled state can't disagree.

#### 2. `QuantityInput` takes the flag

```kotlin
@Composable
fun QuantityInput(
    value: String,
    onValueChange: (String) -> Unit,
    uom: String,
    wholeNumber: Boolean,
    modifier: Modifier = Modifier,
) {
    val error = validateQuantity(value, wholeNumber)
    OutlinedTextField(
        ...
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = if (wholeNumber) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        ),
        ...
    )
}
```

`KeyboardType.Number` hides the decimal key on most keyboards, so the operator can't type a fraction to begin with. Vendor keyboards vary, though, so the validator stays the real check.

Update the previews too: add one for a whole-number unit with `"2.5"`, to show the error.

#### 3. Start: knowing the flag before Continue

On Start, the finished good's flag is only known after Continue (it comes with `get_bom_details`). The BOM search can return it, the same way as the shelf life (2.10). `BOM.uom` is a link to the **UOM** doctype, so it can be reached through the link:

```kotlin
"fields" to buildJsonArray {
    add("name"); add("item"); add("item_name"); add("uom"); add("is_default")
    add("item.shelf_life_in_days as shelf_life_in_days")
    add("uom.must_be_whole_number as must_be_whole_number")   // from the linked UOM (confirmed)
}.toString(),
```

```kotlin
@Serializable
data class BomSearchResultItem(
    ...
    @SerialName("must_be_whole_number") val wholeNumber: Int = 0,   // Frappe Check fields arrive as 0/1
) {
    val isWholeNumber: Boolean get() = wholeNumber == 1
}
```

*Confirmed with `curl` (your key and `pda-test`'s, with only the PDA User role). It returns e.g. `{"name":"BOM-BEEF_MEATBALLS-001","uom":"Nos","shelf_life_in_days":7,"must_be_whole_number":1}`.*

On Start, pass `state.selectedItem?.isWholeNumber == true` to `QuantityInput`. The server's own check (`validate_fg_qty` in `get_bom_details`) stays as a second line of defence, and the app shows its message (01, finding 6).

Notes from your test:

- **`1`/`0`, not `true`/`false`.** The list API returns Check fields as integers, while our own methods return JSON booleans. So the model keeps it as an `Int` and converts in one place, the `isWholeNumber` getter above.

  `isDefault` is the same kind of field (01, finding 21), so give it an `isDefaultBom` getter the same way. Don't use a custom serializer that accepts both forms; it's more code for no gain.
- **Only the list call can do this.** `/api/resource/BOM/<name>` returns stored fields only, so linked values like this never appear there. Keep the BOM search as the source for Start.
- **Not for ingredients.** Frappe rejects a link lookup on child rows (`` `tabBOM Item`.uom `` can be listed, but not `uom.must_be_whole_number` through it). That's fine: ingredient flags come from `get_bom_details`, which the dialog and Form already use (parts 4 and 5).

**When the BOM changes:** a quantity typed for one BOM may become invalid for the next (`2.5` kg, then a BOM in Nos). Keep the text as it is. The field then shows the error at once, and Continue is disabled until it's fixed.

#### 4. Batch dialog: the flag comes from the matched ingredient

The ingredient, and so its unit and flag, is only known **after the lookup** (2.3). So:

```kotlin
val ingredient = ingredients.firstOrNull { it.itemCode == details?.item }

QuantityInput(
    value = quantityText,
    onValueChange = { quantityText = it; quantityTyped = true },
    uom = ingredient?.uom.orEmpty(),
    wholeNumber = ingredient?.wholeNumber == true,
)
```

- **Before the lookup**, the field accepts any positive number. Select is disabled anyway, until the lookup has matched an ingredient.
- **After the lookup**, a quantity typed earlier is checked again. If the operator typed `2.5` and the scan matches onions in Nos, the error appears straight away.
- **Rescans** (2.1) keep a typed quantity, so a new scan can make it valid or invalid. That's expected: the error follows the ingredient currently matched.
- **Edit mode** uses the ingredient of the batch being edited. It's already known, so the field is validated from the start.

**Prefill for whole-number ingredients.** The "still needed" amount (2.3) can be fractional: 2.4 Nos required, 0 chosen. Round it before filling the field, so the prefill is never invalid:

```kotlin
val remaining = ingredient.quantity - state.chosenQuantity(ingredient.itemCode)
val prefill = if (ingredient.wholeNumber) remaining.roundToLong().toDouble() else remaining
if (!quantityTyped && prefill > 0) quantityText = formatDecimal(prefill)
```

`roundToLong()` rounds 2.4 to 2 and 2.5 to 3. Both are inside the ±0.5 range, so the result is always a valid total. If the rounded amount is 0 (less than 0.5 still needed), nothing is pre-filled.

**A whole-number ingredient that needs less than 0.5 can't be submitted at all.** 0 is rejected, and 1 is more than 0.5 away (04, implementation notes, "Desk page"). That means the run is too small for the BOM: on the test BOM, fewer than 25 meatballs (0.12 onions each). Check this **on Start**, as soon as the BOM details arrive, and send the operator back to the quantity with a clear message:

```kotlin
// FormUiState: ingredients no amount can satisfy
val unworkableIngredients: List<BomDetailsItem>
    get() = bom?.items.orEmpty().filter { it.wholeNumber && wholeNumberChoices(it).isEmpty() }
```

```xml
<string name="quantity_too_small">The quantity is too small for %1$s (needs %2$s %3$s). Increase the quantity.</string>
```

- **When to check.** The BOM details only arrive after Continue, so run the check when the fetch succeeds. If any ingredient is unworkable, stay on Start and show the message under the quantity field, instead of opening Form.
- **How it's found.** `wholeNumberChoices` (part 5) starts at 1, so it returns an empty range for these ingredients. For a requirement of 0.12, the allowed range is −0.38 to 0.62, and the only whole number in it is 0.
- **Why it matters.** It keeps the operator from reaching a Form screen where Submit can never be enabled.

#### 5. Form: the tolerance rule for whole-number units

Update `allowedRange` from 2.2 to match the server: **±0.5** for whole-number units, instead of the percentage:

```kotlin
fun allowedRange(ingredient: BomDetailsItem): ClosedFloatingPointRange<Double> {
    val margin = if (ingredient.wholeNumber) {
        0.5                                    // same as api.py: a percentage can't work for small whole numbers
    } else {
        ingredient.quantity * (bom?.tolerancePercent ?: DEFAULT_TOLERANCE_PERCENT) / 100
    }
    return (ingredient.quantity - margin)..(ingredient.quantity + margin)
}
```

`coverage()` and `canSubmit` don't change. They use `allowedRange`, and the `EPSILON` of 0.0005 matches the server's `+ 0.0005`.

For the table (2.2), show the whole-number range as whole numbers. A required 2.4 Nos shows `0 / 2.4 Nos · use 2`, not `at least 1.9`. When the required amount is exactly x.5, two whole numbers fit (2.5 allows 2 or 3), so show `use 2–3`:

```kotlin
fun wholeNumberChoices(ingredient: BomDetailsItem): LongRange {
    val range = allowedRange(ingredient)
    // At least 1: a batch quantity of 0 is never allowed (04, B3)
    return maxOf(1L, ceil(range.start - EPSILON).toLong())..floor(range.endInclusive + EPSILON).toLong()
}
```

#### 6. Models, label and fake

```kotlin
@Serializable
data class BomDetailsItem(
    ...
    @SerialName("must_be_whole_number") val wholeNumber: Boolean = false,
)

@Serializable
data class BomDetails(
    ...
    @SerialName("must_be_whole_number") val wholeNumber: Boolean = false,
)

@Serializable
data class ManufactureEntry(
    ...
    @SerialName("must_be_whole_number") val wholeNumber: Boolean = false,
)
```

- **Defaults.** The `false` defaults keep old responses and the fake decoding.
- **JSON types.** `get_bom_details` and `create_manufacture_entry` send real JSON booleans (`in whole_number_uoms` is a Python `bool`), so these three are `Boolean`. Only the BOM search sends `0`/`1` (part 3).
- **Label.** Print whole-number quantities without decimals (`25 Nos`, not `25.000 Nos`). `formatDecimal` (2.9) already strips trailing zeros, so `ManufactureEntry.wholeNumber` mostly matters if you ever choose a fixed number of decimals.
- **`FakeErpRepository`.** Make some ingredients whole-number, with a `Nos` unit and whole quantities, so the rules can be tried without a server.

#### 7. Tests

`validateQuantity`, `allowedRange` and `wholeNumberChoices` are pure functions, so they're quick unit tests. Cases to cover:

- `"25"`, `"25.0"`, `"2.5"`, `"2,5"`, `"0"`, `"-1"` and `""`, each with and without the whole-number flag
- a required 2.4 Nos accepts 2 and rejects 3
- a required 2.5 Nos accepts 2 and 3
- a required 0.12 Nos gives no choices, so the run is too small (`unworkableIngredients`)
- a required 5 kg at 5% accepts 4.75 and rejects 4.74

### 2.14 The Search and Details pages

Your decisions:

| Topic | Decision |
|---|---|
| Finding a batch | One field for both: a scan or Enter looks up an exact batch number; typing lists batches whose number **or** product name contains the text |
| Search filters | Behind a ⚙ button, in a bottom sheet: sort by production date (default, newest first), expiry date or product name, a direction, "Produced in the last N days" (whole days), and "Hide expired batches" (off by default). Not remembered between visits. Scans ignore the filters |
| Empty search field | Lists the latest batches straight away (a normal server request, nothing stored locally) |
| Manufactured only | Batches of items with a default BOM: `["item.default_bom","is","set"]`. Covers the PDA, the desk page and web-UI corrections, with no custom field. `reference_doctype` can't be used, because ERPNext sets it on raw-material batches too |
| Search day limit | "Produced in the last N days" starts at **30** |
| API key in Settings | **One field**, as now: the operator enters `api_key:api_secret` in it |
| Scan on Search | Look up, then go straight to Details |
| Scan on Details | Replace the batch shown, so several labels can be checked in a row |
| Details shows | The label fields, plus **expiry status** and the **ingredients used** (the ingredient batches the producing Stock Entry consumed) |
| Details is for | Batches this app manufactured only, as decided earlier |

So the scanner now listens on **three** routes: Form, Search and Details (`OnScan` in 2.1).

#### 1. Navigation

Search and Details form a second small flow, next to `manufacture`:

```kotlin
navigation(route = "batches", startDestination = MainScreen.Search.name) {
    composable(MainScreen.Search.name) { ... }
    composable(
        route = "${MainScreen.Details.name}/{batch}",
        arguments = listOf(navArgument("batch") { type = NavType.StringType }),
    ) { ... }
}

// from Search, after a lookup or a tap on a result
navController.navigate("${MainScreen.Details.name}/${Uri.encode(batchNumber)}") { launchSingleTop = true }
```

- **The batch number goes in the route.** The Details screen then reloads by itself after process death, and doesn't depend on a ViewModel that may be gone.
- **`MainScreen.valueOf(route)` breaks with arguments.** Today's top bar does `MainScreen.valueOf(backStackEntry.destination.route)`, which throws for `Details/{batch}` (02, finding 17). Either move to type-safe routes now, with a `@Serializable data class Details(val batch: String)` (Navigation 2.9 supports it), or match on `route.substringBefore('/')`. Type-safe routes are the cleaner fix, and this is a good moment for it, because it's the first route with an argument.
- **A scan on Details replaces the batch** without stacking screens. Navigate with `popUpTo(Details) { inclusive = true }`, so Back from any Details goes to Search, however many labels were scanned.
- **`Uri.encode`** keeps a batch number with `/` or spaces from breaking the route.
- **Each screen gets its own ViewModel.** `SearchViewModel` and `DetailsViewModel` (keyed by the batch argument) are small and independent of `FormViewModel`. Don't share one ViewModel across the graph like the manufacture flow; there's nothing to carry between the two pages.

#### 2. Search page

```
┌──────────────────────────────┐
│ Find batch                   │
│ ┌────────────────────────┐ ⚙ │
│ │ 🔍 Batch no. or product│ ● │   ● = filters differ from the defaults
│ └────────────────────────┘   │
│  Last 30 days · Produced ↓   │   ← one-line summary of the current filters
│                              │
│  Sausage          BATCH-0123 │
│  20 kg · prod. 23.10 · exp. 30.10.2026 │
│  ────────────────────────────│
│  Sausage          BATCH-0119 │
│  18 kg · prod. 21.10 · exp. 28.10.2026 ⚠ │
│          [ Load more ]       │
└──────────────────────────────┘
```

**One field, for both batch numbers and product names.** The operator never has to say which one they mean. The app can tell from how the text arrives:

| Input | What the app does |
|---|---|
| **Scan** | The text is always a batch number (or a label URL, via `parseScan`, 2.11 part 5). Look it up exactly with `getBatch`. Found: go to Details. Not found: stay, with the error under the field ("Batch not found", or "This code is not a batch label"). |
| **Typing** | After the usual 300 ms pause, list batches whose **batch number or product name** contains the text, using the current filters. The operator taps a row to open Details. Typing never jumps away by itself, because a half-typed batch number could match a different batch. |
| **Enter** (keyboard Search key) | Look the text up as an exact batch number. If it exists, go to Details. Otherwise, keep showing the list. |

So typing "beef" lists every beef batch, typing "0123" lists batches whose number contains 0123, and typing a full batch number then Enter opens it directly. The placeholder "Batch no. or product" tells the operator both work. If operators find the mixed list confusing, a "Batch / Product" toggle is an easy change later.

**Scans and exact lookups ignore the filters.** A scanned label always opens its batch, even if it's expired or older than the date limit. The filters only shape the typed-text list.

**Advanced filters, in a bottom sheet.** The ⚙ button next to the field opens a `ModalBottomSheet`:

```
┌──────────────────────────────┐
│ Search filters               │
│                              │
│ Sort by                      │
│  (•) Production date         │
│  ( ) Expiry date             │
│  ( ) Product name            │
│ Order   [ Newest first  ▾ ]  │
│                              │
│ Produced in the last         │
│  [ 30 ] days                 │
│                              │
│ [ ] Hide expired batches     │
│                              │
│ [ Reset ]          [ Done ]  │
└──────────────────────────────┘
```

| Filter | Default | Request |
|---|---|---|
| **Sort by** | Production date | `order_by` = `manufacturing_date`, `expiry_date` or the product name |
| **Order** | Newest first (production), soonest first (expiry), A → Z (product name) | `desc` / `asc`. Picking a sort resets the order to that sort's default. The labels follow the sort: "Newest / Oldest first", "Soonest / Latest first", "A → Z / Z → A" |
| **Produced in the last N days** | 30 | `["manufacturing_date", ">=", today − N]`. An empty field means no limit |
| **Hide expired batches** | Off | `["expiry_date", ">=", today]` when on |

- **Why a bottom sheet:** on a small PDA screen it opens from the bottom, near the thumb, and leaves the results visible above it. It also closes with Back or a swipe, so no extra button is needed. `ModalBottomSheet` is in Material 3, which the app already uses.
- **The day limit is a whole number of days** (your decision), not a date. Use the same `QuantityInput`-style field with `wholeNumber = true` and the number keyboard (2.13), with "days" as the suffix. 0 means "today only". Empty means no limit. The request converts it to a date, `today − N`, using the device's local date (2.12 point 5), so it always means "the last N days" from the moment of the search.
- **Hide expired** compares `expiry_date` with today. Off by default, because most batches will be historic and expired: hiding them by default would make older lookups look like missing data. It uses the same "expired" rule as the banner on Details (part 3): expiring *today* still counts as not expired.
- **When filters apply:** changes take effect when the sheet closes (Done, Back or swipe), with one new search. They don't re-search on every tap.
- **Reset** returns every filter to its default.
- **The ● dot on the ⚙ button** shows when any filter differs from the default. The one-line summary under the field ("Last 30 days · Expires ↑ · Hiding expired") tells the operator what's active, without opening the sheet. Tapping the summary opens the sheet too.
- **No memory between visits** (your decision). Keep the filters in the `SearchViewModel`'s `SavedStateHandle`, so they survive rotation and returning from Details, and reset to the defaults the next time Search is opened from Home.
- **A day limit by default:** 30 is a suggestion. It keeps the default list short and recent, and makes the "list without typing" below cheap. See the question in section 4.

```kotlin
enum class BatchSort(val field: String, val defaultDescending: Boolean) {
    PRODUCED("manufacturing_date", defaultDescending = true),
    EXPIRES("expiry_date", defaultDescending = false),
    PRODUCT("item_name", defaultDescending = false),   // the `item.item_name as item_name` alias in fields
}

data class BatchFilters(
    val sort: BatchSort = BatchSort.PRODUCED,
    val descending: Boolean = BatchSort.PRODUCED.defaultDescending,
    val producedWithinDaysText: String = DEFAULT_DAYS.toString(),   // text, so the field can be empty
    val hideExpired: Boolean = false,
) {
    val producedWithinDays: Int? get() = producedWithinDaysText.trim().toIntOrNull()?.takeIf { it >= 0 }
    val isDefault: Boolean get() = this == BatchFilters()

    companion object { const val DEFAULT_DAYS = 30 }
}

data class SearchUiState(
    val query: String = "",
    val filters: BatchFilters = BatchFilters(),
    val results: NetworkState<List<BatchDetails>, ErpError> = NetworkState.Idle,
    val canLoadMore: Boolean = false,
    val lookupError: ErpError? = null,   // shown under the field after a failed scan / Enter
)
```

`producedWithinDaysText` is a `String`, so the field can be empty and keep what's typed. A non-number shows an error in the sheet, and Done stays enabled but keeps the previous value.

**Listing without typing.** No local database is needed (your condition): with an empty field, run the same request without the text filter. Search then opens on the latest batches straight away, newest first, within the day limit. "The batch we made this morning" is one tap away. It's a normal server request, re-run each time Search opens, so nothing is stored on the device.

**Paging.** Frappe returns **20 rows** unless `limit_page_length` is set (you found this). Ask for 30 at a time, with `limit_start` for the next page:

- **First page:** `limit_start = 0`, `limit_page_length = 30`.
- **"Load more"** at the bottom of the list fetches the next 30 (`limit_start = 30`, then 60, …) and appends them.
- **`canLoadMore`** is true when the last page came back full (30 rows). A shorter page means the end. Frappe doesn't return a total count, so a full last page shows "Load more" once more, and that request returns nothing new.
- **Any change starts again at page one:** new text, or new filters.
- **A button rather than infinite scroll:** it's easier to control with gloves, and predictable on a slow connection. Infinite scroll is a later change if wanted.

**Manufactured batches only: `item.default_bom` is set.** `reference_doctype = Stock Entry` doesn't single these out: ERPNext links *every* new batch to the entry that created it, including raw-material batches from a Material Receipt (e.g. BEEF.0001 from MAT-STE-2026-00001). Instead, filter on the **item**, with your filter:

```kotlin
add(buildJsonArray { add("item.default_bom"); add("is"); add("set") })   // the item is something we make
```

- **What it means:** "a batch of an item that has a default BOM". That covers finished goods and sub-assemblies, whoever made the batch: the PDA, the desk page, or a correction in the web UI (your point: these aren't the only places batches are made). Raw materials have no BOM, so they're left out.
- **No custom field, no patch.** It uses the same link syntax already confirmed in `fields` and `filters` (`item.item_name`), so nothing changes on the server. This replaces the earlier `pda_produced` suggestion.
- **Edge cases:**
  - An item whose default BOM is removed later drops out of the search. Its batches can still be found by scanning or by exact batch number (Enter), which ignore the filters.
  - An item with a BOM that is also bought in (rare in a butcher's) would show its bought batches too. That's harmless: the labels describe the batch either way.
- **Details uses the same rule.** A single-document request (`/api/resource/Batch/<name>`) returns only the Batch's own stored fields, so `item.default_bom` isn't in it (you found the same with the BOM). Two ways to get it:
  - **Recommended:** fetch the Batch for Details with a one-row **list** request, with `filters=[["name","=","<batch>"]]` and the same `fields` as the search plus `item.default_bom as default_bom`. Details and the Search rows then share one request shape and one model. Ask for the Stock Entry link (`reference_name`) in `fields` as well.
  - Or read the Item: `/api/resource/Item/<item>` returns `default_bom`. That's an extra request.

  Then `BatchDetails.defaultBom != null` means "made here". Show the Print button; otherwise show "This item isn't produced here".

  The batch dialog's lookup after a scan can keep using the single-document request. It only needs `item`, which is a stored field.

**The request:**

```kotlin
// NetworkErpRepository.searchBatches(query, filters, start)
val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

"fields" to buildJsonArray {
    add("name"); add("item"); add("item.item_name as item_name")
    add("batch_qty"); add("stock_uom"); add("expiry_date"); add("manufacturing_date")
    add("item.default_bom as default_bom"); add("reference_name")
}.toString(),
"filters" to buildJsonArray {
    add(buildJsonArray { add("item.default_bom"); add("is"); add("set") })              // items we make (have a default BOM)
    filters.producedWithinDays?.let { days ->
        add(buildJsonArray { add("manufacturing_date"); add(">="); add(today.minus(days, DateTimeUnit.DAY).toString()) })
    }
    if (filters.hideExpired) {
        add(buildJsonArray { add("expiry_date"); add(">="); add(today.toString()) })
    }
}.toString(),
// Only when there's text: match the batch number OR the product name
query.takeIf { it.isNotBlank() }?.let { q ->
    "or_filters" to buildJsonArray {
        add(buildJsonArray { add("name"); add("like"); add("%${escapeLike(q)}%") })
        add(buildJsonArray { add("item.item_name"); add("like"); add("%${escapeLike(q)}%") })
    }.toString()
},
"order_by" to "${filters.sort.field} ${if (filters.descending) "desc" else "asc"}, name desc",
"limit_start" to start.toString(),
"limit_page_length" to PAGE_SIZE.toString(),   // 30; Frappe's default is 20
```

- **`filters` and `or_filters`:** Frappe combines them as *all of `filters`* AND *any of `or_filters`*. So the result is "an item we make, within the date limit, (not expired), and (batch number matches, or product name matches)".
- **`order_by` is built from the enum, never from free text.** It's sent as raw SQL ordering, so only the three fixed fields can ever end up in it.
- **Sorting by product name** is most useful with a partial name: typing "meat" matches "Meatballs (Beef)", "Meatballs (Pork)" and "Minced meat", and sorting A → Z groups each product's batches together. Within one product, the tie-break (`name desc`) puts the newest batch numbers first. If you'd rather have the newest *production* first within a product, use `item_name asc, manufacturing_date desc, name desc` for this sort.
- **How it sorts:** *confirmed with `curl`.* `order_by=item_name asc, name desc` sorts on the `item.item_name as item_name` alias, and `item.item_name asc` works too. `` `tabItem`.item_name `` does **not** ("Unknown column"). Use the alias, as `BatchSort.PRODUCT` does.
- **Collation:** MariaDB's case-insensitive collation sorts "beef" and "Beef" together. Cyrillic names sort by the Bulgarian alphabet only if the database uses a Unicode collation such as `utf8mb4_unicode_ci`, which is Frappe's default.
- **Ties** are ordered by `name desc`, so the order is stable and doesn't shuffle between pages. That's important now with paging: otherwise rows could repeat or go missing between "Load more" pages.
- **Batches without a date** (`NULL`) sort first when ascending and last when descending, in MariaDB. A date filter (the day limit, or "hide expired") also leaves them out, because `NULL >= date` is false. Since ERPNext fills `manufacturing_date` and the app always sends an expiry, this only affects old test data.
- **All confirmed with `curl` as `pda-test`** (04, "For the Search and Details pages"):
  - `item.item_name like ...` in `filters` and in `or_filters`, not case-sensitive.
  - The date filters and `order_by`.
  - Paging with `limit_start`: consecutive pages with no gaps or repeats.
  - BEEF.0001 (a raw material) is left out by `item.default_bom is set`.
  - The one-row Details request.
- **Stock levels play no part in the search** (your decision). Used-up batches are listed like any other. The quantity is still shown in each row.
- **Use the `Batch` model from Details** (part 3) for the rows, with `defaultBom` added. It's the same doctype and the same request shape, so the decoding is shared.
- **Each row shows both dates,** so the current sort is easy to read at a glance. The ⚠ comes from `expiryStatus` (part 3).
- **No results, network error:** "No results" (plus "Try fewer filters" when the filters aren't the defaults), or the error with Retry.

**Scanner on Search:** `OnScan { raw -> viewModel.onScan(raw) }`. The SDK's trigger is active while Search is visible, and turned off when it isn't (2.11 part 3). While the filter sheet is open, a scan still works: it closes the sheet and goes to Details.

#### 3. Details page

```
┌──────────────────────────────┐
│ ← Details                    │
│ Sausage                      │
│ BATCH-0123                   │
│ ┌──────────────────────────┐ │
│ │ ⚠ Expires tomorrow       │ │
│ │   30.10.2026             │ │
│ └──────────────────────────┘ │
│ Quantity    20 kg            │
│ Produced    23.10.2026       │
│ Stock entry MAT-STE-0042     │
│                              │
│ Ingredients used             │
│  BATCH-0101          15 kg   │
│  BATCH-0098          0.4 kg  │
│  BATCH-0087          60 Nos  │
│                              │
│      [ Print label ]         │
└──────────────────────────────┘
```

**Loading.** `DetailsViewModel(batch)` makes two requests: first the Batch, then the Stock Entry for the ingredients. The Batch fields appear as soon as the first returns; the ingredient list shows its own spinner. If only the second fails, the page still shows the batch and offers Retry for the ingredients alone.

**The batch:** the class from 2.12 covers it: name, item, item name, `stock_uom`, `batch_qty`, `manufacturing_date`, `expiry_date`, the reference to the Stock Entry, and `default_bom`.

**If it isn't a manufactured batch** (`defaultBom == null`, part 2), show "This item isn't produced here" and hide the Print button. That way a scanned supplier label doesn't print a misleading label. (`reference_doctype` can't be used for this: ERPNext sets it on raw-material batches too.)

**Loading the batch for Details** uses a one-row list request (`filters=[["name","=",batch]]`), not the single-document request, so that `item.default_bom` and `item.item_name` come back through the links (part 2). It also returns `reference_name`, the Stock Entry for the ingredients.

**Expiry status.** Compare `expiry_date` with today's local date (2.12 point 5):

| Status | Rule | Display |
|---|---|---|
| Expired | expiry < today | Red banner: "Expired on 21.10.2026" |
| Expires today | expiry = today | Red banner: "Expires today" |
| Expires soon | expiry ≤ today + N days | Amber banner: "Expires tomorrow" / "Expires in 2 days" |
| OK | later | No banner, only the date in the list |
| Unknown | no expiry date | Grey: "No expiry date" |

```kotlin
enum class ExpiryStatus { EXPIRED, TODAY, SOON, OK, UNKNOWN }

fun expiryStatus(expiry: LocalDate?, today: LocalDate, soonDays: Int = 1): ExpiryStatus = when {
    expiry == null -> ExpiryStatus.UNKNOWN
    expiry < today -> ExpiryStatus.EXPIRED
    expiry == today -> ExpiryStatus.TODAY
    expiry <= today.plus(soonDays, DateTimeUnit.DAY) -> ExpiryStatus.SOON
    else -> ExpiryStatus.OK
}
```

- **Pass `today` in.** That keeps the function testable. The screen calls it with `Clock.System.todayIn(TimeZone.currentSystemDefault())`.
- **Search uses it too:** the same function drives the ⚠ in the Search list. "Soon" means **1 day** (your decision), which is the default above.
- **Wording:** "Expires in 2 days" needs a `<plurals>` resource. Bulgarian uses *one* / *other* (03, finding 9).

**Ingredients used.** The batch's `reference_name` is the Stock Entry that produced it. A single-document request returns the whole entry, **including its `items` child table**:

```
GET /api/resource/Stock Entry/MAT-STE-0042
```

```kotlin
@Serializable
data class StockEntryDetails(
    val name: String,
    @SerialName("posting_date") val postingDate: LocalDate,
    val items: List<StockEntryRow>,
)

@Serializable
data class StockEntryRow(
    @SerialName("item_code") val itemCode: String,
    @SerialName("item_name") val itemName: String,
    val qty: Double,
    val uom: String,
    @SerialName("batch_no") val batchNo: String? = null,
    @SerialName("is_finished_item") val isFinishedItem: Int = 0,
)

val ingredients = entry.items.filter { it.isFinishedItem == 0 }
```

- **A plain list** (your decision): one row per ingredient batch, with its **batch number and quantity** (with unit). Rows aren't tappable. Showing the item name as well is optional; the batch number is what matters here.
- **Permissions:** the PDA User role has Read on **Stock Entry**. *Confirmed: `pda-test` gets the document with `items`.*
- **`ignoreUnknownKeys`** (01, finding 7) matters here, because the Stock Entry document has dozens of fields the model doesn't list.

**Scan on Details:** `OnScan { raw -> ... }` runs `parseScan`, and on a batch number replaces the current Details (part 1). An unrecognised code shows a toast ("This code is not a batch label") and keeps the current batch on screen.

**Print label:** as in 2.11 part 4, using `BatchDetails.toLabel()`. The quantity is the current `batch_qty` with `stock_uom`.

#### 5. Permissions: the PDA User role *(done)*

Testing as `pda-test` showed that ERPNext's default roles don't let the PDA read Batches:
- **Lists** returned only `name`; every other field was silently dropped.
- **A single Batch** failed with a `PermissionError`.

That would have broken the batch dialog's lookup, Search and Details. Only Submit was unaffected, since the server reads batches itself.

**Done on the server (04, A7):** a dedicated **PDA User** role, set up by `my_app` on `bench migrate`. Each PDA user gets only that role.

- **Read** on Batch, Item, BOM, UOM and Warehouse.
- **Create / Write / Submit** on Stock Entry and on Serial and Batch Bundle. Submitting through the API needs Write.
- **A `has_permission` hook** limits PDA-only users to Manufacture entries from a BOM, so a PDA key can't create a Material Receipt, for example.
- **Every check passes as `pda-test`.** That includes reading Batch lists, single Batches and Stock Entries, and being refused edits, cancels, deletes and other doctypes.

Two practical points for the app:

- **PDA users are Website Users,** because the role has no desk access. API keys work, but a PDA user can't log in to the desk. That's expected; nobody should use those accounts in a browser.
- **Assigning the role is a manual step** when a new PDA user is created. If a new PDA is set up without it, the app sees the same symptoms as before: missing fields and `PermissionError`.

**In the app**, two things make this kind of problem obvious instead of confusing:

- **A `PermissionError` (HTTP 403) gets its own message**, e.g. "This device isn't allowed to read batches. Ask an administrator to check its role." It shouldn't show as "Batch not found" or "Network error" (03, B7 already suggests a separate message for 401/403).
- **Silently dropped fields show up as decode errors.** If a Batch row arrives with only `name`, `BatchDetails` fails to decode because `item` is missing. That's good: it fails loudly. Map a `SerializationException` to "Unexpected response from the server" in the error handling (Phase 1.3), not to "No results", so it isn't mistaken for an empty search.



```kotlin
interface ErpRepository {
    ...
    suspend fun searchBatches(query: String): Result<List<BatchDetails>>
    suspend fun getStockEntry(name: String): Result<StockEntryDetails>
}
```

- **Shared `BatchDetails`.** The Search list and the Details page use the same class. In list results, `item_name` comes through the link alias.
- **`FakeErpRepository`** gets fake batches with a mix of expiry dates (expired, today, tomorrow, next week) and a fake Stock Entry with several ingredient batches. Then every state can be seen on the emulator.
- **Tests:**
  - `expiryStatus` with each boundary.
  - MockWebServer tests for `searchBatches` (checking the filters sent) and `getStockEntry` (decoding a real response, child rows included).
  - `SearchViewModel`: a scan goes to Details, typing doesn't navigate, Enter on an exact match navigates.

---

## 3. Smaller themes (details in the reports)

- **Numbers:** see 2.9. Use one `parseDecimal` / `formatDecimal` pair everywhere. (03)
- **Expiry days** are validated as a decimal but used as an `Int`, so `1.5` looks valid and is silently dropped. (03)
- **Bulgarian strings:** use your terms.
  - `bom_label` = "СНМ". The file currently has `CHM` typed in **Latin** letters. It looks the same, but it may render in a different font from the Cyrillic text around it, and a screen reader spells it out as English letters. Retype it as Cyrillic С-Н-М (U+0421 U+041D U+041C).
  - `explode_label` = "Разгръщане" (currently "Експлодирам", "I explode").
  - `search_label` is still English, and "Сума" means a sum of money; "Количество" fits. (03)
- **Glove use:** small text buttons, 11sp labels, swipe-only edit/delete, and checkbox labels that can't be tapped. (03)
- **Settings:** the initial load can overwrite what the user is typing. The key is shown in plain text, and isn't trimmed. *One field stays (your decision):* label it "API key and secret", with the hint `api_key:api_secret`, and check that the value contains exactly one `:` with text on both sides, so a pasted key without its secret is caught before the first request. Since there's one key per device, it should also be excluded from backups. Otherwise restoring a backup onto a new PDA would copy another device's key. (01, 02)
- **Build:** no minify or signing for release, empty keep rules, and duplicate or unused version-catalog entries. (01)
- **Tidy-up:** the `seachBom` typo, the stale `AssemblyViewModel` log tag, unused imports, `JobSlot`/`NetworkState` in the `network` package, and mixed error types (`String?` vs enum). (01, 02)

---

## 4. Open questions still unanswered

1. **Scan broadcast on the device:** does the scan text arrive on `com.qs.scancode` (what the JAR sends) or on `ismart.intent.scandown` (what the docs say)? A test receiver for both on the real PDA will tell (2.11, part 3). The vendor questions in 05, section 9, cover the rest.
