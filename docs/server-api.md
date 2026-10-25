# Server and API

The backend is ERPNext (Frappe). Custom server code lives in a separate repo, <https://github.com/MLuleci/ButcherERP>, in the `my_app` Frappe app (`my_app/my_app/api.py`, `my_app/install.py`, `hooks.py`, fixtures, `translations/bg.csv`).

## Server setup (manual steps)

1. **One Frappe user per PDA** (e.g. `pda-01@…`), with an API key and secret. The Stock Entry's `owner` then records which device made it.
2. **Give each PDA user only the PDA User role.** It has to be assigned by hand, since users aren't in the repo. Without it, list requests silently drop fields (the app shows "Unexpected response"), and other requests fail with 403 or `PermissionError`.
3. **User Permissions:** if the site restricts companies or warehouses with them, give the PDA users those too.
4. **My App Settings** (a single doctype): set `source_warehouse` and `target_warehouse`, and optionally `default_tolerance_percent`.
5. **`bench migrate`** loads the fixtures, the role, its permissions and Bulgarian. After changing `bg.csv` or `hooks.py`, run `bench clear-cache`.

For testing, use a PDA user with only the PDA User role, e.g. `pda-test`.

## The PDA User role

- **Where it's defined:** the role itself is a fixture (`fixtures/role.json`), with no desk access.
- **Where its permissions are set:** the `after_migrate` hook in `my_app/install.py` (`PDA_PERMISSIONS`, using `add_permission`). This can't be a fixture: once a doctype has any Custom DocPerm rows, Frappe uses only those, and every other role would lose access.
- **What it can do:**
  - Batch, Item, BOM, UOM and Warehouse: Read and Select.
  - Stock Entry and Serial and Batch Bundle: Read, Select, Create, Write and Submit. Write is needed because `submit()` saves the document.
  - No Cancel or Delete anywhere.
- **The `has_permission` hook** (`stock_entry_has_permission`) refuses creating, writing or submitting a Stock Entry unless it's a Manufacture entry from a BOM. It only applies to users whose only role is PDA User.
- **Why not the default ERPNext roles:**
  - Manufacturing User only has Select on Batch, so lists return just `name`.
  - Stock User has no Batch permission at all.
  - Item Manager is too broad.
- **Side effects:** PDA users become Website Users. API keys still work, but `/app` redirects away.
- **Bulgarian:** `after_migrate` also enables it.

## Custom methods

### `my_app.my_app.api.get_bom_details(bom, qty, explode)`
- **Returns** the BOM:
  - `name`, `qty`, `item_code`, `item_name`, `uom`, `stock_uom`, `shelf_life_in_days`, `tolerance_percent`, `must_be_whole_number`;
  - `items[]`, each with `qty`, `item_code`, `item_name`, `uom`, `stock_uom`, `conversion_factor` and `must_be_whole_number`.
- **Quantities** are rounded to 3 decimals.
- **The ingredient list** is built with ERPNext's `get_bom_items_as_dict`, keyed by item code, so an item can't appear twice.
- **A fractional finished-good quantity** in a whole-number unit is rejected (`validate_fg_qty`).

### `my_app.my_app.api.create_manufacture_entry(bom, qty, items, expiry_date=None, explode=False, is_finished=True, request_id=None)`
- **What it does:** creates and submits a Manufacture Stock Entry, and creates the finished-goods Batch.
- **`items`** is `{item_code: {batch_no: qty}}`, read with `frappe.parse_json`, so it can be a JSON body or a JSON string.
- **`expiry_date`** is required in code, but optional in the signature (see the notes below). It must be `YYYY-MM-DD` and after today.
- **Item keys** must exactly match the BOM's lines. The error lists `Missing:` and `Not in BOM:`.
- **Each batch** must exist and belong to the right item ("Batch {0} is for item {1} not {2}"), with a quantity above 0.
- **Tolerance:** a line is rejected when `abs(chosen − required) > required × tol/100 + 0.0005`. Whole-number ingredients use ±0.5 instead, and must be whole.
- **Warehouses** come from My App Settings. If `is_finished` is false, the finished good goes to the source warehouse.
- **Ingredient rows** use the BOM line's `uom` and `conversion_factor`.
- **Response** (`entry_summary`, wrapped in `message`):
  - `name` (the Stock Entry ID), `batch_no`, `item_code`, `item_name` (the display name), `qty`, `uom`, `must_be_whole_number`;
  - `production_date` (the posting date), `expiry_date` (read from the Batch), `is_finished`.

