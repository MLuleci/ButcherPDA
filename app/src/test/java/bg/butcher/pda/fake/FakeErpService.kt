package bg.butcher.pda.fake

import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.model.DataWrapper
import bg.butcher.pda.model.ManufactureEntry
import bg.butcher.pda.model.ManufactureEntryRequest
import bg.butcher.pda.model.MessageWrapper
import bg.butcher.pda.network.ErpService

class FakeErpService: ErpService {
    override suspend fun searchBom(options: Map<String, String>): DataWrapper<List<BomSearchResultItem>> {
        return DataWrapper(FakeDataSource.bomSearchResult)
    }

    override suspend fun getBatch(batchNumber: String): DataWrapper<BatchDetails> {
        return DataWrapper(FakeDataSource.batchDetails)
    }

    override suspend fun getBomDetails(
        bom: String,
        quantity: Double,
        explode: Boolean
    ): MessageWrapper<BomDetails> {
        return MessageWrapper(FakeDataSource.bomDetails)
    }

    override suspend fun createManufactureEntry(request: ManufactureEntryRequest): MessageWrapper<ManufactureEntry> {
        return MessageWrapper(FakeDataSource.manufactureEntry)
    }
}
