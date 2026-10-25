package bg.butcher.pda.hardware

import bg.butcher.pda.model.BatchDetails
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class LabelDataTest {
    @Test
    fun batchUrl_isTheDeskPageAndScansBackToTheBatch() {
        val url = batchUrl("https://erp.example.bg/".toHttpUrl(), "PORK_SAUSAGE0012")

        assertEquals("https://erp.example.bg/app/batch/PORK_SAUSAGE0012", url)
        assertEquals(ScannedCode.Batch("PORK_SAUSAGE0012"), parseScan(url))
    }

    @Test
    fun batchUrl_ignoresAnyPathAndEncodesTheName() {
        val url = batchUrl("https://erp.example.bg/some/path".toHttpUrl(), "BATCH/001 A")

        assertEquals("https://erp.example.bg/app/batch/BATCH%2F001%20A", url)
        assertEquals(ScannedCode.Batch("BATCH/001 A"), parseScan(url))
    }

    @Test
    fun batchToLabel_usesTheCurrentAmountInTheStockUnit() {
        val label = BatchDetails(
            name = "B1", item = "FG", itemName = "Product", stockUom = "Nos", batchQuantity = 25.0,
            referenceName = "MAT-STE-1",
        ).toLabel()

        assertEquals("25 Nos", label.quantity)
        assertEquals("MAT-STE-1", label.stockEntryId)
    }
}
