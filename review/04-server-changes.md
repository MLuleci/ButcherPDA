# Server-side changes (ButcherERP `my_app`)

All the changes to [`my_app/my_app/api.py`](https://github.com/MLuleci/ButcherERP/blob/master/my_app/my_app/api.py) and the ERPNext setup that the app work depends on are collected here, so they can be done first. Line numbers refer to `api.py` as it was before these changes (commit `bf4cc04`).

_Status: all items in this file are done, including A7 (the PDA User role). What follows is now documentation of what was done. Where the implementation differed from the plan below, a note says so; see the "Implementation notes" and "Whole-number units (done)" sections at the end._

The items are in the order I'd do them:

- **Part A:** needed before the app changes. The app's new code assumes these.
- **Part B:** strongly recommended.
- **Part C:** later, when you add the tolerance to the server. _Updated: done too._

At the end there's a complete version of `create_manufacture_entry` with parts A and B applied. Several items touch that function, and it's easier to read as a whole.

**Test on a copy of the site.** `create_manufacture_entry` submits real Stock Entries and creates real Batches.

---

## Part A: needed before the app work

### A1. Accept `items` as JSON, not only as a JSON string

[api.py:126](https://github.com/MLuleci/ButcherERP/blob/master/my_app/my_app/api.py#L126)

The app will send a JSON request body (summary, 2.4) instead of form fields. Frappe merges the keys of a JSON body into the method's arguments, so `items` will arrive as an already-parsed dict, and `json.loads(dict)` raises a `TypeError`. `frappe.parse_json` handles both a string and a dict, so the current form-encoded callers keep working:

```python
# before
items: dict[str, dict[str, float]] = json.loads(items)
# after
items: dict[str, dict[str, float]] = frappe.parse_json(items)
```

The other arguments already cope with JSON types: `flt(20)` and `sbool(True)` work on numbers and booleans as well as strings. The `import json` at the top can go once nothing else uses it.

**App side:** `ManufactureEntryRequest` sent with `@Body` (summary, 2.4).

### A2. Make the expiry date required

[api.py:111](https://github.com/MLuleci/ButcherERP/blob/master/my_app/my_app/api.py#L111), [:120-123](https://github.com/MLuleci/ButcherERP/blob/master/my_app/my_app/api.py#L120), [:182-183](https://github.com/MLuleci/ButcherERP/blob/master/my_app/my_app/api.py#L182)

You decided that expiry is always needed. Today it's optional (`expiry_date=None`), and the batch is created without one if it's missing. Enforce it on the server, so an old app or a hand-made request can't create batches with no expiry:

```python
from frappe.utils import flt, getdate, sbool, today

def create_manufacture_entry(bom, qty, items, expiry_date, explode=False, is_finished=True, request_id=None):
	...
	if not expiry_date:
		frappe.throw(_("Expiry date is required"))
	try:
		expiry_date = date.fromisoformat(str(expiry_date))
	except ValueError:
		frappe.throw(_("Invalid expiry date"))
	if expiry_date <= getdate(today()):
		frappe.throw(_("Expiry date must be after today"))
```

The `if expiry_date:` guard around `fg_batch.expiry_date = expiry_date` can then go.

_Updated: as implemented, the signature keeps `expiry_date=None` and the code checks it (a missing argument otherwise gives an HTTP 500 `TypeError` before the function runs; see the implementation notes). So the snippet above and the full function below should read `expiry_date=None`, not a required argument._

**App side:** `expiryDate` is non-null in the request, and Start requires valid expiry days (summary, 2.10).

### A3. Return what the label and Summary screen need

[api.py:208-215](https://github.com/MLuleci/ButcherERP/blob/master/my_app/my_app/api.py#L208)

The label needs the item **name**, the **quantity** and its **unit**. The response today has none of these:

- `"item_name": fg_doc.name` is the item **code** (e.g. `SKU.012`), not its name.
- There is no `qty` or `uom`.

Build the response in a small helper. A4 reuses it to return the same answer for a repeated request, so both paths can't drift apart. It reads everything from the submitted Stock Entry, which is the record of what actually happened:

```python
def entry_summary(se) -> dict:
	"""The response for a submitted manufacture Stock Entry (also used for repeated requests)."""
	fg_row = next(row for row in se.items if row.is_finished_item)
	settings = frappe.get_single("My App Settings")
	return {
		"name": se.name,                                      # Stock Entry ID, printed on the label
		"batch_no": fg_row.batch_no,
		"item_code": fg_row.item_code,
		"item_name": fg_row.item_name,                        # display name, printed on the label
		"qty": flt(fg_row.qty),
		"uom": fg_row.uom,
		"production_date": str(se.posting_date),
		"expiry_date": str(frappe.db.get_value("Batch", fg_row.batch_no, "expiry_date")),
		"is_finished": fg_row.t_warehouse == settings.target_warehouse,
	}

# at the end of create_manufacture_entry
return entry_summary(se)
```

Using `se.posting_date` instead of `today()` gives the same value at creation time. It's also still correct when the summary is rebuilt later (A4).

`fg_row.uom` is the finished good's stock unit (lines 193-194). You chose the **BOM's unit** for the label. For a finished good it's the same value, because ERPNext doesn't allow a finished good's BOM unit to differ from its stock unit (the comment at line 192). So `fg_row.uom` is right.

**App side:** `ManufactureEntry` gains `itemCode`, `quantity` and `uom` (summary, 2.11, part 4).

### A4. Ignore repeated submissions (`request_id`)

When the request reaches the server but the reply is lost on weak Wi-Fi, the operator presses Submit again and a second Stock Entry is created. A double tap or OkHttp's automatic retry can do the same (01, finding 11). The app will send a `request_id` (a UUID made when the form opens, and renewed by "Same BOM again"). The server stores it on the Stock Entry and answers a repeat with the original result.

**Setup:** add a custom field to **Stock Entry**. Use a fixture in `my_app`, so it's versioned with the code:

| Property  | Value            | Why                                                                                       |
| --------- | ---------------- | ----------------------------------------------------------------------------------------- |
| Fieldname | `pda_request_id` |                                                                                           |
| Type      | Data             |                                                                                           |
| Unique    | ✔                | The database rejects a second entry with the same ID, even if two requests arrive at once |
| No Copy   | ✔                | Amending a cancelled entry must not copy the ID, or the amendment would clash with it     |
| Read Only | ✔                | Not for people to edit                                                                    |

```python
# at the very start of create_manufacture_entry
if request_id:
	existing = frappe.db.get_value("Stock Entry", {"pda_request_id": request_id, "docstatus": 1}, "name")
	if existing:
		return entry_summary(frappe.get_doc("Stock Entry", existing))

# when building the Stock Entry
se.pda_request_id = request_id
```

If two identical requests arrive at the same moment, both pass the check. The unique index then makes the second `se.insert()` fail, and Frappe rolls back that whole request, including the Batch it created. The app's retry then receives the first result.

`request_id` is optional in the signature, so existing callers keep working.

### A5. Give a clear error for a batch that doesn't exist

[api.py:158-159](https://github.com/MLuleci/ButcherERP/blob/master/my_app/my_app/api.py#L158)

```python
batch_doc = frappe.db.get_value("Batch", batch_no, ["item"], as_dict=True)
batch_item = batch_doc["item"]
```

For an unknown batch number, `get_value` returns `None`, and `batch_doc["item"]` crashes with a `TypeError`. The app gets an HTTP 500 with a Python traceback instead of a message. The app checks batches when they're scanned, but a batch can be deleted between scanning and submitting:

```python
batch_item = frappe.db.get_value("Batch", batch_no, "item")
if not batch_item:
	frappe.throw(_("Batch {0} not found").format(batch_no))
if batch_item != item_code:
	frappe.throw(_("Batch {0} is for item {1} not {2}").format(batch_no, batch_item, item_code))
```

### A6. Set up the API users

Not code, but the app depends on it:

- **One user per PDA.** In Frappe, an API key and secret belong to a **User**, so "one key per device" means one user per device, e.g. `pda-01@…`. A useful side effect: each Stock Entry's `owner` then records which device created it.
- **Read permission on BOM, Item and Batch.** _Updated: done by the PDA User role (A7). Give each PDA user only that role._ The BOM search now reads `item.shelf_life_in_days` through the link (summary, 2.10). Without read access to **Item**, that column fails, even though BOM access works. Also needed: whatever `create_manufacture_entry` requires (create and submit Stock Entry, create Batch).
- **Language: Bulgarian.** `frappe.throw(_("..."))` messages are translated into the _user's_ language. The app will show the server's error text to the operator (01, finding 6), so set these users' language to `bg` and add Bulgarian translations for the messages in `api.py`, either via the Translation doctype or `my_app/translations/bg.csv`. _Updated: the user's language setting alone is not enough. Frappe picks the request language before it checks the API key, so the app must send `Accept-Language: bg` (implementation notes; summary, 2.12). Bulgarian must also be enabled under Language; `my_app`'s `after_migrate` hook does this. The translations are in `my_app/translations/bg.csv`._
- **Test the BOM search columns.** You've confirmed `item.shelf_life_in_days as shelf_life_in_days` works with your own key. Repeat the same `curl` with a PDA user's key, to confirm its permissions. _Done: works as `pda-test` with only the PDA User role, including `uom.must_be_whole_number as must_be_whole_number`._

### A7. A "PDA User" role _(done)_

_Found while testing as `pda-test`._ With ERPNext's default roles, the PDA user can't read Batches:

- **Manufacturing User** has only **Select** on Batch, so list requests return just `name`, and other fields are silently dropped.
- **Stock User** has no Batch permission.
- Only **Item Manager** can read.

A single `/api/resource/Batch/<name>` fails with a `PermissionError`. `create_manufacture_entry` still works, because it reads batches on the server side, but the app's batch check after a scan, the Search list and the Details page all read Batches with the PDA's key (summary, 2.14 part 5).

**Fix:** a dedicated **PDA User** role, assigned to every PDA user _instead of_ Stock User and Manufacturing User. It's all in `my_app`, so `bench migrate` sets it up on a new site with no steps in the UI:

- the role itself is a fixture (`fixtures/role.json`), with no desk access;
- its permissions are set by the `after_migrate` hook in `my_app/install.py` (`PDA_PERMISSIONS`). This can't be a fixture: once a doctype has any Custom DocPerm rows, Frappe uses _only_ those and ignores its standard permissions, so a fixture with just the PDA row would remove every other role's access. The hook uses Frappe's `add_permission`, which copies the standard rules across first. Running migrate again changes nothing.

| Doctype                 | Read | Select | Create | Write | Submit | Why                                                                                                           |
| ----------------------- | ---- | ------ | ------ | ----- | ------ | ------------------------------------------------------------------------------------------------------------- |
| Batch                   | ✔    | ✔      |        |       |        | Scan check, Search, Details. The finished-goods batch is created with `ignore_permissions=True`               |
| Item                    | ✔    | ✔      |        |       |        | Shelf life and item names through link fields                                                                 |
| BOM                     | ✔    | ✔      |        |       |        | BOM search                                                                                                    |
| UOM                     | ✔    | ✔      |        |       |        | `uom.must_be_whole_number` in the BOM search                                                                  |
| Stock Entry             | ✔    | ✔      | ✔      | ✔     | ✔      | Submitting, and reading the ingredients on Details. Frappe's `submit()` saves the document, which needs Write |
| Serial and Batch Bundle | ✔    | ✔      | ✔      | ✔     | ✔      | ERPNext creates and submits one per batch row when the Stock Entry is submitted, as the user                  |
| Warehouse               | ✔    | ✔      |        |       |        | Stock Entry validation reads the warehouses from My App Settings                                              |

**Stock Entry is limited to manufacturing.** Create and Write on Stock Entry would also let a PDA key create other kinds of entry, such as a Material Receipt that adds stock (tested: it was allowed). A `has_permission` hook (`stock_entry_has_permission` in `install.py`) refuses create, write and submit unless the entry is a Manufacture entry from a BOM. It only applies to users whose only role is PDA User; a user who also has Stock User, say, is not restricted. Frappe's `has_permission` hooks can only refuse, never grant.

Notes:

- **Write is needed, despite the first plan.** Submitting through the API fails without Write on Stock Entry, and on Serial and Batch Bundle. With Write, a PDA user could edit their own _draft_ Manufacture entries, but `create_manufacture_entry` never leaves one.
- **No Cancel or Delete** on anything. Editing a submitted entry fails with `UpdateAfterSubmitError`.
- **Not needed after all:** Company and Stock Entry Type. Submitting works without them.
- **Don't use Item Manager.** It would also let the PDAs edit Items and Batches.
- **PDA users become Website Users.** Frappe does this because the role has no desk access. API keys still work, and `/app` redirects away, so a PDA user can't log in to the desk.
- **Assign the role by hand.** Users aren't in the repo, so giving each PDA user the PDA User role is a step when the user is created.
- **"User Permissions" are separate.** If the site restricts users to certain companies or warehouses, the PDA users need those too.

**Checks, run as `pda-test` with only PDA User** (all pass):

1. A Batch list with `fields=["name","item","item.item_name as item_name","batch_qty","stock_uom","expiry_date"]` returns every field.
2. `/api/resource/Batch/<name>` returns the document.
3. `/api/resource/Stock Entry/<name>` returns the document, with `items`.
4. The BOM search still returns shelf life and the whole-number flag.
5. A test `create_manufacture_entry` succeeds, and repeating its `request_id` returns the same entry.
6. Refused: cancelling, deleting or editing a submitted entry; editing a Batch, Item or BOM; creating a Material Receipt, or a Manufacture entry without a BOM; reading other doctypes such as Purchase Invoice and My App Settings.

---

## Part B: strongly recommended

### B1. Only allow submitted BOMs

[api.py:12-33](https://github.com/MLuleci/ButcherERP/blob/master/my_app/my_app/api.py#L12)

`get_bom` checks `is_active` but not whether the BOM is **submitted**. A draft BOM (`docstatus = 0`) can be active, but it can't be used for manufacturing, so the Stock Entry fails later with a less clear error. Add `"docstatus"` to the fields and check it:

```python
if bom_doc.docstatus != 1:
	frappe.throw(_("BOM {0} is not submitted").format(bom))
```

The app's search should filter on `docstatus = 1` too, so drafts don't appear in the list. _Done: submitted BOMs only._

### B2. Say _which_ items don't match the BOM

[api.py:139-140](https://github.com/MLuleci/ButcherERP/blob/master/my_app/my_app/api.py#L139)

"Items do not match BOM" doesn't tell the operator what to fix. List the differences:

```python
missing = bom_items.keys() - items.keys()
extra = items.keys() - bom_items.keys()
if missing or extra:
	frappe.throw(_("Items do not match BOM. Missing: {0}. Not in BOM: {1}").format(
		", ".join(bom_items[i].item_name for i in missing) or "-",
		", ".join(extra) or "-",
	))
```

### B3. Check each batch quantity

[api.py:157-167](https://github.com/MLuleci/ButcherERP/blob/master/my_app/my_app/api.py#L157)

The quantity from the request is passed straight into the Stock Entry row. A zero, a negative number or a non-number gives a confusing ERPNext error, or a silently wrong row. Use `flt` and check it:

```python
quantity = flt(quantity)
if quantity <= 0:
	frappe.throw(_("Quantity for batch {0} must be greater than zero").format(batch_no))
```

### B4. Fill in the new batch's dates, unit and source _(skipped: ERPNext does this itself; see the implementation notes)_

[api.py:180-184](https://github.com/MLuleci/ButcherERP/blob/master/my_app/my_app/api.py#L180)

The finished-goods Batch is created with only `item` and `expiry_date`. The "Print label" button on the **Details** route reprints a label from the Batch alone (summary, 2.11, part 4), so the Batch should carry the rest:

```python
fg_batch = frappe.new_doc("Batch")
fg_batch.item = bom_doc.item
fg_batch.expiry_date = expiry_date
fg_batch.manufacturing_date = today()
fg_batch.uom = fg_doc.stock_uom        # your custom field; skip if it's already filled by "Fetch From"
fg_batch.insert(ignore_permissions=True)

...
se.insert()
# Link the batch to the entry that produced it, so Details can show the Stock Entry ID
frappe.db.set_value("Batch", fg_batch.name, {
	"reference_doctype": "Stock Entry",
	"reference_name": se.name,
})
se.submit()
```

The **custom `uom`** is for the Details route and labels. The batch dialog now takes its unit from the BOM line instead (summary, 2.3). The app will make `BatchDetails.uom` optional, so a batch without it still decodes.

_Updated: skipped, so no custom `uom` field exists. ERPNext fills `manufacturing_date`, `reference_name` and `stock_uom` itself. The app reads `stock_uom` on Details and replaces `BatchDetails.uom` with it (summary, 2.12 point 2)._

---

## Part C: later

### C1. Tolerance from the server _(done; `0` means "use the default", see the implementation notes)_

You decided to hard-code ±5% in the app for now and send it from the server later (summary, 2.2). When you do:

**Where it comes from:** a custom field on **BOM**, e.g. `pda_tolerance_percent` (Percent). If it's empty, fall back to a `default_tolerance_percent` field on **My App Settings**, and then to 5:

```python
def get_tolerance_percent(bom: str) -> float:
	value = frappe.db.get_value("BOM", bom, "pda_tolerance_percent")
	if value is None or value == "":
		value = frappe.db.get_single_value("My App Settings", "default_tolerance_percent")
	return flt(value) if value not in (None, "") else 5.0
```

A plain `or` won't work here, because it would treat a deliberate 0% as "not set".

_Updated: as implemented, `0` does mean "not set" and falls back to the default. A Percent field can't be empty (it is stored as `NOT NULL DEFAULT 0`), so a deliberate 0% is not possible this way (implementation notes, "Custom fields"). The helper above is the first plan; the implemented one treats `0` like an empty value._

**Return it** from `get_bom_details`:

```python
"tolerance_percent": get_tolerance_percent(bom),
```

The app already reads `tolerance_percent` when it's present, with 5% as the default when it isn't. Always send a number, never `null` (summary, 2.2). _Done. Send the resolved value, never the raw `0`: if the server sent `0`, the app would use 0% (summary, 2.12 point 7)._

**Enforce it** in `create_manufacture_entry`, so an old app version can't submit totals outside the range:

```python
tolerance = get_tolerance_percent(bom)
for item_code, batches in items.items():
	required = flt(bom_items[item_code].qty)
	chosen = sum(flt(q) for q in batches.values())
	if abs(chosen - required) > required * tolerance / 100 + 0.0005:
		frappe.throw(_("{0}: {1} used, {2} required (±{3}%)").format(
			bom_items[item_code].item_name, chosen, required, tolerance))
```

The `0.0005` is the same small allowance the app uses, so values at the exact edge of the range get the same answer on both sides.

---

## `create_manufacture_entry` with parts A and B applied

For reference, the whole function with A1–A5 and B2–B4 applied. B1 lives in `get_bom`. `entry_summary` is in A3.

_Updated: this is the first plan. As implemented: `expiry_date=None` in the signature (A2); the `fg_batch.manufacturing_date` and `fg_batch.uom` lines and the `frappe.db.set_value("Batch", ...)` call are left out (B4 skipped); and the tolerance check (C1) and the whole-number checks (`validate_fg_qty`, batch quantities, the ±0.5 range) are added. B3's quantity check runs in the tolerance loop, before any Batch is created, so a single batch of 0 gets B3's message rather than the tolerance one. See the implementation notes._

```python
@frappe.whitelist()
def create_manufacture_entry(bom, qty, items, expiry_date, explode=False, is_finished=True, request_id=None):
	"""Create and submit a Manufacture Stock Entry."""
	# A repeat of a request that already succeeded (lost reply, double tap, retry)
	if request_id:
		existing = frappe.db.get_value("Stock Entry", {"pda_request_id": request_id, "docstatus": 1}, "name")
		if existing:
			return entry_summary(frappe.get_doc("Stock Entry", existing))

	qty = flt(qty)
	if not qty:
		frappe.throw(_("Invalid quantity"))

	explode = sbool(explode)
	is_finished = sbool(is_finished)

	if not expiry_date:
		frappe.throw(_("Expiry date is required"))
	try:
		expiry_date = date.fromisoformat(str(expiry_date))
	except ValueError:
		frappe.throw(_("Invalid expiry date"))
	if expiry_date <= getdate(today()):
		frappe.throw(_("Expiry date must be after today"))

	# { [item_code]: { [batch_no]: quantity } }, as a JSON body or a JSON string
	items: dict[str, dict[str, float]] = frappe.parse_json(items)
	bom_doc = get_bom(bom)
	fg_doc = get_fg(bom_doc.item)

	bom_items = get_bom_items_as_dict(
		bom,
		bom_doc.company,
		qty=qty,
		fetch_exploded=explode,
		fetch_qty_in_stock_uom=False
	)

	# Verify included items match BOM:
	missing = bom_items.keys() - items.keys()
	extra = items.keys() - bom_items.keys()
	if missing or extra:
		frappe.throw(_("Items do not match BOM. Missing: {0}. Not in BOM: {1}").format(
			", ".join(bom_items[i].item_name for i in missing) or "-",
			", ".join(extra) or "-",
		))

	settings = frappe.get_single("My App Settings")
	if not settings.source_warehouse or not settings.target_warehouse:
		frappe.throw(_("Please configure source and target warehouses in My App Settings."))

	# Stock Entry common details:
	se: StockEntry = frappe.new_doc("Stock Entry")
	se.purpose = "Manufacture"
	se.from_bom = 1
	se.bom_no = bom
	se.company = bom_doc.company
	se.fg_completed_qty = qty
	se.pda_request_id = request_id

	# Add ingredients:
	for item_code, batches in items.items():
		item = bom_items.get(item_code)
		for batch_no, quantity in batches.items():
			quantity = flt(quantity)
			if quantity <= 0:
				frappe.throw(_("Quantity for batch {0} must be greater than zero").format(batch_no))

			batch_item = frappe.db.get_value("Batch", batch_no, "item")
			if not batch_item:
				frappe.throw(_("Batch {0} not found").format(batch_no))
			if batch_item != item_code:
				frappe.throw(_("Batch {0} is for item {1} not {2}").format(batch_no, batch_item, item_code))

			se.append(
				"items",
				{
					"item_code": item_code,
					"qty": quantity,
					"uom": item.uom or item.stock_uom,
					"stock_uom": item.stock_uom,
					"conversion_factor": flt(item.get("conversion_factor")) or 1.0,
					# We always take from the source warehouse for simplicity
					"s_warehouse": settings.source_warehouse,
					"is_finished_item": 0,
					"batch_no": batch_no,
					"use_serial_batch_fields": 1,
				},
			)

	# Finished-goods batch:
	fg_batch: Batch = frappe.new_doc("Batch")
	fg_batch.item = bom_doc.item
	fg_batch.expiry_date = expiry_date
	fg_batch.manufacturing_date = today()
	fg_batch.uom = fg_doc.stock_uom  # custom field; drop if filled by "Fetch From"
	fg_batch.insert(ignore_permissions=True)

	# Add finished-goods:
	se.append(
		"items",
		{
			"item_code": bom_doc.item,
			"qty": qty,
			# BOM can't use a UOM different from the stock UOM for finished-goods
			"uom": fg_doc.stock_uom,
			"stock_uom": fg_doc.stock_uom,
			"conversion_factor": 1.0,
			# If this finished-good is a sub-assembly itself, send it to the source warehouse
			"t_warehouse": settings.target_warehouse if is_finished else settings.source_warehouse,
			"is_finished_item": 1,
			"batch_no": fg_batch.name,
			"use_serial_batch_fields": 1,
		},
	)

	se.set_stock_entry_type()
	se.insert()
	frappe.db.set_value("Batch", fg_batch.name, {
		"reference_doctype": "Stock Entry",
		"reference_name": se.name,
	})
	se.submit()

	return entry_summary(se)
```

---

## Testing with `curl`

Use a PDA user's key (A6, with only the PDA User role from A7) and a test site. The JSON body is what the app will send:

```sh
curl -sS -X POST "https://<site>/api/method/my_app.my_app.api.create_manufacture_entry" \
  -H "Authorization: token <api_key>:<api_secret>" \
  -H "Accept-Language: bg" \
  -H "Content-Type: application/json" \
  -d '{
        "bom": "BOM-0001",
        "qty": 20,
        "items": {"SKU.001": {"BATCH-0001": 10.2}, "SKU.002": {"BATCH-0002": 9.9}},
        "expiry_date": "2026-11-01",
        "explode": false,
        "is_finished": true,
        "request_id": "3f0c9a3e-0000-4000-8000-000000000001"
      }'
```

Things to check:

1. **The first call** returns `{"message": {...}}` with `item_name` as the product name, plus `qty` and `uom`.
2. **The same command again** returns the _same_ `name` and `batch_no`, and doesn't create a second Stock Entry (A4).
3. **A changed `request_id`** creates a new entry.
4. **These variations** each give a readable error in `_server_messages`, not a 500:
   - leaving out `expiry_date` (A2)
   - an unknown batch (A5)
   - a missing ingredient (B2)
   - a quantity of `0` (B3)
   - a total outside the tolerance (C1)
   - a fractional quantity in a whole-number unit, for the finished good or an ingredient

   With `Accept-Language: bg`, the messages are in Bulgarian. _All of 1–6 have been run on the dev site._

5. **The new Batch** has `manufacturing_date`, `stock_uom` and `reference_name` filled in. ERPNext does this itself, which is why B4 was skipped. Also check that the response carries `must_be_whole_number`.
6. **The old form-encoded call** still works, if anything else uses it (A1).

---

## Implementation notes (found while implementing)

Things you found in ButcherERP while working through this file. Kept here so they aren't rediscovered.

### Requests and translations

- **Language with API keys.** Frappe picks the request language _before_ it checks the API key, so the API user's language setting is ignored. The app must send `Accept-Language: bg` (summary, 2.12). Without the header, messages come back in the site's default language (English). Bulgarian also has to be enabled under **Language**, or the header is ignored; `my_app`'s `after_migrate` hook enables it.
- **Translation cache.** After changing `bg.csv`, run `bench clear-cache`, or the old translations are still served.
- **Missing arguments give a 500.** If a request leaves out a required argument, Frappe fails with a `TypeError` (HTTP 500) before the function runs. Give such arguments a default (`expiry_date=None`) and check them in code, so the caller gets a readable message. This revises A2's `expiry_date` (no default) in the signature.

### ERPNext data rules

- **BOM unit = stock unit.** A BOM's unit is always the item's stock unit, and ERPNext resets it on every save. The stock unit can't be changed once the item has transactions.
- **Whole-number units.** A unit rejects fractions when **Must Be Whole Number** is ticked on its UOM (Nos on the dev site; Kg isn't). ERPNext checks this on every Stock Entry row. 1 meatball needs 0.12 onions, so the smallest workable quantity on the test BOM is 25. See the summary, 2.13, for the app side. The tolerance question is settled: ±0.5 for whole-number units (see "Whole-number units (done)" below).
- **Batches fill themselves in.** ERPNext sets a new Batch's `manufacturing_date`, its link to the Stock Entry (`reference_doctype` / `reference_name`) and its `stock_uom` without any code, so **B4 was skipped**. The app reads `stock_uom` instead of a custom `uom` (summary, 2.12 point 2).

### Custom fields

- **Percent fields can't be empty.** They're stored as `NOT NULL DEFAULT 0`, so "not set" and 0% look the same. C1 therefore treats `0` as "use the default". A deliberate 0% tolerance isn't possible this way. If it's ever needed, use a Float field, which can hold NULL, or a separate "Use default tolerance" check field.
- **Unique fields and empty values.** An empty value is stored as `NULL`, so entries without a `request_id` never clash with each other (A4).
- **Simultaneous duplicates.** The second request fails on a database lock (`QueryDeadlockError`, HTTP 500) rather than on the unique index. Either way no duplicate is created, and a retry with the same `request_id` returns the original entry (summary, 2.12 point 4).

### Desk page

- **Date format.** `frappe.datetime.add_days` returns a full timestamp, which `date.fromisoformat` rejects. Format the date as `YYYY-MM-DD`. The old `toISOString()` used UTC, so it could give the wrong day around midnight.
- **Decimals.** Adding up batch quantities can give values like `0.30000000000000004`. The tolerance message rounds them to 3 places, and `get_bom_details` rounds the ingredient quantities it returns to 3 places.
- **Whole-number rounding.** The page rounds whole-number ingredient quantities to the nearest whole number before showing and submitting them. If a whole-number ingredient needs less than 0.5, nothing can be submitted: 0 is rejected, and 1 is more than 0.5 away. That means the batch is too small for the BOM.

### Deploying and dev tools

- **`bench migrate` overwrites the print format.** It re-imports the **Batch Label** print format from the fixture, which replaces any changes made to it on the site. Edit it in the fixture, or export it after editing on the site.
- **DocType changes need a newer `modified`.** Frappe only reloads a changed DocType JSON when its `modified` timestamp is newer than the one in the database.
- **`bench execute` paths.** It runs from inside `sites/`, so file paths passed to it must be absolute.
- **Custom DocPerm replaces standard permissions.** Once a doctype has any Custom DocPerm rows, Frappe uses only those. So role permissions can't be shipped as a fixture of just the new rows; see A7.
- **Submitting needs Write.** Frappe's `submit()` saves the document, and saving checks Write. Submit alone isn't enough through the API.
- **Hooks are cached.** After adding or changing a hook in `hooks.py`, run `bench clear-cache` (a restart also does it).

### Whole-number units (done)

`must_be_whole_number` is returned on `get_bom_details` (BOM and each ingredient) and on the `create_manufacture_entry` response. The server rejects:

- a fractional finished-good quantity in a whole-number unit (`validate_fg_qty`)
- a fractional batch quantity for a whole-number ingredient

Whole-number ingredients get a ±0.5 range instead of the percentage. For the app side, see the summary, 2.13.

### For the Search and Details pages (summary, 2.14)

No new server method is needed, but the PDA user needs the **PDA User role** (A7). Checks:

1. **Read access to Stock Entry** through `/api/resource/Stock Entry/<name>`. Details shows the ingredients from its `items` child table. _Done (A7): returns the document with `items` as `pda-test`._
2. **Link fields in filters**, for the product-name search. _Confirmed: works, case-insensitive, and returns full rows as `pda-test` with the PDA User role._
3. **Manufactured batches: no server change.** `reference_doctype = Stock Entry` doesn't single out production (ERPNext sets it on raw-material batches from Material Receipts too, e.g. BEEF.0001 from MAT-STE-2026-00001). The app filters on the item instead: `["item.default_bom","is","set"]`, i.e. batches of items that have a default BOM. That covers batches made on the PDA, the desk page and in web-UI corrections, with no custom field and no patch.
4. **The full search request**, as `pda-test` _(tested; works)_:

   ```sh
   curl -G "https://<site>/api/resource/Batch" -H "Authorization: token <key>:<secret>" \
     --data-urlencode 'fields=["name","item","item.item_name as item_name","item.default_bom as default_bom","batch_qty","stock_uom","manufacturing_date","expiry_date","reference_name"]' \
     --data-urlencode 'filters=[["item.default_bom","is","set"],["manufacturing_date",">=","2026-09-23"],["expiry_date",">=","2026-10-23"]]' \
     --data-urlencode 'or_filters=[["name","like","%beef%"],["item.item_name","like","%beef%"]]' \
     --data-urlencode 'order_by=expiry_date asc, name desc' \
     --data-urlencode 'limit_start=0' \
     --data-urlencode 'limit_page_length=30'
   ```

   Without `limit_page_length`, Frappe returns at most **20** rows. _Tested: paging with `limit_start` gives consecutive pages with no gaps or repeats (checked at 5 per page; the dev site has only 14 batches, so the 20-row default itself couldn't be seen). `or_filters` with a link field works, and combines as `filters` AND (any `or_filters`)._

   BEEF.0001 (a raw material) should not appear, and the Details request is the same with `filters=[["name","=","<batch>"]]` and no `or_filters`. _Both confirmed._

   **Product-name sort:** `order_by=item_name asc, name desc` works, sorting on the `item.item_name as item_name` alias. `order_by=item.item_name asc, name desc` works too. ``order_by=`tabItem`.item_name asc, name desc`` does **not**: it fails with "Unknown column 'tabItem.item_name'". Use the alias.
