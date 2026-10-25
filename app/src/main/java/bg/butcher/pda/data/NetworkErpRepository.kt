package bg.butcher.pda.data

import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BatchFilters
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.model.ManufactureEntry
import bg.butcher.pda.model.ManufactureEntryRequest
import bg.butcher.pda.model.StockEntryDetails
import bg.butcher.pda.network.ErpError
import bg.butcher.pda.network.ErpService
import bg.butcher.pda.util.roundForErp
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlin.time.Clock

/** Runs [block], turning any failure except cancellation into an [ErpError]. */
internal suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(ErpError.from(e))
}

// Frappe returns 20 rows unless told otherwise
const val BOM_SEARCH_LIMIT = 50

private fun likeEscape(text: String) =
    text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

private fun JsonArrayBuilder.filter(field: String, operator: String, value: String) =
    addJsonArray { add(field); add(operator); add(value) }

private fun JsonArrayBuilder.filter(field: String, operator: String, value: Int) =
    addJsonArray { add(field); add(operator); add(value) }

private val BATCH_FIELDS = buildJsonArray {
    add("name")
    add("item")
    add("item.item_name as item_name")
    add("item.default_bom as default_bom")
    add("batch_qty")
    add("stock_uom")
    add("manufacturing_date")
    add("expiry_date")
    add("reference_name")
}.toString()

class NetworkErpRepository(
    private val service: ErpService,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
): ErpRepository {
    override suspend fun searchBom(query: String): Result<List<BomSearchResultItem>> = apiCall {
        val fields = buildJsonArray {
            add("name")
            add("item")
            add("item_name")
            add("uom")
            add("is_default")
            add("item.shelf_life_in_days as shelf_life_in_days")
            add("uom.must_be_whole_number as must_be_whole_number")
        }
        val filters = buildJsonArray {
            filter("item_name", "like", "%${likeEscape(query.trim())}%")
            filter("is_active", "=", 1)
            filter("docstatus", "=", 1)
        }
        service.searchBom(
            mapOf(
                "fields" to fields.toString(),
                "filters" to filters.toString(),
                // A to Z by product name, then by BOM, so a product's BOMs keep the same order
                "order_by" to "item_name asc, name asc",
                "limit_page_length" to BOM_SEARCH_LIMIT.toString(),
            )
        ).data
    }

    override suspend fun getBatch(batchNumber: String): Result<BatchDetails> = apiCall {
        val options = mapOf(
            "fields" to BATCH_FIELDS,
            "filters" to buildJsonArray { filter("name", "=", batchNumber.trim()) }.toString(),
        )
        service.listBatches(options).data.firstOrNull()
            ?: throw ErpError.Http(404, "Batch $batchNumber not found")
    }

    override suspend fun searchBatches(query: String, filters: BatchFilters, start: Int): Result<List<BatchDetails>> =
        apiCall {
            val date = today()
            val conditions = buildJsonArray {
                filter("item.default_bom", "is", "set")
                filters.producedWithinDays?.let { days ->
                    filter("manufacturing_date", ">=", date.minus(days, DateTimeUnit.DAY).toString())
                }
                if (filters.hideExpired) filter("expiry_date", ">=", date.toString())
            }
            val options = buildMap {
                put("fields", BATCH_FIELDS)
                put("filters", conditions.toString())
                query.trim().takeIf { it.isNotEmpty() }?.let { text ->
                    val pattern = "%${likeEscape(text)}%"
                    put("or_filters", buildJsonArray {
                        filter("name", "like", pattern)
                        filter("item.item_name", "like", pattern)
                    }.toString())
                }
                // From the enum only, since Frappe sends it to the database as SQL; ties by name keep pages stable
                put("order_by", "${filters.sort.field} ${if (filters.descending) "desc" else "asc"}, name desc")
                put("limit_start", start.toString())
                put("limit_page_length", BATCH_PAGE_SIZE.toString())
            }
            service.listBatches(options).data
        }

    override suspend fun getStockEntry(name: String): Result<StockEntryDetails> = apiCall {
        service.getStockEntry(name).data
    }

    override suspend fun getBomDetails(
        bom: String,
        quantity: Double,
        explode: Boolean
    ): Result<BomDetails> = apiCall {
        service.getBomDetails(bom, quantity.roundForErp(), explode).message
    }

    override suspend fun createManufactureEntry(
        bom: String,
        quantity: Double,
        items: Map<String, Map<String, Double>>,
        expiryDate: LocalDate,
        explode: Boolean,
        isFinished: Boolean,
        requestId: String,
    ): Result<ManufactureEntry> = apiCall {
        val request = ManufactureEntryRequest(
            bom = bom,
            quantity = quantity.roundForErp(),
            items = items.mapValues { (_, batches) -> batches.mapValues { (_, qty) -> qty.roundForErp() } },
            expiryDate = expiryDate,
            explode = explode,
            isFinished = isFinished,
            requestId = requestId,
        )
        service.createManufactureEntry(request).message
    }
}
