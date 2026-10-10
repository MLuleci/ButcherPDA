package bg.butcher.pda.data

import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomSearchResult
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.model.ManufactureEntry
import kotlinx.datetime.LocalDate
import java.util.Date

interface ErpRepository {
    suspend fun searchBom(query: String): Result<List<BomSearchResultItem>>

    suspend fun getBatch(batchNumber: String): Result<BatchDetails>

    suspend fun getBomDetails(
        bom: String,
        quantity: Float = 1.0f,
        explode: Boolean = false,
    ): Result<BomDetails>

    suspend fun createManufactureEntry(
        bom: String,
        quantity: Float,
        items: Map<String, Map<String, Float>>,
        expiryDate: LocalDate?,
        explode: Boolean = false,
        isFinished: Boolean = true,
    ): Result<ManufactureEntry>
}