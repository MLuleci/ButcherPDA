package bg.butcher.pda.fake

import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomSearchResult
import bg.butcher.pda.model.ManufactureEntry
import bg.butcher.pda.network.ErpService
import java.util.Date

class FakeErpService: ErpService {
    override suspend fun seachBom(options: Map<String, String>): BomSearchResult {
        return FakeDataSource.bomSearchResult
    }

    override suspend fun getBatch(
        batchNumber: String,
        options: Map<String, String>
    ): BatchDetails {
        return FakeDataSource.batchDetails
    }

    override suspend fun getBomDetails(
        bom: String,
        quantity: Float,
        explode: Boolean
    ): BomDetails {
        return FakeDataSource.bomDetails
    }

    override suspend fun createManufactureEntry(
        bom: String,
        quantity: Float,
        items: Map<String, Map<String, Float>>,
        expiryDate: Date?,
        explode: Boolean,
        isFinished: Boolean
    ): ManufactureEntry {
        return FakeDataSource.manufactureEntry
    }
}