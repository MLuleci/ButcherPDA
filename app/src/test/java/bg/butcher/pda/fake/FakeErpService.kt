package bg.butcher.pda.fake

import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.model.DataWrapper
import bg.butcher.pda.model.ManufactureEntry
import bg.butcher.pda.model.MessageWrapper
import bg.butcher.pda.network.ErpService
import kotlinx.datetime.LocalDate

class FakeErpService: ErpService {
    override suspend fun seachBom(options: Map<String, String>): DataWrapper<List<BomSearchResultItem>> {
        return DataWrapper(FakeDataSource.bomSearchResult)
    }

    override suspend fun getBatch(
        batchNumber: String,
        options: Map<String, String>
    ): BatchDetails {
        return FakeDataSource.batchDetails
    }

    override suspend fun getBomDetails(
        bom: String,
        quantity: Double,
        explode: Boolean
    ): MessageWrapper<BomDetails> {
        return MessageWrapper(FakeDataSource.bomDetails)
    }

    override suspend fun createManufactureEntry(
        bom: String,
        quantity: Double,
        items: Map<String, Map<String, Double>>,
        expiryDate: LocalDate,
        explode: Boolean,
        isFinished: Boolean
    ): ManufactureEntry {
        return FakeDataSource.manufactureEntry
    }
}