# Business rules

Most rules are pure extensions on `FormUiState` in `ui/screens/FormRules.kt`, unit-tested in `FormRulesTest` and `FormViewModelTest`. The server checks the same rules again ([server-api.md](server-api.md)), so the app's checks are there to help the operator and the server's to enforce them.

## Inputs and gates

- **`quantity`:** `parseDecimal(quantityText)`, kept only if it's above 0.
- **`expiryDays`:** a whole number of days above 0.
- **`canFetch`:** a BOM is selected, and the quantity is valid for its unit (`validateQuantity(text, selectedItem.isWholeNumber)`). This is what the BOM details request needs.
- **`canContinue`:** `canFetch`, plus valid expiry days. This is Start's Continue button.
- **`canSubmit`:** valid expiry days, every ingredient's coverage is `OK`, and the run isn't submitting or already submitted.

`validateQuantity` returns `value_required`, `invalid_number`, `must_be_positive` or `whole_number_required`. `"25.0"` counts as a whole number.

## Whole-number units

- **Which units:** those with **Must Be Whole Number** set on their ERPNext UOM, e.g. Nos.
- **Quantity field:** for these units it uses the number keyboard, and the validator requires a whole number.
- **The flags come from:**
  - **the finished good:** the BOM search (`uom.must_be_whole_number`);
  - **each ingredient:** the BOM details (`must_be_whole_number`).

## Tolerance and coverage

- **Constants:** `EPSILON = 0.0005`, which matches the server's `+ 0.0005`, and `WHOLE_NUMBER_MARGIN = 0.5`.
- **`allowedRange(ingredient)`** is `required ± margin`:
  - for whole-number ingredients the margin is 0.5;
  - otherwise it's `required × tolerancePercent / 100`.
  - `tolerancePercent` comes from `BomDetails`. It falls back to `DEFAULT_TOLERANCE_PERCENT = 5.0` only when the key is missing. The server always sends the resolved value, and a `0` from it would mean 0%.
- **`coverage(ingredient)`**, where "chosen" is the sum of that ingredient's batch quantities:
  - `MISSING` when chosen is 0;
  - `UNDER` when chosen is below `start − EPSILON`;
  - `OVER` when chosen is above `end + EPSILON`;
  - `OK` otherwise.
- **`wholeNumberChoices`** is `max(1, ceil(start − ε)) .. floor(end + ε)`. It never includes 0. The table shows it as "use 2" or "use 2–3" instead of the limits.
- **`unworkableIngredients`** are the whole-number ingredients whose choices are empty, because they need less than 0.5.
  - **On Start:** Continue doesn't go to Form while any exist. Start shows "The quantity is too small for …" under the quantity field (`QuantityInput(extraError = …)`).
  - **On the server:** this run can't be submitted at all.
- **Table text:** OK shows a ✓. Otherwise it shows "at least X" (`coverage_at_least`) or "at most X" (`coverage_at_most`), or the whole-number "use …" hint.

## Fetching the BOM details

- **Continue (`fetchBomDetails()`):**
  - If the details are already loaded, it goes straight to Form, unless the run is too small.
  - If the inputs are incomplete, it shows a toast.
  - Otherwise it loads the details; a failure shows a toast.
- **`reloadBomDetails()`** loads from Form after a restore, with no navigation and no toasts.
- **A change to the inputs** cancels a fetch in flight.

## Batch lookup (`FormViewModel.lookupBatch`)

The checks run in this order, and the first that fails gives the error:
1. **Empty input:** `InvalidInput`.
2. **Already on the form:** `AlreadyAdded`. This is checked before any network request.
3. **`getBatch(number)`:** a 404 gives `NotFound`; any other failure gives `Failed(ErpError)`.
4. **Already on the form under the server's name:** `AlreadyAdded` again, checked with the server's spelling of the name.
5. **Not an ingredient:** no BOM line has `itemCode == batch.item`, so `NotInRecipe(batch)`.
6. **Success:** `BatchMatch(batch, ingredient, prefillQuantity(ingredient))`.

