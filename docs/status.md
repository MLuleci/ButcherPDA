# Status

As of commit `d842999` (October 2026): all the planned phases (0–7) are implemented, along with the self-update.

## Confirmed on the device

- **A full manufacturing run:** scan ingredient labels, submit, Summary, then "Same BOM again" with the inputs kept.
- **Backgrounding** the app on any screen doesn't crash.
- **Printing on a continuous roll:** text and QR code, with label mode off, so there's no long feed.
- **Scan results** arrive on `com.qs.scancode`, the trigger on `ismart.intent.scandown`, and `mComFd` is 23.
- **"Unknown sources"** can be switched on in the PDA's settings, and is on for the test device.

## Built but not yet confirmed on the device

- **Search and Details:**
  - scanning a label opens its Details;
  - scanning another label on Details replaces it, and Back goes to Search;
  - the filters and sorts change the list;
  - raw-material batches (e.g. `BEEF.0001`) are left out by default, and listed under "Raw materials";
  - a label printed from Details scans back to the same page;
  - a raw-material batch prints from Details, and its label is accepted in the Form flow's batch dialog.
- **Roll labels** at the configured height, tearing cleanly in the blank end. Adjust `TEAR_OFF_ROWS` (a multiple of 24) if needed.
- **The minified, signed release build** on the PDA (R8 with the SDK and the serialization models).
- **Self-update:**
  - with no releases, it says it's up to date;
  - a newer release downloads and opens the installer;
  - a file signed with a different key is refused.
- **The Android 6 install path:** the `file://` URI in the external files folder. The first attempt with a `content://` URI found no installer, even with "Unknown sources" on.
- **58 mm die-cut labels with the measured print area:** the whole QR code on the label, the text not cut short, and no extra blank label after each print. Earlier tests clipped the QR code's left edge and fed a blank label each time.
- **Labels without padding:** the QR code at the label's left edge (about 1 mm in on 58 mm), about 26-27 mm on a 58 x 40 mm label (a whole number of dots per module, up to the 29 mm cap), and both still scanning.
- **No "Batch not found" after printing:** on Details, printing a label, or printing with the roll away from the right edge, no longer shows it, and a scan with the trigger still works.
- **The emulator** with `useFakeErp=true`: pick a BOM, see the expiry filled in, see the ingredient table.

## Known gaps and deferred work

- **Delete has no confirmation:** the ingredient table's Delete button removes a batch at once, with no undo.
- **Validation errors show on untouched fields**, e.g. an empty Start screen shows "A value is required". There's no "touched" flag.
- **No emulator scanning:** there's no way to trigger `FakeBarcodeScanner` on the emulator.
- **Keyboard suggestions:** turning them off outside the search fields is in `git stash@{0}` ("Turn off keyboard suggestions outside search fields": a `NoSuggestions` wrapper using `InterceptPlatformTextInput`). It predates later changes to those fields, so it needs reapplying by hand.
- **Die-cut labels:** 80 mm labels are slightly too wide for the test device's bay, and 58 mm labels are untried.
- **Print result:** a job the SDK drops during its 200 ms delay still counts as printed.
- **Error types:** `printState` uses `Throwable` rather than `ErpError`, deliberately, since a printer failure isn't a server error.
- **AppCompat and Material** are still dependencies, because the XML theme (`Theme.MaterialComponents…`) needs them. The theme files are off limits, so they stay.
- **R8's new `optimization { }` block** waits for AGP 9.3.
- **No instrumented tests:** `androidTest` is still the template.

## Open questions

- **Vendor questions:** gap or black-mark sensing for `labelEnable(true)`, and the unit of `setStarNum`.
- **Cyrillic sorting:** product names sort in Bulgarian order only if the database collation is Unicode. Not verified.
- **Search edge cases:**
  - an item whose default BOM is removed drops out of Search, though it can still be found by a scan or an exact Enter;
  - an item that's both made and bought shows its bought batches under "made here", since the kind is decided by the item, not the batch.
