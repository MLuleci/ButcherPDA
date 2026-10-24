package bg.butcher.pda.fake

import bg.butcher.pda.model.BatchDetails
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomDetailsItem
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.model.ManufactureEntry
import kotlinx.datetime.LocalDate

object FakeDataSource {
    private const val fgItem = "item1"
    private const val fgItemName = "Fake Item 1"
    private const val bomName = "bom1"
    private const val materialItem = "item2"
    private const val materialItemName = "Fake Item 2"
    private const val fgBatchNo = "I.002"
    val productionDate = LocalDate(2026, 9, 20)
    val expiryDate = LocalDate(2026, 9, 25)
    val bomItem = BomDetailsItem(
        quantity = 1.0,
        itemCode = materialItem,
        itemName = materialItemName,
        uom = "Kgs",
        stockUom = "Kgs",
        conversionFactor = 1.0,
    )
    val bomDetails = BomDetails(
        name = bomName,
        quantity = 1.0,
        itemCode = fgItem,
        itemName = fgItemName,
        uom = "Nos",
        stockUom = "Nos",
        shelfLife = 5,
        items = listOf(bomItem),
        wholeNumber = true,
    )
    val bomSearchResultItem = BomSearchResultItem(
        name = bomName,
        item = fgItem,
        itemName = fgItemName,
        uom = "Nos",
        isDefault = 1,
        shelfLife = 5,
        wholeNumber = 1,
    )
    val bomSearchResult = listOf(bomSearchResultItem)
    val manufactureEntry = ManufactureEntry(
        stockEntryId = "stock1",
        batchNumber = fgBatchNo,
        itemCode = fgItem,
        itemName = fgItemName,
        quantity = 1.0,
        uom = "Nos",
        wholeNumber = true,
        productionDate = productionDate,
        expiryDate = expiryDate,
        isFinished = true,
    )
    val batchDetails = BatchDetails(
        name = "batch1",
        item = materialItem,
        itemName = materialItemName,
        stockUom = "Kgs",
    )
}
