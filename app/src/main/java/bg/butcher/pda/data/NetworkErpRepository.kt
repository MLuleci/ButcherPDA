package bg.butcher.pda.data

import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.model.ManufactureEntry
import bg.butcher.pda.model.ManufactureEntryRequest
import bg.butcher.pda.network.ErpError
import bg.butcher.pda.network.ErpService
import bg.butcher.pda.util.roundForErp
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray

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

class NetworkErpRepository(
    private val service: ErpService
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
                "order_by" to "item_name desc",
                "limit_page_length" to BOM_SEARCH_LIMIT.toString(),
            )
        ).data
    }

    override suspend fun getBatch(batchNumber: String): Result<BatchDetails> = apiCall {
        service.getBatch(batchNumber).data
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
