package bg.butcher.pda.fake

import bg.butcher.pda.data.ErpRepository
import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomSearchResult
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.model.ManufactureEntry
import java.util.Date

class FakeNetworkErpRepository : ErpRepository {
    override suspend fun searchBom(query: String): Result<List<BomSearchResultItem>> {
        return Result.success(FakeDataSource.bomSearchResult.data)
    }

    override suspend fun getBatch(batchNumber: String): Result<BatchDetails> {
        return Result.success(FakeDataSource.batchDetails)
    }

    override suspend fun getBomDetails(
        bom: String,
        quantity: Float,
        explode: Boolean
    ): Result<BomDetails> {
        return Result.success(FakeDataSource.bomDetails)
    }

    override suspend fun createManufactureEntry(
        bom: String,
        quantity: Float,
        items: Map<String, Map<String, Float>>,
        expiryDate: Date?,
        explode: Boolean,
        isFinished: Boolean
    ): Result<ManufactureEntry> {
        return Result.success(FakeDataSource.manufactureEntry)
    }
}