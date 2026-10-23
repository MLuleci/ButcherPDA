# Review 03: UI / Compose

Scope: `StartScreen`, `FormScreen`, `HomeScreen`, `SettingsScreen`, everything in `ui/components`, `ui/theme`, and the string/theme resources.
Target device: minSdk 23 (Android 6), small screen, gloves, hardware scanner. Nothing was run on a device; where I could not confirm something from the code I say so.

Severity tags: **[Bug]** wrong now or will crash/misbehave as soon as the code path is reached. **[Risk]** likely to hurt in real use. **[Improvement]** would make it better. **[Tidy-up]** housekeeping.

---

## Summary table

| # | Severity | Title |
|---|----------|-------|
| 1 | Bug | Batch row formats a `String` with `%.1f` (will crash) |
| 2 | Risk | Saved state in `BatchDialog` holds non-saveable classes |
| 3 | Risk | Ingredient table sits in a scrolling parent (may be invisible or crash) |
| 4 | Bug | Selected BOM row: text colour does not switch, so it is hard to read |
| 5 | Risk | Comma vs dot decimals; validation and display disagree |
| 6 | Bug | Expiry accepts "1.5" as valid but it is silently ignored |
| 7 | Risk | Quantities are rounded to 1 decimal and printed with raw `Float` text |
| 8 | Risk | Duplicate LazyColumn keys if a BOM lists an item twice |
| 9 | Risk | Hard-coded English strings (including a visible "days") and mistakes in the Bulgarian file |
| 10 | Risk | Touch targets and text sizes for glove use |
| 11 | Improvement | Errors are shown on untouched fields |
| 12 | Improvement | Swipe-only edit/delete |
| 13 | Improvement | Theme: dynamic colour, unused colours, XML theme |
| 14 | Improvement | `StartScreen.kt` is doing too much |
| 15 | Tidy-up | Settings screen details |
| 16 | Tidy-up | Previews, modifier conventions, unused imports |

BatchDialog has its own section after the numbered findings.

*Updated: later decisions change some rows. #5 and #7: assume `.`, parse with a comma fallback and format with `.` everywhere (summary, 2.9). #6: expiry moves to the Start page, is always required and defaults to the product's shelf life (2.10). #9: the Bulgarian terms are decided (СНМ, Разгръщане). #2 and the BatchDialog section: the dialog state is owned by the Form route (2.1). Quantity checks now also depend on whole-number units (2.13).*

---

## 1. Batch row formats a `String` with `%.1f` **[Bug, latent]**

