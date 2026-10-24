package bg.butcher.pda.data

import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.model.ManufactureEntry
import bg.butcher.pda.network.ErpService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate
import java.util.Date

suspend fun <T> apiCall(block: suspend () -> T): Result<T> {
    val result = runCatching { block() }
    result.onFailure { if (it is CancellationException) throw it }
    return result
}

class NetworkErpRepository(
    private val service: ErpService
): ErpRepository {
    override suspend fun searchBom(query: String): Result<List<BomSearchResultItem>> = apiCall {
        val result = service.seachBom(
            mapOf(
                "fields" to "[\"name\",\"item\",\"item_name\",\"uom\",\"is_default\"]",
                "filters" to "[[\"item_name\",\"LIKE\",\"%$query%\"],[\"is_active\",\"=\",\"1\"]]",
                "order_by" to "item_name desc",
            )
        )
        result.data
    }

    override suspend fun getBatch(batchNumber: String): Result<BatchDetails> = apiCall {
        service.getBatch(
            batchNumber,
            mapOf(
                "fields" to "[\"name\",\"item\",\"item_name\",\"uom\"]"
            )
        )
    }

    override suspend fun getBomDetails(
        bom: String,
        quantity: Double,
        explode: Boolean
    ): Result<BomDetails> = apiCall {
        val result = service.getBomDetails(bom, quantity, explode)
        result.message
    }

    override suspend fun createManufactureEntry(
        bom: String,
        quantity: Double,
        items: Map<String, Map<String, Double>>,
        expiryDate: LocalDate,
        explode: Boolean,
        isFinished: Boolean
    ): Result<ManufactureEntry> = apiCall {
        service.createManufactureEntry(bom, quantity, items, expiryDate, explode, isFinished)
    }
}