# Review 01: data layer, network, models, build config, tests

Scope: `data/*`, `network/ErpService*.kt`, `model/*`, Gradle files, manifest and backup XML, R8 rules, test folders.

I did not change any source files. I fetched the server code at `my_app/api.py` from GitHub (it is public) and checked each lead against it. That file is what the server really does with our requests, so most of the "Bug" items below are confirmed against it and not guessed.

Severity tags: **[Bug]** is wrong now, **[Risk]** will likely hurt in real use, **[Improvement]** is worth doing, **[Tidy-up]** is housekeeping.

Items that are knowingly unfinished (from the "WIP" commit) are marked **(known WIP)**.

---

## Summary table

| # | Severity | Title |
|---|----------|-------|
| 1 | Bug | `createManufactureEntry` sends `items` as Kotlin `toString()`, server needs JSON |
| 2 | Bug | `createManufactureEntry` response is not unwrapped from `{"message": ...}` |
| 3 | Bug | `getBatch` expects a bare object, Frappe returns `{"data": ...}` (and `uom` is probably not a Batch field) |
| 4 | Bug | `%` in the search filter is passed raw because of `encoded = true` |
| 5 | Bug | Unit tests do not compile (`BatchDetails` is missing `uom`) |
| 6 | Risk | Server error messages never reach the user |
| 7 | Risk | Strict `Json` will fail on any extra key from the server |
| 8 | Risk | Search results are cut off at 20 rows (Frappe default page size) |
| 9 | Risk | API key (and URL) are included in Android cloud backup |
| 10 | Risk | Auth header is `token <one string>`; settings only have one field; whitespace not trimmed |
| 11 | Risk | OkHttp can silently retry the POST that creates stock entries |
| 12 | Risk | Cleartext HTTP is allowed for the whole app |
| 13 | Known WIP | `DefaultAppContainer` still returns the fake repository, and builds all network objects eagerly |
| 14 | Risk | `Float` for quantities |
| 15 | Improvement | JSON in `searchBom` is built by string interpolation |
| 16 | Improvement | No tests, and what to test first |
| 17 | Improvement | Release build: no minify, no signing, empty keep rules |
| 18 | Tidy-up | Gradle catalog has many duplicates and unused dependencies; obsolete `composeOptions` |
| 19 | Improvement | Interceptor uses `runBlocking` on every request |
| 20 | Tidy-up | `PdaSettings.baseUrl` default is never used |
| 21 | Tidy-up | Typo, unused imports, stale comments, `isDefault: Int` |
| 22 | Tidy-up | `FakeErpRepository` quirks (one of them can crash the list) |
| 23 | Improvement | Server-side oddity: `item_name` in the create response contains the item code |

*Updated: items 1, 2 and 23 are now handled on the server side (JSON body with `request_id`, `entry_summary` response with `item_name`, `item_code`, `qty`, `uom`; see 04). The app side still needs the typed `@Body` request, the unwrapping, and `Accept-Language: bg` (summary, 2.4 and 2.12). Item 3 now reads `stock_uom`, not `uom`; item 11 is harmless for this endpoint thanks to `request_id`; item 12 is decided (HTTPS, `usesCleartextTraffic="false"`). Item 14: switch to `Double` (summary, 2.2).*

---

## 1. `createManufactureEntry` sends `items` as Kotlin `toString()`, server needs JSON **[Bug]**