How the lookup behaves:
- **Case:** batch names match ignoring case, as Frappe does (`beef.0001` finds `BEEF.0001`), so `hasBatch` uses `ignoreCase`.
- **Prefill:** `prefillQuantity` is the amount still needed, i.e. required minus chosen. Whole-number ingredients round it to a whole number, others round it for the ERP. It's null when nothing more is needed. It's only filled in if the operator hasn't typed a quantity.
- **Unit:** the dialog shows the matched ingredient's name, and takes the unit and whole-number flag from the matched `BomDetailsItem`.
- **Debounce:** typing waits 300 ms before the lookup; scans don't wait.
- **Edit mode:** the batch number is read-only and there's no lookup. Save needs a valid quantity that differs from the original.

`addBatch` and `updateBatch` repeat the important checks and return a `BatchChangeError?`: `INVALID_INPUT`, `NOT_IN_RECIPE`, `ALREADY_ADDED` (add only) or `NOT_FOUND`. They're a backstop; the dialog's lookup has already run the same checks.

## Request IDs and submitting

Every submit carries a `request_id` (a UUID in `FormUiState.requestId`). The server returns the original entry for a repeated ID instead of creating a second one.

- **A new ID** is made when:
  - the form is created;
  - the BOM changes (by name);
  - the quantity's *value* changes. `"10"` to `"10.0"` is the same run;
  - Explode changes;
  - "Same BOM again" is pressed (`startAgain()`).

  Any of these except "Same BOM again" also clears the loaded details, batches and submit state. Changing the expiry or "Is finished" doesn't renew the ID.
- **A failed submit keeps its ID.**
  - **Network error or 5xx** (`ErpError.isOutcomeUnknown`): the app shows "Couldn't confirm the submission. Retry to check.", and the button reads Retry. Retry re-sends the same ID, so it's always safe.
  - **Other failures** show the server's message.
- **After success:** the entry is saved, `canSubmit` is false, and the app moves to Summary.
- **`startAgain()`** keeps the Start inputs and the loaded BOM, and clears the batches, submit and print state.

## Expiry

- **Default:** picking a BOM fills expiry days from the item's shelf life (`shelf_life_in_days`). If there's no shelf life, the field stays empty and must be filled in.
- **Hint:** once the operator types over the default, a "Default from product: N days" hint appears.
- **Submitted date:** `dateFromToday(expiryDays)`, a local calendar date.
- **`expiryStatus(expiry, today, soonDays = 1)`** in `model/ExpiryStatus.kt`:
  - `UNKNOWN` when there's no date;
  - `EXPIRED` when the date is before today;
  - `TODAY` when it's today, which still counts as not expired;
  - `SOON` when it's tomorrow at the latest;
  - `OK` otherwise.
- **Display:**
  - **Search rows** show ⚠ for EXPIRED, TODAY and SOON.
  - **The Details banner** is red for EXPIRED and TODAY, amber for SOON, grey for UNKNOWN, and hidden for OK.

## Search filters (`model/BatchFilters.kt`)

- **Defaults:**
  - sort by production date, newest first;
  - produced within 30 days (`DEFAULT_DAYS`);
  - "hide expired" off.
- **Sorts:** picking one also picks its usual direction: production date newest first, expiry soonest first, product A→Z. The order labels follow the sort ("Newest / Oldest first" and so on).
- **The day limit** is text, so the field can be empty, which means no limit. 0 means today only. A value that isn't a whole number keeps the previous one when the sheet closes.
- **The request** always filters to items with a default BOM, i.e. made here. The day limit and "hide expired" add date filters, and the query text matches the batch number or the product name.
  - **Order:** `order_by` is `<sort field> <dir>, name desc`. It's built only from the `BatchSort` enum, because Frappe inserts it into the SQL as raw text.
  - **Paging:** 30 rows a page (`BATCH_PAGE_SIZE`). "Load more" shows while the last page was full.
  - **Changes:** any change of text or filters starts again at page one and cancels a "Load more" in flight.
- **Enter vs a scan:** Enter on text that isn't a batch number keeps the list, with no error. A scan of an unknown batch shows "not found"; a code that isn't a label shows "not a batch label".

## BOM search

- **The request:** active, submitted BOMs whose item name contains the text (LIKE with escaping), with a fixed 50-row limit (`BOM_SEARCH_LIMIT`).
- **Sort:** `item_name desc`. This is still an open question ([status.md](status.md)).
- **The star** marks the item's default BOM.