**Dedup by `request_id`:**
- **The field:** a Stock Entry custom field `pda_request_id` (Data, Unique, No Copy, Read Only; shipped as a fixture).
- **On every call:** the server first looks for a submitted entry with that ID. If one exists, it returns that entry's summary and creates nothing.
- **Empty IDs** are stored as NULL, so entries without one never clash.
- **Two identical requests at the same moment:** the second fails with a 500 (`QueryDeadlockError`). No duplicate is created, and the app treats a 5xx as "unknown" and retries with the same ID.

**Tolerance:**
- **Where it comes from:** a BOM custom field `pda_tolerance_percent` (Percent).
- **If it's 0:** the server uses `default_tolerance_percent` from My App Settings, and failing that 5.
- **So 0 means "not set":** Percent fields are `NOT NULL DEFAULT 0`, so a deliberate 0% isn't possible. The server always sends the resolved value.

**Server dev notes:**
- **Missing required arguments:** Frappe answers with a 500 `TypeError` before the function runs. Give such arguments a default and check them in code.
- **`bench migrate`** re-imports the Batch Label print format fixture, overwriting edits made on the site.
- **DocType JSON** is only reloaded when its `modified` timestamp is newer than the one in the database.
- **`bench execute`** runs from `sites/`, so pass it absolute file paths.

## What the app sends (`network/ErpService.kt`, `data/NetworkErpRepository.kt`)

`ErpServiceInterceptor` handles every ERP request:
- **URL:** it replaces the placeholder base `http://localhost/` with the Settings URL. The URL must be HTTPS, and any path prefix in it is kept.
- **Headers:** it adds `Authorization: token <key>:<secret>` and `Accept-Language: bg|en`.
- **Invalid settings:** it throws `InvalidSettingsException` before anything is sent.

Responses are decoded with `erpJson = Json { ignoreUnknownKeys = true }`.

| Call | Request | Wrapper | Used for |
|---|---|---|---|
| `searchBom` | `GET /api/resource/BOM`. `fields`: `name, item, item_name, uom, is_default, item.shelf_life_in_days as shelf_life_in_days, uom.must_be_whole_number as must_be_whole_number`. `filters`: `item_name like %q%`, `is_active = 1`, `docstatus = 1`. `order_by=item_name asc, name asc`, `limit_page_length=50` | `DataWrapper<List<BomSearchResultItem>>` | BOM picker on Start |
| `getBatch` | `GET /api/resource/Batch` with `filters=[["name","=",batch]]` and `BATCH_FIELDS`. A one-row list, so link fields come through. An empty result becomes a 404 `ErpError.Http` | `DataWrapper<List<BatchDetails>>` | Batch dialog, Search scans and Enter, Details |
| `searchBatches(query, filters, start)` | `GET /api/resource/Batch`. `filters`: `item.default_bom is set`, plus optional `manufacturing_date >= today−N` and `expiry_date >= today`. `or_filters` (only with text): `name like p`, `item.item_name like p`. `order_by`: `<field> <dir>, name desc`. `limit_start`, `limit_page_length=30` | same | Search list |
| `getStockEntry` | `GET /api/resource/Stock Entry/{id}` | `DataWrapper<StockEntryDetails>` | Details: "ingredients used" are the rows with `is_finished_item == 0` |
| `getBomDetails` | `POST /api/method/my_app.my_app.api.get_bom_details`, form fields `bom`, `qty` (via `roundForErp`), `explode` (`"true"`/`"false"`) | `MessageWrapper<BomDetails>` | Ingredients, tolerance, whole-number flags |
| `createManufactureEntry` | `POST /api/method/my_app.my_app.api.create_manufacture_entry`, JSON body `ManufactureEntryRequest`: `bom`, `qty`, `items`, `expiry_date` (`YYYY-MM-DD`), `explode`, `is_finished`, `request_id`. Every quantity goes through `roundForErp()` | `MessageWrapper<ManufactureEntry>` | Submit |