[ErpService.kt:37](../app/src/main/java/bg/butcher/pda/network/ErpService.kt#L37)

A `@Field` always turns the value into text with `toString()`. A `Map<String, Map<String, Float>>` becomes `{SKU.001={B1=1.0}}`, which is not JSON. The server does `items = json.loads(items)` ([api.py line 126](https://github.com/MLuleci/ButcherERP/blob/master/my_app/my_app/api.py#L126)), so every call to create a manufacture entry would fail with a JSON decode error. This confirms the lead.

The other fields are fine. `LocalDate.toString()` gives ISO `2026-09-20`, which matches `date.fromisoformat`. A `null` `@Field` is skipped by Retrofit, so the server falls back to `expiry_date=None`. *(Updated: expiry is now always required and sent; the server rejects a missing one with a readable message, 04, A2.)* Booleans become `"true"`/`"false"`, and `sbool` handles those. Quantities as `"1.0"` are fine for `flt()`.

Before:
```kotlin
@Field("items") items: Map<String, Map<String, Float>>,
```
*Updated: superseded. The server now accepts a JSON body (04, A1), so send a typed `ManufactureEntryRequest` with `@Body` instead of `@Field`s and a hand-encoded string (summary, 2.4). The request also carries a `request_id`. The snippet below is the older, form-field fix.*

After (encode in the repository, keep the service dumb):
```kotlin
// ErpService
@Field("items") itemsJson: String,

// NetworkErpRepository
val itemsJson = Json.encodeToString(items)   // {"SKU.001":{"B1":1.0}}
service.createManufactureEntry(bom, quantity, itemsJson, expiryDate, explode, isFinished)
```
Note the server also requires `items.keys() == bom_items.keys()` (api.py line 139), so every BOM ingredient must be present in the map, even if the operator used zero of it. Worth making sure the form sends all of them. *(Updated: with the tolerance rule, every ingredient needs a chosen total inside the allowed range, so each ingredient has at least one batch in practice; the server's mismatch message now names the missing items, 04, B2.)*

## 2. `createManufactureEntry` response is not unwrapped **[Bug]**

[ErpService.kt:41](../app/src/main/java/bg/butcher/pda/network/ErpService.kt#L41), [NetworkErpRepository.kt:60](../app/src/main/java/bg/butcher/pda/data/NetworkErpRepository.kt#L60)

Every Frappe `/api/method/...` response is wrapped as `{"message": <return value>}`. `getBomDetails` handles this with `BomDetailsWrapper`, but `createManufactureEntry` is declared to return `ManufactureEntry` directly. The decoder will see `{"message": {...}}`, find no `name` key, and throw. Even if it did not, the strict `Json` (item 7) would reject the unknown `message` key.

Make the wrapper generic so both calls use it:
```kotlin
@Serializable
data class MethodResponse<T>(val message: T)

suspend fun getBomDetails(...): MethodResponse<BomDetails>
suspend fun createManufactureEntry(...): MethodResponse<ManufactureEntry>
// repository: service.createManufactureEntry(...).message
```
`BomDetailsWrapper` can then be deleted (the test fake `FakeErpService` uses it, so update that too).

## 3. `getBatch` response shape (and the `uom` field) **[Bug]**

[ErpService.kt:21-22](../app/src/main/java/bg/butcher/pda/network/ErpService.kt#L21), [BatchDetails.kt](../app/src/main/java/bg/butcher/pda/model/BatchDetails.kt)

Two separate problems, both from what I know of the standard Frappe REST API (I could not call your server):

1. `GET /api/resource/Batch/{name}` returns `{"data": {...}}`, the same envelope as the list call. `getBatch` is declared to return `BatchDetails` directly, so it would fail the same way as item 2. Use a `DataResponse<T>(val data: T)` wrapper (the existing `BomSearchResult` is the same idea for lists).
2. The `fields` query parameter is only honoured on the list endpoint (`/api/resource/Batch`). The single-document endpoint ignores it and returns the whole document, including many keys we do not model. That is another reason for item 7.
3. *Updated again: ERPNext fills the Batch's `stock_uom` itself, and for manufactured items it equals the BOM unit. The app reads `stock_uom` on Details, and the batch dialog uses the BOM line's unit (summary, 2.3 and 2.12).* (Original note: the standard ERPNext Batch doctype has `stock_uom`, not `uom`.) `BatchDetails.uom` is required, so unless you added a custom `uom` field, decoding fails with "missing field uom". Check a real response. If you only need item and name, make `uom` optional or read `stock_uom`.

*Updated: the Batch is looked up through the standard resource endpoint and read as `stock_uom`; no custom `uom` field was added (04, B4 skipped).*

A simple alternative that keeps `fields` working: call the list endpoint with `filters=[["name","=","<batch>"]]` and take the first row, or just use a whitelisted method in `my_app` (the other two calls already do).

## 4. `%` in the search filter is passed raw because of `encoded = true` **[Bug]**

[ErpService.kt:19](../app/src/main/java/bg/butcher/pda/network/ErpService.kt#L19), [NetworkErpRepository.kt:27](../app/src/main/java/bg/butcher/pda/data/NetworkErpRepository.kt#L27)

I checked how OkHttp treats `addEncodedQueryParameter` (which is what `encoded = true` uses). Findings:

- Spaces, `"`, `'`, `<`, `>`, `#`, `&`, `=` are still percent-encoded. So the JSON quotes and spaces do not break the URL, and the lead's worry about those is mostly wrong.
- `[`, `]`, `{`, `}`, `,`, `:` and `+` are left as they are. OkHttp and Frappe accept them, so that is also fine (a `+` in the query would be read as a space by the server).
- A `%` is left as it is. The filter contains `%` as the LIKE wildcard, so the URL contains `%<query>%`. The server URL-decodes it, and anything that looks like `%` followed by two hex digits is turned into a byte. Searching for `beef`, `cafe`, `abc`, `dead` and so on produces `%be`, `%ca`, `%ab`, `%de` and the server decodes those as raw bytes (garbage or invalid UTF-8) instead of "percent, b, e". Words that do not start with two hex letters work, which makes this a bug that looks random to the user.

The lead's second half is right: a `"` or `\` in the query breaks the JSON itself (item 15).

Fix: drop `encoded = true` and let Retrofit/OkHttp encode everything.
```kotlin
// before
suspend fun seachBom(@QueryMap(encoded = true) options: Map<String, String>): BomSearchResult
// after
suspend fun searchBom(@QueryMap options: Map<String, String>): BomSearchResult
```

## 5. Unit tests do not compile **[Bug]**

[FakeDataSource.kt:56-60](../app/src/test/java/bg/butcher/pda/fake/FakeDataSource.kt#L56)

`BatchDetails(name, item, itemName)` is missing the required `uom` argument, so `./gradlew test` fails at compile time. The other fakes match the current interfaces today (`FakeErpService` matches `ErpService`, including the misspelt `seachBom`; `FakeNetworkErpRepository` matches `ErpRepository`). They will break again as soon as you apply items 1-3 and 21. Details in item 16.

## 6. Server error messages never reach the user **[Risk]**

[NetworkErpRepository.kt:14-18](../app/src/main/java/bg/butcher/pda/data/NetworkErpRepository.kt#L14)

`apiCall` is correct about cancellation (rethrowing `CancellationException` is right). But the failure it returns is the raw exception, and the ViewModel shows `e.message` ([FormViewModel.kt:87](../app/src/main/java/bg/butcher/pda/ui/screens/FormViewModel.kt#L87)).

- For a Frappe `frappe.throw(...)` (for example "BOM X is not active", "Items do not match BOM", "Batch B1 is for item A not B") the server replies with HTTP 417 and a body like `{"exc_type":"ValidationError","_server_messages":"[\"{\\\"message\\\": \\\"BOM X is not active\\\"}\"]", "exc": "<python traceback>"}`. Retrofit throws `HttpException` whose message is just `HTTP 417 ...`. The operator sees that, not the useful sentence. Nothing in the code reads `_server_messages` or `exc`.
- Network problems are `IOException` ("Unable to resolve host ...") and decoding problems are `SerializationException` (long technical text). Both are shown as raw text.
- A 401/403 (bad API key) is indistinguishable from other failures.

Suggestion: map errors into a small sealed type in `apiCall`, and let the UI choose a string resource for each.
```kotlin
sealed interface ApiError : Throwable-like {
    data class Server(val code: Int, val message: String?) : ApiError  // from _server_messages
    data object Unauthorized : ApiError                                // 401: bad or missing API key
    data class Forbidden(val message: String?) : ApiError              // 403: PermissionError (see below)
    data object Offline : ApiError                                     // IOException
    data class Unexpected(val cause: Throwable) : ApiError             // SerializationException etc.
}
```

*Updated: split 401 and 403. With the PDA User role (04, A7), a 403 `PermissionError` no longer means a bad key. It means one of two things:*
- *The role is missing on a new PDA user. Assigning it is a manual step.*
- *The `has_permission` hook refused an action the PDA isn't meant to do.*

*Show "This device isn't allowed to do that. Ask an administrator to check its PDA User role." for 403, and "Check the API key in Settings" for 401 (summary, 2.14 part 5).*
`_server_messages` is a JSON string containing a list of JSON strings, each with a `message` key (the text can contain HTML tags). Parse with `Json.parseToJsonElement` twice, strip tags, and fall back to the HTTP code. *Updated: the server's messages are translated to Bulgarian (04, A6), but only if the app sends `Accept-Language: bg` on every request (summary, 2.12 point 1). A 500 on submit should be shown as "couldn't confirm, retry", not as a plain failure (2.12 point 4).*

## 7. Strict `Json` fails on any extra key **[Risk]**

[AppContainer.kt:49](../app/src/main/java/bg/butcher/pda/data/AppContainer.kt#L49)

`Json.asConverterFactory(...)` uses the default `Json` instance, where unknown keys throw `SerializationException`. Confirmed lead. Cases where the server will add keys:

- `/api/method` responses can carry `_server_messages` next to `message` whenever the server code calls `frappe.msgprint`. In developer mode they also carry `_debug_messages`.
- The single-document GET (item 3) returns the full document.
- Any custom field added later to BOM/Batch/Item.

```kotlin
private val json = Json { ignoreUnknownKeys = true }
...
.addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
```
Also reuse that same `json` for encoding `items` (item 1) so there is one configuration.

## 8. Search results are cut off at 20 rows **[Risk]**

[NetworkErpRepository.kt:25-29](../app/src/main/java/bg/butcher/pda/data/NetworkErpRepository.kt#L25)

Frappe's list endpoint returns 20 rows by default (`limit_page_length`). A butcher with a few hundred BOMs searching for "sausage" would silently see only the first 20. Add a limit, for example `"limit_page_length" to "50"`, or `0` for no limit (fine for a few hundred small rows). Also check that `"item_name desc"` (Z to A) is intended; `asc` is the usual choice for a picker (see open questions). *Updated: the search should also filter on `docstatus = 1` (submitted BOMs only, 04, B1).*

## 9. API key is included in Android cloud backup **[Risk]**

[AndroidManifest.xml:8-10](../app/src/main/AndroidManifest.xml#L8), [backup_rules.xml](../app/src/main/res/xml/backup_rules.xml), [data_extraction_rules.xml](../app/src/main/res/xml/data_extraction_rules.xml)

Both XML files are the untouched Android Studio templates (everything commented out), so nothing is excluded. With `allowBackup="true"` the DataStore file `datastore/settings.preferences_pb` (server URL and API key, stored as plain text) is uploaded to the user's Google backup when the device has one, and restored onto other devices. Note the comment in `backup_rules.xml` ("ignored for devices older than API 31") is backwards: `fullBackupContent` is the rule file used on API 23-30, which is exactly your PDAs. If the PDAs have no Google account nothing is uploaded, but ADB backup is also affected on some versions.

Simplest fix, since a PDA fleet probably does not need backup at all:
```xml
android:allowBackup="false"
```
If you prefer to keep it, exclude the file in both XMLs:
```xml
<!-- backup_rules.xml -->
<full-backup-content>
    <exclude domain="file" path="datastore/"/>
</full-backup-content>

<!-- data_extraction_rules.xml -->
<data-extraction-rules>
    <cloud-backup><exclude domain="file" path="datastore/"/></cloud-backup>
    <device-transfer><exclude domain="file" path="datastore/"/></device-transfer>
</data-extraction-rules>
```
Plain-text storage itself is acceptable on non-rooted app-private storage, but if several people share a PDA and the key is powerful, consider encrypting it with a Keystore key (AES works from API 23).

*Updated: there is one API key per device (one Frappe user per PDA). That makes the backup exposure sharper in one way: restoring a backup onto a different PDA would copy another device's key, so exclude the file from backups or turn backup off (summary, section 3, Settings).*

## 10. Auth header: one string, no trimming, empty key still sent **[Risk]**

[ErpServiceInterceptor.kt:27](../app/src/main/java/bg/butcher/pda/network/ErpServiceInterceptor.kt#L27), [SettingsRepository.kt:17-20](../app/src/main/java/bg/butcher/pda/data/SettingsRepository.kt#L17)

- *There is one API key per device (confirmed).* Frappe API-key auth is `Authorization: token <api_key>:<api_secret>`. `PdaSettings` has only one `apiKey` field and the UI label is "API Key" ([SettingsScreen.kt:60](../app/src/main/java/bg/butcher/pda/ui/screens/SettingsScreen.kt#L60)). So either the operator must type `key:secret` into that one box (nothing tells them so), or auth will fail with 401/403. Either add an `apiSecret` field or label/validate the box as `key:secret`. *(Answered: one field, as now. Label it "API key and secret" with the hint `api_key:api_secret`, and check for exactly one `:` with text on both sides. The server side is one Frappe user per PDA, 04, A6.)*
- With an empty key the header is `token ` (just a space). The server rejects it, which is correct, but the user only sees "HTTP 401" (item 6). *(Item 6 is partly handled: the server now sends Bulgarian messages, but only if the app sends `Accept-Language: bg`, because Frappe picks the language before it checks the API key; summary, 2.12 point 1.)* Better to fail early with a clear message, like the base URL check.
- Nothing trims the value. A pasted key with a trailing newline makes OkHttp throw `IllegalArgumentException: Unexpected char 0x0a` inside the interceptor. OkHttp reports that as a generic "canceled due to ..." IO error. Trim in `SettingsRepository.update` (or the ViewModel).

```kotlin
val key = settings.apiKey.trim()
if (key.isEmpty()) throw IOException("API key is not set")
...header("Authorization", "token $key")
```

## 11. OkHttp may silently retry the stock-entry POST **[Risk]**

[AppContainer.kt:43-46](../app/src/main/java/bg/butcher/pda/data/AppContainer.kt#L43)

`retryOnConnectionFailure` is on by default. When a pooled Wi-Fi connection has gone stale (very common on PDAs that sleep and roam between access points), OkHttp retries the request on a new connection without telling you. For `create_manufacture_entry` this is the risky case: if the server processed the first request but the response was lost, a retry creates a second submitted Stock Entry and consumes ingredient stock twice. The server has no idempotency check (`api.py` always creates a new batch and entry). Also a user who gets a timeout will naturally tap "Save" again.

Options: disable retries on this client (`.retryOnConnectionFailure(false)`) and set explicit timeouts; add a client-generated request id that the server de-duplicates; or at least have the UI not allow a second submit until the first has a definite result. The ViewModel reviewer should check the double-tap case.

*Updated: the request id option was chosen and is done on the server (04, A4: `request_id`, unique custom field on Stock Entry). A retry with the same id returns the original entry, so OkHttp's automatic retry is now harmless for this endpoint and `retryOnConnectionFailure` can stay on. A failed submit keeps its id; a new form, BOM change or "Same BOM again" makes a new one (summary, 2.12 point 4).* The statement above that the server has no idempotency check is out of date.

## 12. Cleartext HTTP is allowed for the whole app **[Risk]**

[AndroidManifest.xml:15](../app/src/main/AndroidManifest.xml#L15)

*Updated: the developer confirmed the server is HTTPS.*

So `usesCleartextTraffic="true"` should go, but **removing it isn't enough on Android 6**. The "cleartext blocked by default" behaviour comes from `targetSdk ≥ 28` on Android 9+. On Android 6 the platform default is "allowed", whatever the targetSdk. Set it to `false` explicitly; OkHttp reads this flag and refuses `http://` URLs:

```xml
<!-- before -->
android:usesCleartextTraffic="true"
<!-- after -->
android:usesCleartextTraffic="false"
```

Also reject non-`https` base URLs in Settings, so the user gets a clear message instead of a failed request. Use one shared `normaliseBaseUrl()` helper in both `SettingsViewModel` and the interceptor (see the summary, 2.5). Keep the bundled Let's Encrypt roots, because Android 6 still needs them. The placeholder `http://localhost/` base URL is fine, since the interceptor rewrites it before any connection is made.

## 13. `DefaultAppContainer` still uses the fake repository **(known WIP)**

[AppContainer.kt:35-60](../app/src/main/java/bg/butcher/pda/data/AppContainer.kt#L35)

To finish it:
```kotlin
override val erpRepository: ErpRepository by lazy { NetworkErpRepository(service) }
```
For development a switch is handy: add `buildFeatures { buildConfig = true }` and choose `FakeErpRepository` when `BuildConfig.DEBUG && USE_FAKE`, or a "demo mode" setting.

Two things to fix while you are there:
- `certificates`, `okHttp`, `retrofit` are plain `private val`s, so every app start reads four PEM files and builds an SSL context on the main thread, even while the fake repository is in use. Make them `by lazy` like `service`.
- Nothing has ever run through the real network path, so expect the first real call to uncover items 1-4. Retrofit 3.0.0 is built against OkHttp 4.12 and you override it with OkHttp 5.5.0. That is meant to be binary-compatible, but test it once on a real Android 6 device.
- Consider setting explicit timeouts (`connectTimeout`, `readTimeout`, `callTimeout`). The defaults are 10 s each, which may be too short or too long for a flaky warehouse Wi-Fi; an explicit choice is better than an accidental one.

## 14. `Float` for quantities **[Risk]**

[ErpRepository.kt:18,25](../app/src/main/java/bg/butcher/pda/data/ErpRepository.kt#L18), [BomDetails.kt:14,29](../app/src/main/java/bg/butcher/pda/model/BomDetails.kt#L14)

A `Float` holds about 7 significant digits and is binary, so it cannot store 0.1 exactly. Real effects here:
- Anything computed on the device (summing batch quantities, scaling) can pick up noise such as `0.30000001`, and that text is what `Float.toString()` sends to the server. ERPNext rounds to the field precision, but the on-screen display and any "does the sum equal the required amount?" check can be off by a tiny amount. If the UI compares sums with `==`, it can reject a correct total.
- Small amounts are printed in scientific notation (`5.0E-4`); Python's `float()` accepts that, but it is ugly if ever shown.
- Large totals (above 16 million) lose whole units.

*Updated: decided. Use `Double`, with an `EPSILON` of 0.0005 in the range check (matches the server) and round to 3 decimals before sending (summary, 2.2 and 2.12 point 6).*

For recipe weights this is rarely catastrophic, but a cheap improvement is `Double` (JSON numbers decode directly), with formatting and comparison done after rounding to the ERP precision (usually 3 decimals). If you want exactness, keep amounts as `BigDecimal` in the UI and give the model a small serializer that reads/writes the JSON number as text. Float to Double is a mechanical change across the model, repository, fakes and ViewModel, so it is best done now before more code depends on it.

## 15. JSON in `searchBom` is built by string interpolation **[Improvement]**

[NetworkErpRepository.kt:26-29](../app/src/main/java/bg/butcher/pda/data/NetworkErpRepository.kt#L26)

A `"` or `\` in the query yields invalid JSON and the server answers 400 or 500. `%` and `_` typed by the user act as LIKE wildcards (`_` in a SKU would match any character; usually harmless, but unintended). After item 4, build the JSON with the serialization library:
```kotlin
// before
"filters" to "[[\"item_name\",\"LIKE\",\"%$query%\"],[\"is_active\",\"=\",\"1\"]]",

// after
private fun likeEscape(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

val filters = buildJsonArray {
    add(buildJsonArray { add("item_name"); add("like"); add("%${likeEscape(query)}%") })
    add(buildJsonArray { add("is_active"); add("="); add(1) })
}
val fields = buildJsonArray { listOf("name","item","item_name","uom","is_default").forEach(::add) }
// Updated: the field list also gets "item.shelf_life_in_days as shelf_life_in_days" and
// "uom.must_be_whole_number as must_be_whole_number" (summary, 2.10 and 2.13)
service.searchBom(mapOf("fields" to fields.toString(), "filters" to filters.toString(), ...))
```
The `\%` escape relies on MariaDB's default escape character; confirm on your server or skip the escaping if wildcards are acceptable. Also trim the query first.

## 16. Tests: none exist, and what to write first **[Improvement]**

[app/src/test](../app/src/test/java/bg/butcher/pda/fake), [ExampleInstrumentedTest.kt](../app/src/androidTest/java/bg/butcher/pda/ExampleInstrumentedTest.kt)

Today there are three fake helper classes and no `@Test` anywhere except the Android Studio template `useAppContext` (which tests nothing). The unit-test source set does not compile (item 5). `FakeNetworkErpRepository` is a misleading name: it fakes the *repository* and has nothing to do with the network repository. It is also redundant with `FakeErpRepository` in `main`.

Suggested order, most value first:
1. **`NetworkErpRepository` + `MockWebServer`** (add `testImplementation("com.squareup.okhttp3:mockwebserver:<same as okhttp>")`). Build a real Retrofit instance against the mock server and assert the exact request: path, query string, form body, header, and decoding of canned Frappe JSON, including a 417 error body. This would have caught items 1-4 and 6. Use the `FakeErpService` only for ViewModel-level tests.
2. **Model JSON decoding**: paste real responses from your server (BOM list, `get_bom_details`, `create_manufacture_entry`, Batch) into tests. Cheap and guards against server changes.
3. **`ErpServiceInterceptor`**: base URL with and without a path prefix, missing URL, empty key.
4. **`FormViewModel`** with `kotlinx-coroutines-test` and `FakeNetworkErpRepository`: search debounce and cancellation, quantity change, batch lookup success/failure, submit flow, double-submit.
5. **`SettingsRepository`** with a temporary DataStore file.

Also remove the duplicate androidTest dependencies (item 18) and the template instrumented test or replace it with a real UI test later.

## 17. Release build: no minify, no signing, empty keep rules **[Improvement]**

[app/build.gradle.kts:23-29](../app/build.gradle.kts#L23), [rules.keep](../app/src/main/keepRules/rules.keep)

- `optimization { enable = false }` is the new AGP 9 way of saying minify is off. This is fine while you iterate and it avoids R8 surprises. If you turn it on later (smaller APK, a bit faster start on slow PDAs), the libraries here ship their own consumer rules (Retrofit including suspend functions, OkHttp, kotlinx.serialization, DataStore), so the empty keep file is probably enough, but test a minified release build with every call, especially JSON decoding of all models. If something breaks, the usual first additions are `-keep` rules for the `@Serializable` model classes.
- There is no `signingConfigs`/release signing set up, so release builds are unsigned or debug-signed. For a sideloaded PDA fleet that must be updatable in place you need one consistent keystore (kept out of git). Check that `.gitignore` covers `*.jks`/`*.keystore` and `keystore.properties` (it currently does not mention them).
- `versionCode = 1` is hand-edited; fine for now.

## 18. Gradle catalog and dependency housekeeping **[Tidy-up]**

[libs.versions.toml](../gradle/libs.versions.toml), [app/build.gradle.kts](../app/build.gradle.kts)

Duplicates (the Studio "add dependency" dialog likely created these):
- Version keys that repeat: `coilCompose`/`coilComposeVersion`, `composeBom`/`composeBomVersion`, `coreKtx`/`coreKtxVersion`, `datastore`/`datastorePreferences`/`datastoreCore`, `junitVersion`/`androidxJunit`, `espressoCore`/`espressoCoreVersion`.
- Libraries: `androidx-core-ktx` **and** `androidx-core-ktx-v1120` are the same artifact and both are added ([build.gradle.kts:48,61](../app/build.gradle.kts#L48)). The androidTest section adds `androidx.espresso.core` and `espresso.core.v351`, `androidx.junit` and `junit.v115`, and a second compose BOM `...v20230800` (its name says 2023 but it resolves to 2026.09.00).
- DataStore: `datastore-preferences` already depends on `datastore`, so [line 87](../app/build.gradle.kts#L87) `libs.androidx.datastore` is redundant. `androidx-datastore-preferences` (no version) and `androidx-datastore-core` are unused aliases.
- Unused (no references in `src/main`): `coil-compose` (no image loading in the app), `androidx-preference-ktx` (declared, not used), `androidx-navigation-runtime-ktx` (comes with navigation-compose). `appcompat` also looks unused (no AppCompat classes in the code) but the `material` library is needed for the XML theme `Theme.MaterialComponents...`; consider switching the theme to a plain `android:Theme.Material.*` and dropping both.

Other build issues:
- [Line 4](../app/build.gradle.kts#L4): the serialization plugin has a hard-coded version `2.4.21` that duplicates `kotlin = "2.4.21"` in the catalog. Put it in the catalog (`kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }`) and declare it in the root `plugins` block with `apply false` like the others.
- [Lines 33-35](../app/build.gradle.kts#L33): `composeOptions { kotlinCompilerExtensionVersion = "1.5.3" }` is obsolete with the Compose compiler Gradle plugin on Kotlin 2.x and is ignored. Remove it so nobody trusts the wrong number.
- [Line 45](../app/build.gradle.kts#L45): `androidx-compose-ui-text` is pinned to 1.12.1 while everything else in Compose follows the BOM. A pinned version overrides the BOM and can drift out of sync. Drop the version (`module = "androidx.compose.ui:ui-text"` without `version.ref`).
- Retrofit 3 includes an official `com.squareup.retrofit2:converter-kotlinx-serialization`. The `com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter` you use is the old third-party one, now archived. Switching is a one-line change and avoids a dead dependency.
- `org.gradle.jvmargs=-Xmx2048m` is tight for a Compose + Kotlin 2.4 + AGP 9 build; 4 GB avoids random out-of-memory failures. Also `org.gradle.parallel` and build caching are off.
- I could not verify these version numbers against current releases (no network package index access in this review), so the comment at the top of the catalog about minSdk 24 artifacts is the thing to keep respecting: `./gradlew :app:processDebugManifest` will fail with a minSdk merge error if a dependency requires more than 23.

## 19. Interceptor uses `runBlocking` on every request **[Improvement]**

[ErpServiceInterceptor.kt:13](../app/src/main/java/bg/butcher/pda/network/ErpServiceInterceptor.kt#L13)

OkHttp interceptors are synchronous, and the call runs on OkHttp's own thread (not the main thread, because Retrofit's suspend functions enqueue the call), so `runBlocking` will not freeze the screen or deadlock; DataStore has its own scope. It still blocks a dispatcher thread for a disk read per request, and it is the one place where the call cannot be cancelled promptly. Keeping the latest settings in memory removes both:
```kotlin
class ErpServiceInterceptor(settings: SettingsRepository, scope: CoroutineScope) : Interceptor {
    private val current: StateFlow<PdaSettings?> = settings.settings.stateIn(scope, SharingStarted.Eagerly, null)
    override fun intercept(chain: Interceptor.Chain): Response {
        val s = current.value ?: runBlocking { settings.settings.first() } // first call only
        ...
```
Low priority; the present code is correct and reads the freshest value every time, which is nice after the user edits settings.

## 20. `PdaSettings.baseUrl` default is never used **[Tidy-up]**

[SettingsRepository.kt:18,32](../app/src/main/java/bg/butcher/pda/data/SettingsRepository.kt#L18)

Confirmed: the mapping always passes `.orEmpty()`, so the `"http://localhost/"` default in the data class only applies if someone writes `PdaSettings()` directly (the ViewModel may; the interceptor never sees it). A fresh install therefore has an empty URL and the interceptor correctly throws "Invalid or missing API base URL". Make this consistent: default to `""` in the data class, which is the real behaviour, or use `p[Keys.BASE_URL] ?: PdaSettings().baseUrl` if you want a default.

## 21. Typos, unused imports, stale comments **[Tidy-up]**

- `seachBom` should be `searchBom`: [ErpService.kt:19](../app/src/main/java/bg/butcher/pda/network/ErpService.kt#L19), [NetworkErpRepository.kt:24](../app/src/main/java/bg/butcher/pda/data/NetworkErpRepository.kt#L24), and the test fake [FakeErpService.kt:11](../app/src/test/java/bg/butcher/pda/fake/FakeErpService.kt#L11).
- Unused imports: `java.util.Date` in [ErpRepository.kt:9](../app/src/main/java/bg/butcher/pda/data/ErpRepository.kt#L9), [NetworkErpRepository.kt:12](../app/src/main/java/bg/butcher/pda/data/NetworkErpRepository.kt#L12), [ErpService.kt:15](../app/src/main/java/bg/butcher/pda/network/ErpService.kt#L15); `BomSearchResult` in `ErpRepository.kt:5`, `NetworkErpRepository.kt:5`, `ErpService.kt:6` is used only in the service; `coroutineScope` in `NetworkErpRepository.kt:10`; `BomSearchResult` in `FakeNetworkErpRepository.kt:6`. In `NetworkErpRepository`, `kotlinx.coroutines.CancellationException` is fine (an alias of the Java one).
- Stale comment in [BomSearchResult.kt:9](../app/src/main/java/bg/butcher/pda/model/BomSearchResult.kt#L9) mentions `MyAppApiService`; the class is `ErpService`.
- Empty `{ }` body on `BomDetails` ([BomDetails.kt:41](../app/src/main/java/bg/butcher/pda/model/BomDetails.kt#L41)).
- `isDefault: Int` ([BomSearchResult.kt:20](../app/src/main/java/bg/butcher/pda/model/BomSearchResult.kt#L20)): Frappe returns `0`/`1`, and kotlinx.serialization will not read `1` as a Boolean, so keeping `Int` is correct. Hide it from callers instead of comparing `== 1` in the UI ([StartScreen.kt:128](../app/src/main/java/bg/butcher/pda/ui/screens/StartScreen.kt#L128)):
```kotlin
@Serializable
data class BomSearchResultItem(..., @SerialName("is_default") private val isDefaultFlag: Int) {
    val isDefault: Boolean get() = isDefaultFlag == 1
}
```
Or write a tiny `KSerializer<Boolean>` that accepts 0/1 and use `@Serializable(with = ...)` on the field. *Updated: the summary (2.13) prefers the getter and says not to write a custom serializer. `BomSearchResultItem` also gets `shelfLife` and `wholeNumber` (as `Int`, 0/1) from the BOM search.*
- `ApiCall`-related: `apiCall` is a top-level public function in `NetworkErpRepository.kt`; make it `internal` or move it into its own file.
- Unused `val service` is only reachable once item 13 is done; `JobSlot`, `NetworkState` live in `network/` but are UI state helpers; moving them next to the ViewModels might read better (suggestion for the other reviewers).

## 22. `FakeErpRepository` quirks (dev only) **[Tidy-up]**

[FakeErpRepository.kt](../app/src/main/java/bg/butcher/pda/data/FakeErpRepository.kt)

- Line 28: `IntRange(0, 100)` has 101 values, so with `failureRate = 1f` one roll in 101 still succeeds. Use `Random.nextFloat() < failureRate`.
- Line 49: `Random.nextInt(2, 3)` is exclusive of 3, so it always returns 2: every fake word has exactly two syllables. Use `Random.nextInt(2, 4)` or `(2..3).random()`.
- Lines 55-57: ids are random from 1 to 998, and search returns 10-20 of them. Roughly a 10% chance of two equal `BOM.xxx` names in one result. [StartScreen.kt:92](../app/src/main/java/bg/butcher/pda/ui/screens/StartScreen.kt#L92) uses `key = { _, i -> i.name }`, and duplicate keys in a `LazyColumn` crash the app. So the fake can crash the list while the real server cannot (names are unique there). Generate unique ids (a counter or a set).
- Line 146: the production date uses `TimeZone.UTC`. In Bulgaria (UTC+2/+3) the fake date is yesterday between midnight and 2-3 a.m.; use `TimeZone.currentSystemDefault()`. The real server sends its own date (`today()`), so this only matters for the demo.
- `getBomDetails` ignores the `bom` argument and returns a new random name, and `createManufactureEntry` returns the stock entry name as `SKU.xxx`. Harmless, but it makes screens look inconsistent in demos.
- The fake is in `main`, so it ships in the release APK. Fine as a demo mode, otherwise move it to `src/debug` (or `src/test`).

## 23. Server-side oddity in `create_manufacture_entry` **[Improvement]**

[api.py line 211](https://github.com/MLuleci/ButcherERP/blob/master/my_app/my_app/api.py#L211)

The server returns `"item_name": fg_doc.name`. `fg_doc.name` is the item code (for example `SKU.001`), not the display name (`fg_doc.item_name`). The app's `ManufactureEntry.itemName` therefore holds the code. *(Fixed on the server: `entry_summary` returns `item_code` and the real `item_name`; 04, A3.)* If the success screen shows it as a product name, it will look wrong. Fix on the server (`fg_doc.item_name`) or rename the field. *Update: this now matters for the printed label, which shows the item name. The label also needs the quantity and unit, which the response doesn't include yet. See the summary, 2.11, section 4, for the suggested response.* Other server notes seen while reading: `production_date` is `today()` as `YYYY-MM-DD`, which `LocalDate` parses; and `expiry_date` is only the date the app sent.

---

## Open questions for the developer

1. ~~Is the production server on HTTPS?~~ *Answered: yes. See item 12.*
2. ~~API key format?~~ *Answered: one key per device, entered as `api_key:api_secret` in one field (item 10).*
3. ~~Does your Batch doctype have a custom `uom` field?~~ *Answered: yes, use it.* *Updated: superseded. The custom field was not added (04, B4 skipped); read the standard `stock_uom`, which ERPNext fills itself (summary, 2.12 point 2).* Still open: What does a real `GET /api/resource/Batch/<name>` response look like (item 3)? Would you rather have a whitelisted `get_batch` method in `my_app` like the other two?
4. Is `order_by item_name desc` intentional (Z to A), and should only submitted BOMs (`docstatus = 1`) be listed? *(Submitted BOMs only: decided, 04, B1.)* Roughly how many BOMs are there (item 8)?
5. Are the PDAs shared or Google-account-free? That decides how much the backup exposure (item 9) and key encryption matter.
6. ~~Should a double submit be impossible or just unlikely (item 11)? Can the server be changed to accept a client-generated request id?~~ *Answered: yes, done (04, A4).*
7. What precision does the ERP use for quantities (3 decimals?), and can a quantity ever be entered in grams while the stock unit is kilograms? This decides `Double` vs `BigDecimal` (item 14). *Partly answered: use `Double`, rounded to 3 decimals before sending. A BOM line's unit is always the item's stock unit (04, implementation notes).*
8. Is `FakeErpRepository` supposed to stay as a demo or offline mode, or be removed once the network version works (items 13 and 22)?
9. Do you plan to turn on R8 for release, and how will release builds be signed and distributed (item 17)?
10. ~~Is the `item_name` value from `create_manufacture_entry` meant to be the code (item 23)?~~ *Answered: no; the server now returns `item_code` and the real `item_name` (04, A3).*
11. I could not run Gradle or call a real server, so version compatibility (Retrofit 3 with OkHttp 5, AGP 9.2, Kotlin 2.4, minSdk 23 for each AndroidX artifact) is unverified beyond reading the files.