[IngredientTable.kt:159](../app/src/main/java/bg/butcher/pda/ui/components/IngredientTable.kt#L159) and [IngredientTable.kt:217](../app/src/main/java/bg/butcher/pda/ui/components/IngredientTable.kt#L217)

`BatchInfo.quantity` is a `String`. Line 217 builds it with `quantity.toString()`, then line 159 does `"%.1f %s".format(batch.quantity, batch.uom)`. `%f` needs a number; given a `String` it throws `IllegalFormatConversionException`. It has not shown up yet only because no batch rows can be added (the dialog is not wired). The first row that is ever added will crash the Form screen.

```kotlin
// before
Text(text = "%.1f %s".format(batch.quantity, batch.uom))
// after (see #7 for the formatter)
Text(text = "${formatQuantity(batch.quantity.toFloatOrNull() ?: 0f)} ${batch.uom}")
```

Better still: let the row take a `Float`, and only use `BatchInfo` (string quantity) inside the dialog, where a text field needs it.

## 2. `BatchDialog` stores non-saveable classes in `rememberSaveable` **[Risk]**

[BatchDialog.kt:51-53](../app/src/main/java/bg/butcher/pda/ui/components/BatchDialog.kt#L51)

`BatchInfo` is a plain data class, and `NetworkState` / `BatchDetails` are only `@Serializable` (kotlinx), not `Parcelable` or `Serializable`. `rememberSaveable` with no custom `Saver` throws "cannot be saved using the current SaveableStateRegistry" as soon as the activity saves its state (rotation, app switch, process death). Because it is only triggered on save, it will not show in quick desk testing. Fix: keep `status` in plain `remember` (it is re-fetched anyway; the `LaunchedEffect` restarts), and make `BatchInfo` `@Parcelize` or use `rememberSaveable(stateSaver = ...)`. The same applies to any `editing: BatchInfo?` you add in `FormScreen` for the edit flow.

## 3. `IngredientTable` has a `LazyColumn` under a `verticalScroll` parent **[Risk, needs a device check]**

[IngredientTable.kt:181](../app/src/main/java/bg/butcher/pda/ui/components/IngredientTable.kt#L181), [FormScreen.kt:60](../app/src/main/java/bg/butcher/pda/ui/screens/FormScreen.kt#L60), parent at [MainApp.kt:101-105](../app/src/main/java/bg/butcher/pda/ui/MainApp.kt#L101)

`NavHost` has `.verticalScroll(...)`, so every screen is measured with unlimited height. In `FormScreen` the `Column` uses `fillMaxSize()` and the table box uses `weight(1f)`. With unlimited height, `fillMaxSize` does nothing and a `weight` child gets about zero height. I expect the table to be invisible (or, if it gets unlimited height, Compose throws "Vertically scrollable component was measured with an infinity maximum height"). Same root cause: `HomeScreen`'s `Arrangement.Center` / `fillMaxSize` cannot centre anything, so the three buttons will sit at the top.
Suggested fix: remove the `verticalScroll` from `NavHost` and add it only to screens that need it (Start, Settings). Form should not scroll as a whole, since the table is the scroller. (Another reviewer covers navigation; I mention it because it directly breaks this component.)

## 4. Selected BOM row: text keeps the normal colour on the `primary` background **[Bug]**

[StartScreen.kt:95-141](../app/src/main/java/bg/butcher/pda/ui/screens/StartScreen.kt#L95)

Only the star icon switches to `onPrimary`. The two `Text`s use the default content colour, which is dark on the light-theme blue (`0xFF415F91`) and light on the dark-theme light blue, so contrast is poor in both. Selection is also shown by colour only, and the row has no `selected` semantics for TalkBack.

```kotlin
// after
val content = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface
CompositionLocalProvider(LocalContentColor provides content) { ... }
// and use selectable(selected = isSelected, role = Role.RadioButton, onClick = ...) instead of focusable()+clickable()
```

The per-row `FocusRequester` + `focusable()` + `clickable()` is redundant (`clickable` is already focusable). Also consider selecting on a tap and confirming with a second tap on "Select" only if you want that safety; for gloves, two taps per BOM is slower than needed.

## 5. Decimal separator: dot vs comma **[Risk]**

[QuantityInput.kt:21](../app/src/main/java/bg/butcher/pda/ui/components/QuantityInput.kt#L21), [ExpiryInput.kt:37](../app/src/main/java/bg/butcher/pda/ui/components/ExpiryInput.kt#L37), parsing also in `FormViewModel.kt:59`

*Updated: the developer asked to assume `.`. The key point is consistency: parse with a comma fallback, and format with `.` everywhere instead of the locale-dependent `"%.1f".format`. See the summary, 2.9.*

`toFloatOrNull()` only understands `.`. With `KeyboardType.Decimal`, many keyboards (and Android 8+ itself) show a comma when the device language is Bulgarian. A user typing `2,5` gets "Enter a valid number." On Android 6 the stock `numberDecimal` filter normally only allows `.`, but vendor PDA keyboards vary, so I cannot say which you will get. Cheapest robust fix is to accept both:

```kotlin
// before
val value = text.toFloatOrNull()
// after
val value = text.trim().replace(',', '.').toFloatOrNull()
```

Put this in one shared `parseDecimal(text)` and use it in the dialog, Start screen and ViewModel so they cannot disagree. Note `toFloatOrNull` also accepts `1e3` and `1f`, which a scanner typing into the wrong field could produce; a small regex `^\d+([.,]\d+)?$` is stricter.

*Whole-number units: `validateQuantity` and `QuantityInput` also need to know whether the unit must be a whole number (e.g. Nos). See the summary, 2.13.*

## 6. Expiry days: validated as Float, used as Int **[Bug]**

[ExpiryInput.kt:37](../app/src/main/java/bg/butcher/pda/ui/components/ExpiryInput.kt#L37), [ExpiryInput.kt:62](../app/src/main/java/bg/butcher/pda/ui/components/ExpiryInput.kt#L62), [ExpiryInput.kt:76](../app/src/main/java/bg/butcher/pda/ui/components/ExpiryInput.kt#L76)

`validateDays("1.5")` returns no error, but the date preview uses `toIntOrNull()` (so shows nothing) and the ViewModel's `expiryDays` is also `toIntOrNull()` (null). The user sees a green field and the expiry is dropped. Use `toIntOrNull()` in validation too, and `KeyboardType.Number`. *Updated: the expiry field moves from Form to Start, is always required (`canContinue` needs a valid whole number > 0), and is pre-filled from the BOM search's `shelf_life_in_days`; the operator can override it (summary, 2.10).*
Also: the text field's suffix is the hard-coded `"days"` (line 67) even though `R.string.days` exists (and is unused); and `dateFromToday` adds `n.days` as 24-hour units to an `Instant`, which can be a day off around DST changes. A calendar-date add is safer: `Clock.System.todayIn(tz).plus(n, DateTimeUnit.DAY)`. The wrapping `Column { }` around the single field is not needed.

## 7. Quantity formatting: rounding and raw `Float.toString` **[Risk]**

[IngredientTable.kt:96](../app/src/main/java/bg/butcher/pda/ui/components/IngredientTable.kt#L96), [IngredientTable.kt:217](../app/src/main/java/bg/butcher/pda/ui/components/IngredientTable.kt#L217), [FormScreen.kt:55](../app/src/main/java/bg/butcher/pda/ui/screens/FormScreen.kt#L55)

- Table header shows `"%.1f / %.1f"`: one decimal. A recipe needing 0.25 kg of spice shows `0.3`, 0.04 shows `0.0`. For a butcher, grams matter.
- `"%.1f".format(...)` uses the device locale, so a Bulgarian device shows `1,5` while the input (and the keyboard) may use `1.5`.
- `FormScreen` prints `${bom.quantity}` raw, so you can see `1.3333334` or `1.0E-4`. `quantity.toString()` at line 217 does the same (and this string is what would go into the edit dialog).

*Updated: do not use a locale-aware formatter. The decision is to format with `.` everywhere, so a shown value can be typed back in (summary, 2.9). Use `formatDecimal` (up to 3 decimals, no trailing zeros, no exponent) in place of the helper below; it also takes `Double`, not `Float`.*

Suggested single helper (shows up to 3 decimals, drops trailing zeros, locale aware):

```kotlin
fun formatQuantity(value: Float): String =
    NumberFormat.getNumberInstance().apply {
        maximumFractionDigits = 3
        minimumFractionDigits = 0
    }.format(value)
```

Because the sum is a `Float`, `0.1f + 0.2f` can display `0.3` or `0.30000001` depending on formatting; the formatter above hides that. For the text shown in editable fields (dialog), use a locale-independent version so what the user sees round-trips through the parser in #5. *(Updated: now applies to all numbers, not only editable fields; see above.)*
Display idea for required vs chosen: show the remaining amount and colour the line (green when total is within tolerance of required, amber when under, red when over). *Updated: decided as MISSING / UNDER / OK / OVER with the allowed limit shown; whole-number units show `use 2` or `use 2–3` (summary, 2.2 and 2.13).* At the moment the section row is plain text `chosen / required uom` with no padding and no emphasis.

## 8. Duplicate keys if a BOM lists the same item twice **[Risk]**

[IngredientTable.kt:186](../app/src/main/java/bg/butcher/pda/ui/components/IngredientTable.kt#L186), [IngredientTable.kt:210](../app/src/main/java/bg/butcher/pda/ui/components/IngredientTable.kt#L210)

`stickyHeader(ingredient.itemCode)` uses the item code as the key. ERPNext BOMs can have the same item on two lines (unless "explode" merges them), and Compose crashes with "Key ... was already used" for duplicate keys in one LazyColumn. The `batches` map is also keyed by item code, so two lines would share one pool of batches anyway. Please confirm with the developer how the server shapes `items`; if duplicates are possible, merge them before the table. The batch-row keys (`it.first`) are only item-local, and the same batch number under two items would also clash; prefix with the item code (`"$itemCode|$batchNumber"`).

## 9. Strings **[Risk / Tidy-up]**

Hard-coded English that bypasses translation:

| Where | Text |
|-------|------|
| [ExpiryInput.kt:67](../app/src/main/java/bg/butcher/pda/ui/components/ExpiryInput.kt#L67) | `"days"` (visible suffix, `R.string.days` exists but is unused) |
| [FormScreen.kt:72](../app/src/main/java/bg/butcher/pda/ui/screens/FormScreen.kt#L72) | `"Add"` |
| [StartScreen.kt:137](../app/src/main/java/bg/butcher/pda/ui/screens/StartScreen.kt#L137) | `"Default"` |
| [SearchField.kt:37](../app/src/main/java/bg/butcher/pda/ui/components/SearchField.kt#L37), [:41](../app/src/main/java/bg/butcher/pda/ui/components/SearchField.kt#L41) | `"Search"`, `"Clear"` |
| [ErrorMessage.kt:37](../app/src/main/java/bg/butcher/pda/ui/components/ErrorMessage.kt#L37) | `"Retry"` (and the button text already says Retry) |
| [IngredientTable.kt:117](../app/src/main/java/bg/butcher/pda/ui/components/IngredientTable.kt#L117), [:130](../app/src/main/java/bg/butcher/pda/ui/components/IngredientTable.kt#L130) | `"Edit"`, `"Delete"` |
| [BatchDialog.kt](../app/src/main/java/bg/butcher/pda/ui/components/BatchDialog.kt) trailing icon | `"Valid"` |
| [SettingsScreen.kt:55](../app/src/main/java/bg/butcher/pda/ui/screens/SettingsScreen.kt#L55), [:60](../app/src/main/java/bg/butcher/pda/ui/screens/SettingsScreen.kt#L60) | `"API URL"`, `"API Key"` |

String concatenation in [FormScreen.kt:54-55](../app/src/main/java/bg/butcher/pda/ui/screens/FormScreen.kt#L54): word order and the colon belong to the translation. Use format args:

```xml
<string name="bom_line">BOM: %1$s (%2$s)</string>
<string name="quantity_line">Quantity: %1$s %2$s</string>
```
```kotlin
Text(stringResource(R.string.bom_line, bom.itemName, bom.name))
```

Key parity: `values/` and `values-bg/` have exactly the same keys (checked). Only `days` is unused in code (plus nothing else). Content problems in `values-bg`:
- `search_label` is still English ("Search").
- `bom_label` is "CHM" and the bytes are Latin letters (C, H, M). *The developer chose the short form "СНМ".* Type it in Cyrillic (С-Н-М, U+0421 U+041D U+041C), not Latin C-H-M. They look the same, but Latin letters can render in a different font, and a screen reader pronounces them as English letters.
- `explode_label` "Експлодирам" literally means "I explode". *The developer gave the term: use "Разгръщане".*
- `amount_label` "Сума" means a sum of money; "Количество" or "Кол." fits.
- `settings_title` "Настройките" is the definite form ("the settings"); title is normally "Настройки".
- `invalid_batch_no` lacks the final period that the English one has.

Plurals: nothing needs them right now since the number sits in an input and the label is a standalone suffix. If you ever write "Expires in 5 days" as one sentence, use `<plurals>` (Bulgarian: one/other).

## 10. Touch targets, text sizes, accessibility for gloves **[Risk]**

- Home buttons ([HomeScreen.kt:56-59](../app/src/main/java/bg/butcher/pda/ui/screens/HomeScreen.kt#L56)): `TextButton`, 150dp wide, default 40dp tall, looks like plain text. For gloves use filled/outlined buttons at least 56dp tall and full width.
- Tiny text: item code and unit use `labelSmall` (11sp), e.g. [StartScreen.kt:125](../app/src/main/java/bg/butcher/pda/ui/screens/StartScreen.kt#L125), the quantity/expiry suffix. The retry icon is 15dp. On a small, low-density PDA this is hard to read. `AppTypography = Typography()` is the default; consider bumping the smallest styles.
- Labels next to checkboxes ([RowWithLabel.kt](../app/src/main/java/bg/butcher/pda/ui/components/RowWithLabel.kt)) are not tappable; only the 48dp box toggles. Make the entire row `toggleable(role = Role.Checkbox)` and give the Checkbox `onCheckedChange = null`.
- `BomSelect` ([StartScreen.kt:187](../app/src/main/java/bg/butcher/pda/ui/screens/StartScreen.kt#L187)) is a `TextButton` showing "None" or the BOM name. It does not look like an input. An `OutlinedButton` (or a read-only outlined field with a drop-down arrow) makes it obvious it is tappable, and it can be full width so long names fit.
- Add button ([FormScreen.kt:66-73](../app/src/main/java/bg/butcher/pda/ui/screens/FormScreen.kt#L66)): wide tap area but only a 24dp icon, no text. A labelled button "Add batch" is clearer.
- Home icons have `contentDescription = title` and the same text beside them, so TalkBack reads it twice; use `null` for decorative icons. Same for the Retry icon.
- Colour-only meaning: selected row (see #4), error state in table (none yet).
- The 500dp cap on the results list ([StartScreen.kt:81](../app/src/main/java/bg/butcher/pda/ui/screens/StartScreen.kt#L81)) is larger than a small PDA's visible area once the keyboard is up (a 480x800 screen at ~1.5 density is about 530dp tall). I could not tell whether the bottom sheet resizes for the keyboard; please test with the keyboard open (see open questions).

## 11. Validation errors show on untouched fields **[Improvement]**

[QuantityInput.kt:37](../app/src/main/java/bg/butcher/pda/ui/components/QuantityInput.kt#L37), [ExpiryInput.kt:53](../app/src/main/java/bg/butcher/pda/ui/components/ExpiryInput.kt#L53)

The error is computed every composition, so an empty Start screen already shows a red "A value is required." under Quantity, and the dialog opens with a red quantity field. A "touched" flag (or only showing the error after the first edit or focus loss) is friendlier. Also, `Text(..., color = error)` inside `supportingText` is redundant: `isError = true` colours it already. `QuantityInput` should also pass `Modifier.fillMaxWidth()` by default from callers, otherwise it stays at the field's default width and the unit suffix may look cramped.

## 12. Swipe-only edit / delete **[Improvement, partly unfinished]**

[IngredientTable.kt:107-149](../app/src/main/java/bg/butcher/pda/ui/components/IngredientTable.kt#L107)

Known WIP: `onDismiss` is empty (also the "TODO: Pass on callbacks" comment). Things to plan for:
- Swiping is hard with gloves and invisible to the user (nothing hints it exists). Add visible edit/delete icon buttons, or tap-to-edit plus a delete icon.
- After a swipe the row stays in the dismissed position unless you reset the state or remove the item. Return `false` from `confirmValueChange` for edit, and for delete ask for confirmation (an accidental swipe removes a traceability record).
- The row content has no background, so while swiping you see the coloured background through the text. Give the foreground `Modifier.background(MaterialTheme.colorScheme.surface)`.
- `tint = Color.White` is fixed; use `onPrimary` / `onError`.

## 13. Theme **[Improvement / Tidy-up]**

- Dynamic colour is handled correctly on API 23 (`Build.VERSION.SDK_INT >= S` check, falls back to `lightScheme` / `darkScheme`). But on Android 12+ devices the wallpaper colours replace your branded palette, so the app looks different per device. For a fleet app, `dynamicColor = false` by default is usually preferable. Also the parameter is `true` by default but nothing passes it.
- [Color.kt](../app/src/main/java/bg/butcher/pda/ui/theme/Color.kt) has ~210 values; the medium- and high-contrast schemes in [Theme.kt:94-230](../app/src/main/java/bg/butcher/pda/ui/theme/Theme.kt#L94) are never used. Either delete them, or put them to use (a "high contrast" setting could help in glare on a warehouse floor). `ColorFamily`, `unspecified_scheme` and several imports in Theme.kt (`Activity`, `toArgb`, `Typography`) are unused; indentation in `AppTheme` is 2 spaces vs 4 elsewhere. [Type.kt](../app/src/main/java/bg/butcher/pda/ui/theme/Type.kt) has unused imports and just wraps the default.
- XML themes ([themes.xml](../app/src/main/res/values/themes.xml), [values-night/themes.xml](../app/src/main/res/values-night/themes.xml)): still the template's purple/teal `Theme.MaterialComponents.DayNight.DarkActionBar` (colors.xml likewise). The Compose UI does not use these colours, but the window background/status bar and the `DarkActionBar` parent may show a native action bar above your Compose `TopAppBar` (I did not run it to check; look at it on a device). A `NoActionBar` parent avoids that, and if nothing else needs it, you can drop the `com.google.android.material:material` dependency and use `android:Theme.Material.NoActionBar`.
- Dark theme: both schemes exist and `isSystemInDarkTheme()` drives it. The `surfaceBright`/`surfaceDim` zebra striping in the list works in both, but see #4 for the selected row. There are no dark-theme previews.

## 14. `StartScreen.kt` does too much **[Improvement]**

[StartScreen.kt](../app/src/main/java/bg/butcher/pda/ui/screens/StartScreen.kt) (391 lines; half is previews)

`BomSearch` is about 110 lines with a four-level nested `Column > Box > LazyColumn > Row`. Suggested split into the `components` package: `BomResultRow(item, selected, onClick)`, `BomResultsList(...)`, and a shared `ConfirmCancelButtons(onCancel, onConfirm, confirmEnabled)` (the same Cancel/Select row is copied in [BatchDialog.kt](../app/src/main/java/bg/butcher/pda/ui/components/BatchDialog.kt)). Move `BomSelect`/`BomSearch` to `BomSelect.kt`; keep `StartScreen` and its previews alone.
Other points:
- The error text from the search (`NetworkState.Error(e.message)`) is not shown; users only see the generic message. Fine for users, but the payload could be dropped to avoid confusion.
- The keyboard "Search" action does nothing ([SearchField.kt:45](../app/src/main/java/bg/butcher/pda/ui/components/SearchField.kt#L45) has no `keyboardActions`). Since search runs as you type, either hide the keyboard on Search, or change `ImeAction` to `Done`. A scanner that sends Enter would hit this.
- `SearchField` calls `.focusable()` on a `TextField` (already focusable), and `requestFocus` sets a keyboard popping up automatically; on PDAs with a scanner you may or may not want that.
- The results list has no sort/"default first" ordering and the `isDefault` star has no explanation; consider a short legend or the word "default".
- Previews are good for BomSearch states; there is no preview for `StartScreen` with a selection or an error.
- State hoisting is reasonable: `BomSearch` owns `query`/`selectedItem` locally (fine, since they are transient), and the results come in as a parameter.

## 15. Settings screen details **[Tidy-up]**

[SettingsScreen.kt](../app/src/main/java/bg/butcher/pda/ui/screens/SettingsScreen.kt)

- The API key field shows the key in clear text. Add `visualTransformation = PasswordVisualTransformation()` (with a show/hide toggle if you like) and `KeyboardType.Password`/`Uri` for the URL field.
- Labels are hard-coded (#9). The screen `Column` has no padding or spacing, the Save button is not full width, and there is no confirmation after Save ("Saved" toast / snackbar).
- `ImeAction.Done` does nothing but hide the keyboard; fine, but you could save there.
- `collectAsState()` works; `collectAsStateWithLifecycle()` is the lifecycle-aware option (needs `lifecycle-runtime-compose`).
- `ValidatedTextField` takes no `modifier`.

## 16. Previews, modifier conventions, unused code **[Tidy-up]**

- Previews exist for `QuantityInput`, `ExpiryInput`, `HomeScreen` (misnamed `StartScreenPreview`), and Start/Bom* in `StartScreen.kt`. Missing: `BatchDialog`, `IngredientTable` (+ row/section), `FormScreen`, `SettingsScreen`, `ErrorMessage`, `SubmitButton`, `SearchField`. Make preview functions `private`, and add a `uiMode = UI_MODE_NIGHT_YES` variant. `IngredientTable` is the one that will most benefit: you would have seen finding #1 immediately.
- Modifier convention (first optional parameter, applied to the root): mostly followed. Exceptions: [RowWithLabel.kt:17](../app/src/main/java/bg/butcher/pda/ui/components/RowWithLabel.kt#L17) has `modifier` before the `content` lambda (the lambda should be last); `HomeScreen` has `modifier` first optional but `onButtonClicked` after it; `ExpiryInput` puts `modifier` on the field but wraps in a `Column` without it; `BatchDialog`, `ValidatedTextField`, `BomSelect` (applies it to the button, fine) lack or misplace it. `FormScreen` passes `modifier` to a `Box` and then the content `Column` adds `fillMaxSize()` itself, which is fine.
- Unused imports (found by a quick text scan, please let the IDE confirm): `StartScreen.kt` (`size`, `getValue`, `setValue`), `BatchDialog.kt` (`launch`, `rememberCoroutineScope`/`scope` is declared but never used), `FormScreen.kt` (`Button`), `Theme.kt`, `Type.kt`. Some `getValue/setValue` are false positives for delegates (`var x by ...` needs them); only remove the ones the IDE flags.
- `FormScreen`'s submit button uses `state.canContinue`, which (see `FormUiState`) only checks quantity and selected BOM, not expiry or any batches. Probably intentional for WIP; once submitting is real, the enable rule should include valid expiry and at least one batch (and optionally "all ingredients covered"). *Updated: the rule is decided. Submit needs every ingredient within ±tolerance % (±0.5 for whole-number units) via `canSubmit`; expiry is checked on Start through `canContinue` (summary, 2.2 and 2.10).* Also the `Idle` state renders an empty screen, and the error state has no retry.
- Unfinished items I take as intentional: dialog not shown, `onSubmit = {}`, swipe callbacks, Summary/Search screens.

---

# BatchDialog

File: [BatchDialog.kt](../app/src/main/java/bg/butcher/pda/ui/components/BatchDialog.kt). Read together with findings #2, #5, #7, #11.

### B1. `isDirty` is always true when adding **[Bug-ish / Tidy-up]**
[BatchDialog.kt:57](../app/src/main/java/bg/butcher/pda/ui/components/BatchDialog.kt#L57)

`val isDirty = batch != initialValue`. When adding, `initialValue == null` and `batch` is a `BatchInfo`, so this is always `true`, which means it has no effect in add mode (harmless, but misleading). In edit mode it works: `batch` equals `initialValue` until the quantity text changes. A clearer way:

```kotlin
// before
val isDirty = batch != initialValue
// after
val hasChanges = initialValue == null || batch.quantity != initialValue.quantity
```

Edge: if the quantity is edited from `"2.5"` to `"2.50"` it counts as changed; compare parsed floats instead. *(Updated: compare parsed `Double`s using `parseDecimal`; summary, 2.9.)*

### B2. Edit mode re-fetches the batch on open **[Improvement]**

*Updated: the developer confirmed that editing is for changing the quantity only. A scan while editing does nothing apart from a short toast. The suggestion below matches that; see the summary, 2.1, for the `Adding` / `Editing` dialog state.*
[BatchDialog.kt:66-96](../app/src/main/java/bg/butcher/pda/ui/components/BatchDialog.kt#L66)

Opening in edit mode sets status to `Idle`, waits 300 ms, calls the server, and the Done/Select button stays disabled until it succeeds (`isBatchValid` needs `Success`). So an operator offline (or on a flaky Wi-Fi) cannot even fix a typo in a quantity. In edit mode the batch was already validated when it was added, so start as valid and skip the lookup:

```kotlin
var status by remember { mutableStateOf(if (isEditing) Valid else Idle) }
LaunchedEffect(batchNumberText) {
    if (isEditing) return@LaunchedEffect
    ...
}
```

Also use `readOnly = isEditing` instead of `enabled = !isEditing`: a disabled field is greyed and cannot be selected/copied.

### B3. "Stale itemCode/uom" worry: not a real bug today, but fragile **[Tidy-up]**
[BatchDialog.kt:88-94](../app/src/main/java/bg/butcher/pda/ui/components/BatchDialog.kt#L88)

I checked this. When the text changes, `status` is set to `Idle` immediately (line ~69), and submit needs `status is Success`. So a failed second lookup cannot submit the first lookup's `itemCode`/`uom`. The separate `batch` copy and `status` are two sources of truth, though; a future change could let them disagree. Simpler: don't copy into `batch` at all, keep only `quantityText` in state and build the result at submit time from the `Success` payload:

```kotlin
val details = (status as? NetworkState.Success)?.data
Button(onClick = { details?.let { onSubmit(BatchInfo(it.name, it.item, quantityText, it.uom)) } }, ...)
```

That also removes the stale-data question completely. The uom label (line ~136, `(status as? Success)?.data?.uom ?: ""`) is already derived this way, so the two code paths are inconsistent now.

*Updated: the unit no longer comes from `BatchDetails.uom` at all (that field is replaced by `stock_uom`, used only on Details). The dialog takes the unit, and the whole-number flag, from the matched `BomDetailsItem` (summary, 2.3 and 2.13), so `BatchInfo(..., it.uom)` above should use `ingredient.uom`.*

### B4. `ensureActive` comment **[Tidy-up]**
[BatchDialog.kt:85](../app/src/main/java/bg/butcher/pda/ui/components/BatchDialog.kt#L85)

The guard is right in principle: if a lookup returned a value after being cancelled, `status = result` could overwrite a newer state. In this app `apiCall` already rethrows `CancellationException` ([NetworkErpRepository.kt:16](../app/src/main/java/bg/butcher/pda/data/NetworkErpRepository.kt#L16)), so it is belt and braces. Inside `LaunchedEffect` you can call plain `ensureActive()` (the scope is a `CoroutineScope`), no `currentCoroutineContext()` import needed. Fine to keep.

### B5. No check that the batch's item is in the recipe **[Risk, most important]**
[BatchDialog.kt:44-49](../app/src/main/java/bg/butcher/pda/ui/components/BatchDialog.kt#L44), `FormViewModel.upsertBatch`

*Updated after the developer's note: the dialog deliberately doesn't ask for the ingredient. The ingredient comes from the batch's `item` once the lookup succeeds.*

That design is fine, but it needs one guard. Nothing checks that the looked-up `item` is one of the BOM's ingredients. A scanned pork batch on a beef recipe would be accepted. `IngredientTable` only draws rows for BOM ingredients, so that batch would be stored and sent but never shown.

- Pass the BOM's ingredients (or just their item codes) into the dialog. After a successful lookup, if `details.item` isn't among them, show "Batch X is item Y, which is not in this recipe" and keep Select disabled. Add a `NOT_IN_RECIPE` value to `FetchBatchError`; the `Error` state should then carry the fetched `BatchDetails` so the message can name the item.
- **Show the inferred ingredient** in the dialog once valid (`details.itemName`, `details.item`, unit), e.g. in the field's `supportingText`. Right now the fetched `itemName` is thrown away, so the operator can't confirm what the scan matched.
- Units: *updated: the developer suggested using the matched ingredient's `BomDetailsItem.uom` in the dialog, since the ingredients are passed in anyway.* It's also the unit that the required amount and the server's Stock Entry rows use (see the summary, 2.3).
- *Updated:* if the same batch number is added twice, `upsertBatch` silently overwrites it. The developer wants it **rejected**, with an error under the batch number field so the user can correct it. Check against the batch numbers already on the form **before** the network lookup, and again with the server's `name` after it. Edit mode skips the lookup, so it never runs this check. Add `ALREADY_ADDED` to `FetchBatchError`. See the summary, 2.3, for a snippet.

### B6. Scanner flow **[Improvement]**
[BatchDialog.kt:50-95](../app/src/main/java/bg/butcher/pda/ui/components/BatchDialog.kt#L50)

*Updated after the developer's note. The intended flow is:*
- *Manual: + opens an empty dialog, and the operator types the batch number and quantity.*
- *Scan: a hardware scan opens the dialog with the batch number filled in, and later scans replace the field's value.*
- *~~The scanner integration hasn't been explored yet.~~ Answered: it is the vendor SDK `qspdasdk.jar`, set up once in `Application.onCreate` (summary, 2.11).*

To support this:

- **Hoist the batch number.** `batchNumberText` is private dialog state today, so a scan arriving from outside can't open the dialog or replace the text. Move it to the Form route (or `FormViewModel`). The dialog takes `batchNumber` + `onBatchNumberChange`, and "dialog open" becomes "input is not null". See the summary, section 2.1, for a sketch.
- **Mark where the text came from.** A scanned value is complete, so skip the 300 ms debounce and look it up immediately. Keep the debounce for typing. *Updated: a scan goes through `parseScan` first, which reduces an ERPNext batch URL to the batch number (summary, 2.11 part 5). The vendor beep plays when the lookup succeeds, not when the scan arrives.*
- **Ignore scans while editing.** If the edit dialog is open, a scan only shows a toast ("Scan ignored. Close the edit to add a new batch."). It doesn't touch the read-only batch number. For this, the Form route has to know which mode the dialog is in, hence the `Adding` / `Editing` split in the summary, 2.1.
- **Only listen on Form, Search and Details** (Details added later; see the summary, 2.14). Scans on any other route are ignored, as the developer decided. A small `OnScan { }` composable, called only from those two routes, does this; see the summary, 2.1. *Updated: there is also no on-screen scan button; the hardware trigger is the only way to scan (summary, 2.11 part 3).*
- **Scanner behind an interface** (`BarcodeScanner { val scans: Flow<String> }`). The UI then doesn't depend on the vendor, and a fake can be used on the emulator. *The device uses the vendor SDK `qspdasdk.jar`. Per the JAR, scans arrive as a `com.qs.scancode` broadcast with the text in the `"data"` extra. See the summary, 2.11, and 05-scanner-sdk.md.*
- **Focus:** for manual entry, focus the batch field when the dialog opens empty. *Updated: after any scan (whether it opens the dialog or replaces the batch number), clear focus from all inputs and hide the keyboard, as the developer decided. Do it inside `BatchDialog` with `LocalFocusManager.clearFocus(force = true)` and `LocalSoftwareKeyboardController.hide()`, because the dialog has its own window. See the summary, 2.1.*
- **IME flow for manual entry:** batch field `ImeAction.Next` (look up now, move to quantity) → quantity `ImeAction.Done` (submit if valid). `ImeAction.Done` with no `keyboardActions` currently just hides the keyboard.

```kotlin
keyboardActions = KeyboardActions(onNext = {
    lookupNow()                       // cancel the debounce, fetch immediately
    quantityFocus.requestFocus()
})
```
- Consider `capitalization = KeyboardCapitalization.Characters` if batch codes are always upper case. Also strip control characters from scanned text before the lookup (you already `trim()`).
- **Keep a typed quantity on rescan** (developer's decision). Don't key the quantity state on the batch number. Track `quantityTyped` separately from the prefill.
- Prefill the quantity with what's still needed for the inferred ingredient (required minus chosen) once the lookup succeeds, but only if the operator hasn't typed one. A prefill *is* recalculated when a new scan replaces the batch number.

### B7. Layout and look **[Improvement]**
[BatchDialog.kt:96-150](../app/src/main/java/bg/butcher/pda/ui/components/BatchDialog.kt#L96)

- The `Column` inside the `Card` has no padding or spacing: the fields touch the card edges. Add `Modifier.padding(16.dp)` and `verticalArrangement = Arrangement.spacedBy(12.dp)`; make both fields `fillMaxWidth()`.
- The `CircularProgressIndicator()` in the `trailingIcon` uses the default 48dp size and 4dp stroke, bigger than the field's icon slot (it is the whole height of the field). Use `Modifier.size(24.dp), strokeWidth = 2.dp`.
- `supportingText` appears and disappears, so the dialog jumps in height. Reserve a line (an empty `Text(" ")`) or show the item name there once valid ("Beef trim, Kgs"), which also solves part of B5.
- `Icon(Icons.Default.Check, contentDescription = "Valid")` is not translatable; use a string resource or `null` if the supporting text already says it.
- Add a title ("Add batch" / "Edit batch"). Once the lookup succeeds, show the inferred ingredient name under it.
- Use `DialogProperties(dismissOnClickOutside = false)`: with gloves, a stray tap outside closes the dialog and loses the scanned data. Keep `dismissOnBackPress` as is.
- Error text: `REQUEST_FAILED` (server 500 or 401) and `NETWORK_ERROR` both show "Network error". A 401/403 (bad API key) deserves its own message; `INVALID_INPUT` is unreachable here because the text is trimmed and non-empty.
- `QuantityInput` and `Cancel`/`Select` row: the label says "Select" (copied from the BOM sheet); "Add" / "Save" suits better.
- `rememberCoroutineScope()` / `scope` and `launch` are unused (remove them).
- Sizing: the buttons use `IntrinsicSize.Min` for no clear benefit and default 40dp height; make them taller for gloves.

### B8. Suggested shape for the dialog (sketch)

```kotlin
@Composable
fun BatchDialog(
    batchNumber: BatchInput?,                   // hoisted: typed or scanned (fromScan skips debounce); null when editing
    onBatchNumberChange: (String) -> Unit,
    ingredients: List<BomDetailsItem>,          // to check the inferred item is in the recipe
    remainingFor: (itemCode: String) -> Double, // prefill hint once the item is known
    fetchBatchDetails: suspend (String) -> NetworkState<BatchDetails, FetchBatchError>,
    onSubmit: (BatchInfo) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialValue: BatchInfo? = null,            // non-null = editing: quantity only, no lookup, scans ignored by the caller (summary 2.1 uses a sealed `BatchDialogState`: Adding / Editing)
) {
    var quantity by rememberSaveable { mutableStateOf(initialValue?.quantity.orEmpty()) }   // not keyed on batchNumber: survives rescans
    var quantityTyped by rememberSaveable { mutableStateOf(initialValue != null) }        // prefill only replaces untyped values
    var lookup by remember { mutableStateOf<Lookup>(if (initialValue != null) Lookup.Ok(...) else Lookup.Idle) }
    // Lookup: Idle / Loading / Ok(details) / NotFound / NotInRecipe(details) / NetworkError
    ...
}
```

`NotInRecipe` is the new state, and it is what makes B5 work. The batch number no longer lives in the dialog, so a scan can open it or replace its value (B6).

---

# Open questions for the developer

1. ~~`.` or `,`?~~ *Answered: assume `.` (see #5 and the summary, 2.9).*
2. ~~What does your scanner send?~~ *Answered: not explored yet. The plan is for a scan to open the dialog pre-filled and for later scans to replace the value; see B6.* Keyboard: hidden and all inputs unfocused after a scan. *Later answered: the vendor SDK sends the result on `com.qs.scancode` with extra `"data"` (per the JAR; confirm on the device). See the summary, 2.11.*
3. Is the screen orientation locked? (Affects saved-state crashes in #2 and layout.)
4. Can a BOM list the same item on more than one line (#8), and is the "explode" option the thing that merges them?
5. ~~Which unit?~~ *Answered: use the matched ingredient's `BomDetailsItem.uom` in the dialog.*
6. ~~Per-ingredient or global +?~~ *Answered: global. The ingredient is inferred from the batch, and batches whose item isn't in the recipe are blocked (B5).*
7. ~~Duplicate batch: replace or reject?~~ *Answered: reject, with the error shown under the batch field (B5).*
8. ~~Tolerance?~~ *Answered: ±tolerance % around the BOM amount, under or over. It's hard-coded at 5% for now and will later come from `BomDetails`. The table should show MISSING / UNDER / OK / OVER for each ingredient (see the summary, 2.2).*
9. ~~Bulgarian terms?~~ *Answered: BOM = "СНМ" (in Cyrillic letters), explode = "Разгръщане".*
10. On the real PDA, when the keyboard is open in the BOM bottom sheet, can you still see the list and the Select button? (The 500dp cap and edge-to-edge insets are the unknowns; also whether a native action bar shows above your TopAppBar, see #13.)
11. Do you want the app to follow the system dark mode, or force one theme? Is there glare/outdoors use where a high-contrast option would help?
12. Will Android 12+ devices ever be used? If yes, decide whether dynamic colour should apply.
