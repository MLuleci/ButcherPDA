package bg.butcher.pda.data

import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BatchFilters
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.model.ManufactureEntry
import bg.butcher.pda.model.StockEntryDetails
import kotlinx.datetime.LocalDate

// Frappe returns 20 rows unless told otherwise
const val BATCH_PAGE_SIZE = 30

interface ErpRepository {
    suspend fun searchBom(query: String): Result<List<BomSearchResultItem>>

    /** One batch with its link fields (product name, default BOM); 404 if missing. Ignores case. */
    suspend fun getBatch(batchNumber: String): Result<BatchDetails>

    /**
     * Batches of items made here (with a default BOM), whose number or product name contains [query].
     * A blank query lists every such batch. Pages of [BATCH_PAGE_SIZE] rows, from row [start].
     */
    suspend fun searchBatches(query: String, filters: BatchFilters, start: Int = 0): Result<List<BatchDetails>>

    suspend fun getStockEntry(name: String): Result<StockEntryDetails>

    suspend fun getBomDetails(
        bom: String,
        quantity: Double = 1.0,
        explode: Boolean = false,
    ): Result<BomDetails>

    suspend fun createManufactureEntry(
        bom: String,
        quantity: Double,
        items: Map<String, Map<String, Double>>,
        expiryDate: LocalDate,
        explode: Boolean = false,
        isFinished: Boolean = true,
        requestId: String,
    ): Result<ManufactureEntry>
}