`BATCH_FIELDS` = `name, item, item.item_name as item_name, item.default_bom as default_bom, batch_qty, stock_uom, manufacturing_date, expiry_date, reference_name`.

## Errors (`network/ErpError.kt`)

`apiCall` rethrows cancellation and maps everything else with `ErpError.from()`:

| Cause | `ErpError` |
|---|---|
| `InvalidSettingsException` | `NotConfigured`: nothing was sent |
| other `IOException` | `Network` |
| `SerializationException` | `UnexpectedResponse`. This is also the symptom of missing role permissions, when Frappe drops fields |
| HTTP 401 | `Unauthorized` |
| HTTP 403 | `Forbidden(message)`: the role is missing, or the `has_permission` hook refused |
| other HTTP | `Http(code, message)`, with `isNotFound` for 404 |
| anything else | `UnexpectedResponse` |

- **Server messages:** `frappe.throw` arrives as HTTP 417 with a `_server_messages` body. That's a JSON string holding a list of JSON strings, each an object with a `message`. `serverMessage()` unwraps both layers, strips HTML and decodes entities. The message is in Bulgarian when `Accept-Language: bg` is sent, because Frappe picks the language before it checks the key.
- **`isOutcomeUnknown`** is `Network`, or `Http` with a code of 500 or more. Submit then offers a retry with the same request ID ([business-rules.md](business-rules.md)). That's also why OkHttp's `retryOnConnectionFailure` is safe to leave on.

## Frappe and ERPNext facts the code relies on

- **Wrappers:** `/api/method/*` wraps results in `message`, and `/api/resource/*` in `data`. Bodies can carry extra keys (`_server_messages`, `_debug_messages`), so decoding ignores unknown keys.
- **Check fields** come from the list API as `0`/`1` integers (`is_default`, `must_be_whole_number`, `is_finished_item`). Those models keep `Int` and expose getters (`isDefaultBom`, `isWholeNumber`). The custom methods return real booleans.
- **Page size:** without `limit_page_length`, a list returns at most 20 rows. `limit_start` paging needs a stable tie-break, hence `, name desc`. There's no total count.
- **Link fields:** `link.field as alias` reads and filters them (`item.item_name`, `item.default_bom`, `uom.must_be_whole_number`). Lookups through child-table rows are rejected, which is why ingredient flags come from `get_bom_details`.
- **Sorting:** by a link value, use the alias (`item_name`). `` `tabItem`.item_name `` fails with "Unknown column".
- **Single-document requests** (`/api/resource/<Doctype>/<name>`) ignore `fields`. They return the whole document with its child tables, but never link-fetched values, so batch lookups use a one-row list instead.
- **Case:** batch names and LIKE on item names match ignoring case.
- **LIKE escaping:** `\`, `%` and `_` are escaped with a backslash (`likeEscape`), which relies on MariaDB's default escape character. Don't use `@QueryMap(encoded = true)`: the server would decode `%be` in `%beef%` as a byte.
- **Batches:** ERPNext fills a new Batch's `manufacturing_date`, `stock_uom` and `reference_doctype`/`reference_name` (the Stock Entry link). There's no custom `uom` field on Batch.
- **A BOM's unit** is always the item's stock unit.
- **"Made here"** is decided by the item having a default BOM, not by `reference_doctype = Stock Entry`, because Material Receipts set that too.
- **Dates:** `expiry_date` must be a plain local `YYYY-MM-DD` date. In MariaDB, NULL dates sort first when ascending, and `>=` filters leave them out.
