package bg.butcher.pda.data

import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.model.ManufactureEntry
import kotlinx.datetime.LocalDate

interface ErpRepository {
    suspend fun searchBom(query: String): Result<List<BomSearchResultItem>>

    suspend fun getBatch(batchNumber: String): Result<BatchDetails>

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